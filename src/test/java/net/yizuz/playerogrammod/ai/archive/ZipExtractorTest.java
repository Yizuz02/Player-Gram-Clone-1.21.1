package net.yizuz.playerogrammod.ai.archive;

import net.yizuz.playerogrammod.ai.AiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipExtractorTest {
    @Test
    void extractsRegularFiles(@TempDir Path dir) throws IOException {
        Path archive = dir.resolve("runtime.zip");
        Files.write(archive, zip("llama-server.exe", "hello", "docs/readme.txt", "docs"));

        int files = ZipExtractor.extract(archive, dir.resolve("out"));

        assertEquals(2, files);
        assertTrue(Files.isRegularFile(dir.resolve("out/llama-server.exe")));
        assertEquals("hello", Files.readString(dir.resolve("out/llama-server.exe")));
        assertEquals("docs", Files.readString(dir.resolve("out/docs/readme.txt")));
    }

    @Test
    void rejectsPathTraversal(@TempDir Path dir) throws IOException {
        Path archive = dir.resolve("bad.zip");
        Files.write(archive, zip("../escape.txt", "bad"));

        assertThrows(AiException.class, () -> ZipExtractor.extract(archive, dir.resolve("out")));
        assertFalse(Files.exists(dir.resolve("escape.txt")));
    }

    private static byte[] zip(String firstName, String firstContent, String... extra) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(firstName));
            zip.write(firstContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (int i = 0; i + 1 < extra.length; i += 2) {
                zip.putNextEntry(new ZipEntry(extra[i]));
                zip.write(extra[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return buffer.toByteArray();
    }
}
