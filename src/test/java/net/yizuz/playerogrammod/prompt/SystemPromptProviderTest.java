package net.yizuz.playerogrammod.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemPromptProviderTest {
    @Test
    void createsTheDefaultPromptWhenMissing(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("playerogram/system_prompt.md");
        SystemPromptProvider provider = new SystemPromptProvider(file);

        provider.reload();

        assertTrue(Files.isRegularFile(file));
        assertEquals(SystemPromptProvider.DEFAULT_PROMPT.strip(), Files.readString(file).strip());
        assertEquals(SystemPromptProvider.DEFAULT_PROMPT.strip(), provider.get().strip());
    }

    @Test
    void preservesACustomPromptAndSeesReloads(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("prompt.md");
        Files.writeString(file, "custom prompt");
        SystemPromptProvider provider = new SystemPromptProvider(file);

        provider.reload();
        assertEquals("custom prompt", provider.get());
        assertEquals("custom prompt", Files.readString(file));

        Files.writeString(file, "new prompt");
        provider.reload();
        assertEquals("new prompt", provider.get());
    }

    @Test
    void emptyFilesFallBackWithoutOverwriting(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("prompt.md");
        Files.writeString(file, "   \n");
        SystemPromptProvider provider = new SystemPromptProvider(file);

        provider.reload();

        assertEquals(SystemPromptProvider.DEFAULT_PROMPT.strip(), provider.get().strip());
        assertEquals("   \n", Files.readString(file));
    }

    @Test
    void unreadablePathsFallBack(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("prompt.md");
        Files.createDirectories(file);
        SystemPromptProvider provider = new SystemPromptProvider(file);

        provider.reload();

        assertEquals(SystemPromptProvider.DEFAULT_PROMPT.strip(), provider.get().strip());
    }

    @Test
    void lazyLoadCreatesTheFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("nested/prompt.md");
        SystemPromptProvider provider = new SystemPromptProvider(file);

        assertTrue(provider.get().contains("Player-O-Gram"));
        assertTrue(Files.isRegularFile(file));
    }
}
