package net.yizuz.playerogrammod.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryResolverTest {
    @Test
    void parsesTheRequiredGlibcxxVersion() {
        assertEquals("3.4.30", LibraryResolver.requiredVersion(
                "llama-server: /lib64/libstdc++.so.6: version `GLIBCXX_3.4.30' not found"));
    }

    @Test
    void comparesLibraryVersions(@TempDir Path dir) throws IOException {
        Path library = Files.writeString(dir.resolve("libstdc++.so.6"), "prefix GLIBCXX_3.4.31 suffix");

        assertTrue(LibraryResolver.supports(library, "3.4.30"));
        assertFalse(LibraryResolver.supports(library, "3.4.32"));
        assertFalse(LibraryResolver.supports(library, "not-a-version"));
    }
}
