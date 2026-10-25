package net.yizuz.playerogrammod.ai.archive;

import net.yizuz.playerogrammod.ai.AiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TarGzExtractorTest {
    @Test
    void extractsRegularFiles(@TempDir Path dir) throws IOException {
        byte[] archive = tarGz(
                file("bin/llama-server", "hello", 0755),
                file("docs/readme.txt", "docs", 0644));
        Path archivePath = dir.resolve("runtime.tar.gz");
        Files.write(archivePath, archive);

        int files = TarGzExtractor.extract(archivePath, dir.resolve("out"));

        assertEquals(2, files);
        assertEquals("hello", Files.readString(dir.resolve("out/bin/llama-server")));
        assertEquals("docs", Files.readString(dir.resolve("out/docs/readme.txt")));
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            assertTrue(Files.isExecutable(dir.resolve("out/bin/llama-server")));
        }
    }

    @Test
    void extractsSafeRelativeSymlinks(@TempDir Path dir) throws IOException {
        byte[] archive = tarGz(
                file("bin/app", "hello", 0755),
                symlink("bin/libapp.so", "app", 0777));
        Path archivePath = dir.resolve("runtime.tar.gz");
        Files.write(archivePath, archive);

        TarGzExtractor.extract(archivePath, dir.resolve("out"));

        Path link = dir.resolve("out/bin/libapp.so");
        assertTrue(Files.isSymbolicLink(link));
        assertEquals(Path.of("app"), Files.readSymbolicLink(link));
        assertEquals("hello", Files.readString(link));
    }

    @Test
    void rejectsPathTraversal(@TempDir Path dir) throws IOException {
        byte[] archive = tarGz(file("../escape.txt", "bad", 0644));
        Path archivePath = dir.resolve("bad.tar.gz");
        Files.write(archivePath, archive);

        assertThrows(AiException.class, () -> TarGzExtractor.extract(archivePath, dir.resolve("out")));
        assertFalse(Files.exists(dir.resolve("escape.txt")));
        assertFalse(Files.exists(dir.resolve("out/../escape.txt")));
    }

    @Test
    void rejectsEscapingSymlinks(@TempDir Path dir) throws IOException {
        byte[] archive = tarGz(symlink("bin/evil", "../../etc/passwd", 0777));
        Path archivePath = dir.resolve("bad-link.tar.gz");
        Files.write(archivePath, archive);

        assertThrows(AiException.class, () -> TarGzExtractor.extract(archivePath, dir.resolve("out")));
        assertFalse(Files.isSymbolicLink(dir.resolve("out/bin/evil")));
    }

    @Test
    void rejectsAbsoluteSymlinks(@TempDir Path dir) throws IOException {
        byte[] archive = tarGz(symlink("bin/evil", "/etc/passwd", 0777));
        Path archivePath = dir.resolve("absolute-link.tar.gz");
        Files.write(archivePath, archive);

        assertThrows(AiException.class, () -> TarGzExtractor.extract(archivePath, dir.resolve("out")));
    }

    @Test
    void rejectsHardlinks(@TempDir Path dir) throws IOException {
        byte[] archive = tarGz(hardlink("bin/hard", "bin/app", 0777));
        Path archivePath = dir.resolve("hardlink.tar.gz");
        Files.write(archivePath, archive);

        assertThrows(AiException.class, () -> TarGzExtractor.extract(archivePath, dir.resolve("out")));
    }

    private static Entry file(String name, String content, int mode) {
        return new Entry(name, content.getBytes(StandardCharsets.UTF_8), mode, '0', null);
    }

    private static Entry symlink(String name, String target, int mode) {
        return new Entry(name, new byte[0], mode, '2', target);
    }

    private static Entry hardlink(String name, String target, int mode) {
        return new Entry(name, new byte[0], mode, '1', target);
    }

    private static byte[] tarGz(Entry... entries) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            for (Entry entry : entries) {
                gzip.write(header(entry.name(), entry.data().length, entry.mode(), entry.type(), entry.linkName()));
                gzip.write(entry.data());
                gzip.write(new byte[padding(entry.data().length)]);
            }
            gzip.write(new byte[1024]);
        }
        return buffer.toByteArray();
    }

    private static byte[] header(String name, long size, int mode, char type, String linkName) {
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));
        if (linkName != null) {
            byte[] linkBytes = linkName.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(linkBytes, 0, header, 157, Math.min(linkBytes.length, 100));
        }
        putOctal(header, 100, 8, mode);
        putOctal(header, 108, 8, 0);
        putOctal(header, 116, 8, 0);
        putOctal(header, 124, 12, size);
        putOctal(header, 136, 12, 0);
        Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = (byte) type;
        byte[] magic = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(magic, 0, header, 257, magic.length);
        header[263] = '0';
        header[264] = '0';

        long checksum = 0;
        for (byte value : header) {
            checksum += (value & 0xFF);
        }
        putOctal(header, 148, 8, checksum);
        return header;
    }

    private static void putOctal(byte[] header, int offset, int length, long value) {
        String text = Long.toOctalString(value);
        while (text.length() < length - 1) {
            text = "0" + text;
        }
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length - 1));
        header[offset + length - 1] = 0;
    }

    private static int padding(long size) {
        return (int) ((512 - (size % 512)) % 512);
    }

    private record Entry(String name, byte[] data, int mode, char type, String linkName) {
    }
}
