package net.yizuz.playerogrammod.ai.archive;

import net.yizuz.playerogrammod.ai.AiException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class TarGzExtractor {
    private static final int BLOCK = 512;
    private static final int MAX_METADATA = 1 << 20;

    public static int extract(Path archive, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Files.createDirectories(root);

        int files = 0;
        int zeroBlocks = 0;
        String paxPath = null;
        Long paxSize = null;
        String paxLinkPath = null;
        String longName = null;
        String longLink = null;

        try (InputStream raw = Files.newInputStream(archive);
             InputStream in = new GZIPInputStream(new BufferedInputStream(raw, 1 << 16), 1 << 16)) {
            byte[] header = new byte[BLOCK];
            while (true) {
                if (!readFully(in, header, 0, BLOCK)) {
                    break;
                }
                if (isZero(header)) {
                    zeroBlocks++;
                    if (zeroBlocks >= 2) {
                        break;
                    }
                    continue;
                }
                zeroBlocks = 0;
                verifyChecksum(header);

                char type = (char) (header[156] & 0xFF);
                long headerSize = parseNumber(header, 124, 12);
                int mode = (int) parseNumber(header, 100, 8);

                if (type == 'x' || type == 'X') {
                    Map<String, String> values = parsePax(readMetadata(in, headerSize));
                    if (values.containsKey("path")) {
                        paxPath = values.get("path");
                    }
                    if (values.containsKey("size")) {
                        paxSize = parseLong(values.get("size"));
                    }
                    if (values.containsKey("linkpath")) {
                        paxLinkPath = values.get("linkpath");
                    }
                    continue;
                }
                if (type == 'g') {
                    readMetadata(in, headerSize);
                    continue;
                }
                if (type == 'L') {
                    longName = stripNul(new String(readMetadata(in, headerSize), StandardCharsets.UTF_8));
                    continue;
                }
                if (type == 'K') {
                    longLink = stripNul(new String(readMetadata(in, headerSize), StandardCharsets.UTF_8));
                    continue;
                }
                if (type == 'V') {
                    readMetadata(in, headerSize);
                    continue;
                }

                String name = readString(header, 0, 100);
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty() && isPosixUstar(header)) {
                    name = prefix + "/" + name;
                }
                if (longName != null) {
                    name = longName;
                }
                if (paxPath != null) {
                    name = paxPath;
                }
                long entrySize = paxSize != null ? paxSize : headerSize;
                String headerLink = readString(header, 157, 100);
                String linkName = paxLinkPath != null ? paxLinkPath : (longLink != null ? longLink : headerLink);

                if (type == '5') {
                    Path target = safePath(root, name);
                    Files.createDirectories(target);
                    applyMode(target, mode, true);
                    skipFully(in, entrySize + paddingFor(entrySize));
                } else if (type == '0' || type == '\0' || type == '7') {
                    Path target = safePath(root, name);
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        copyExactly(in, out, entrySize);
                    }
                    skipPadding(in, entrySize);
                    applyMode(target, mode, false);
                    files++;
                } else if (type == '2') {
                    Path target = safePath(root, name);
                    if (target.equals(root)) {
                        throw new AiException("Tar archive contains an invalid symlink path: " + name);
                    }
                    createSymlink(root, target, linkName);
                    skipFully(in, entrySize + paddingFor(entrySize));
                } else if (type == '1' || type == '3' || type == '4' || type == '6') {
                    throw new AiException("Unsupported link or device entry in tar archive: " + name);
                } else {
                    throw new AiException("Unsupported tar entry type '" + type + "' in archive.");
                }

                paxPath = null;
                paxSize = null;
                paxLinkPath = null;
                longName = null;
                longLink = null;
            }
        }
        return files;
    }

    private static Path safePath(Path root, String name) {
        if (name == null || name.isBlank()) {
            throw new AiException("Tar archive contains an entry with an empty path.");
        }
        String normalizedName = name.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new AiException("Tar archive contains an absolute path: " + name);
        }
        Path target = root.resolve(normalizedName).normalize();
        if (!target.startsWith(root)) {
            throw new AiException("Tar archive contains a path outside the destination: " + name);
        }
        return target;
    }

    private static void createSymlink(Path root, Path target, String linkName) throws IOException {
        if (linkName == null || linkName.isBlank()) {
            throw new AiException("Tar archive contains a symlink without a target: " + target);
        }
        String link = linkName.replace('\\', '/');
        if (link.startsWith("/") || link.matches("^[A-Za-z]:.*")) {
            throw new AiException("Tar archive contains an absolute symlink target: " + link);
        }
        Path resolved = target.getParent().resolve(link).normalize();
        if (!resolved.startsWith(root)) {
            throw new AiException("Tar archive symlink escapes the destination: " + target + " -> " + link);
        }
        Files.createDirectories(target.getParent());
        try {
            Files.deleteIfExists(target);
            Files.createSymbolicLink(target, Path.of(link));
        } catch (UnsupportedOperationException e) {
            throw new AiException("This filesystem cannot create the symlink required by the runtime archive: "
                    + target, e);
        } catch (FileSystemException e) {
            throw new AiException("Could not create symlink " + target + " -> " + link + ": " + e.getMessage(), e);
        }
    }

    private static void verifyChecksum(byte[] header) {
        long expected = parseNumber(header, 148, 8);
        long actual = 0;
        for (int i = 0; i < BLOCK; i++) {
            actual += (i >= 148 && i < 156) ? 0x20 : (header[i] & 0xFF);
        }
        if (actual != expected) {
            throw new AiException("Corrupt tar archive: header checksum mismatch.");
        }
    }

    private static long parseNumber(byte[] data, int offset, int length) {
        if ((data[offset] & 0x80) != 0) {
            long value = data[offset] & 0x3F;
            for (int i = 1; i < length; i++) {
                value = (value << 8) | (data[offset + i] & 0xFF);
            }
            return value;
        }
        String text = readString(data, offset, length).trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(text, 8);
        } catch (NumberFormatException e) {
            throw new AiException("Corrupt tar archive: invalid number '" + text + "'.", e);
        }
    }

    private static Map<String, String> parsePax(byte[] data) {
        Map<String, String> values = new HashMap<>();
        int index = 0;
        while (index < data.length) {
            int space = indexOf(data, (byte) ' ', index);
            if (space < 0) {
                break;
            }
            String lengthText = new String(data, index, space - index, StandardCharsets.UTF_8).trim();
            int recordLength;
            try {
                recordLength = Integer.parseInt(lengthText);
            } catch (NumberFormatException e) {
                break;
            }
            if (recordLength <= 0 || index + recordLength > data.length) {
                break;
            }
            int equals = indexOf(data, (byte) '=', space + 1);
            if (equals < 0 || equals >= index + recordLength) {
                break;
            }
            int valueEnd = index + recordLength;
            if (data[valueEnd - 1] == '\n') {
                valueEnd--;
            }
            String key = new String(data, space + 1, equals - (space + 1), StandardCharsets.UTF_8);
            String value = new String(data, equals + 1, valueEnd - (equals + 1), StandardCharsets.UTF_8);
            values.put(key, value);
            index += recordLength;
        }
        return values;
    }

    private static int indexOf(byte[] data, byte value, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new AiException("Corrupt tar archive: invalid PAX size '" + value + "'.", e);
        }
    }

    private static byte[] readMetadata(InputStream in, long size) throws IOException {
        if (size < 0 || size > MAX_METADATA) {
            throw new AiException("Tar archive metadata entry is too large: " + size);
        }
        byte[] data = new byte[(int) size];
        if (!readFully(in, data, 0, data.length)) {
            throw new AiException("Truncated tar archive metadata.");
        }
        skipPadding(in, size);
        return data;
    }

    private static void copyExactly(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[1 << 16];
        long remaining = size;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new AiException("Truncated tar archive: expected " + remaining + " more bytes.");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        skipFully(in, paddingFor(size));
    }

    private static long paddingFor(long size) {
        return (BLOCK - (size % BLOCK)) % BLOCK;
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (in.read() < 0) {
                throw new AiException("Truncated tar archive.");
            }
            remaining--;
        }
    }

    private static boolean readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, offset + total, length - total);
            if (read < 0) {
                if (total == 0) {
                    return false;
                }
                throw new AiException("Truncated tar archive header.");
            }
            total += read;
        }
        return true;
    }

    private static boolean isZero(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPosixUstar(byte[] header) {
        return header[257] == 'u' && header[258] == 's' && header[259] == 't'
                && header[260] == 'a' && header[261] == 'r' && header[262] == 0;
    }

    private static String readString(byte[] data, int offset, int length) {
        int limit = offset + length;
        int end = offset;
        while (end < limit && data[end] != 0) {
            end++;
        }
        String value = new String(data, offset, end - offset, StandardCharsets.UTF_8);
        int trimmed = value.length();
        while (trimmed > 0 && (value.charAt(trimmed - 1) == ' ' || value.charAt(trimmed - 1) == '\0')) {
            trimmed--;
        }
        return value.substring(0, trimmed);
    }

    private static String stripNul(String value) {
        int nul = value.indexOf('\0');
        return nul >= 0 ? value.substring(0, nul) : value;
    }

    private static void applyMode(Path path, int mode, boolean directory) {
        int bits = mode & 0777;
        if (directory) {
            bits |= 0700;
        } else {
            bits |= 0600;
        }
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        add(permissions, bits, 0400, PosixFilePermission.OWNER_READ);
        add(permissions, bits, 0200, PosixFilePermission.OWNER_WRITE);
        add(permissions, bits, 0100, PosixFilePermission.OWNER_EXECUTE);
        add(permissions, bits, 0040, PosixFilePermission.GROUP_READ);
        add(permissions, bits, 0020, PosixFilePermission.GROUP_WRITE);
        add(permissions, bits, 0010, PosixFilePermission.GROUP_EXECUTE);
        add(permissions, bits, 0004, PosixFilePermission.OTHERS_READ);
        add(permissions, bits, 0002, PosixFilePermission.OTHERS_WRITE);
        add(permissions, bits, 0001, PosixFilePermission.OTHERS_EXECUTE);
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX filesystem; the installer sets the executable bit separately.
        }
    }

    private static void add(Set<PosixFilePermission> permissions, int bits, int mask, PosixFilePermission permission) {
        if ((bits & mask) != 0) {
            permissions.add(permission);
        }
    }

    private TarGzExtractor() {
    }
}
