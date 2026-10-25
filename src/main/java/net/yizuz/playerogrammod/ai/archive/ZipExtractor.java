package net.yizuz.playerogrammod.ai.archive;

import net.yizuz.playerogrammod.ai.AiException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipExtractor {
    public static int extract(Path archive, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Files.createDirectories(root);
        int files = 0;
        byte[] buffer = new byte[1 << 16];

        try (InputStream raw = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 1 << 16))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = safePath(root, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            out.write(buffer, 0, read);
                        }
                    }
                    files++;
                }
                zip.closeEntry();
            }
        }
        return files;
    }

    private static Path safePath(Path root, String name) {
        if (name == null || name.isBlank()) {
            throw new AiException("Zip archive contains an entry with an empty path.");
        }
        String normalizedName = name.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new AiException("Zip archive contains an absolute path: " + name);
        }
        Path target = root.resolve(normalizedName).normalize();
        if (!target.startsWith(root)) {
            throw new AiException("Zip archive contains a path outside the destination: " + name);
        }
        return target;
    }

    private ZipExtractor() {
    }
}
