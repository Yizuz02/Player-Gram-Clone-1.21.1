package net.yizuz.playerogrammod.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSelectionTest {
    private static final String DEFAULT = "gemma-4-E2B-it-Q4_K_M.gguf";

    @Test
    void customModelWinsOverDefault(@TempDir Path dir) throws IOException {
        Path defaultModel = Files.createFile(dir.resolve(DEFAULT));
        Path custom = Files.createFile(dir.resolve("my-model.gguf"));

        ModelSelection.Selection selection = ModelSelection.select(List.of(defaultModel, custom), DEFAULT);

        assertNotNull(selection);
        assertEquals(custom, selection.path());
        assertFalse(selection.isDefault());
    }

    @Test
    void defaultIsUsedWhenNoCustomModel(@TempDir Path dir) throws IOException {
        Path defaultModel = Files.createFile(dir.resolve(DEFAULT));

        ModelSelection.Selection selection = ModelSelection.select(List.of(defaultModel), DEFAULT);

        assertNotNull(selection);
        assertEquals(defaultModel, selection.path());
        assertTrue(selection.isDefault());
    }

    @Test
    void noSelectionWhenEmpty(@TempDir Path dir) {
        assertEquals(null, ModelSelection.select(List.of(), DEFAULT));
    }

    @Test
    void multipleCustomModelsAreRejected(@TempDir Path dir) throws IOException {
        Path first = Files.createFile(dir.resolve("a.gguf"));
        Path second = Files.createFile(dir.resolve("b.gguf"));

        assertThrows(AiException.class, () -> ModelSelection.select(List.of(first, second), DEFAULT));
    }

    @Test
    void shardSetsCollapseToFirst(@TempDir Path dir) throws IOException {
        Path first = Files.createFile(dir.resolve("big-00001-of-00003.gguf"));
        Path second = Files.createFile(dir.resolve("big-00002-of-00003.gguf"));
        Path third = Files.createFile(dir.resolve("big-00003-of-00003.gguf"));

        List<Path> collapsed = ModelSelection.collapseShards(List.of(third, second, first));

        assertEquals(1, collapsed.size());
        assertEquals(first, collapsed.get(0));
    }

    @Test
    void auxiliaryFilesAreIgnored() {
        assertTrue(ModelManifest.isAuxiliary("mmproj-F16.gguf"));
        assertTrue(ModelManifest.isAuxiliary("mtp-gemma-4.gguf"));
        assertTrue(ModelManifest.isAuxiliary("imatrix_unsloth.gguf_file"));
        assertTrue(ModelManifest.isAuxiliary("model.gguf.part"));
        assertFalse(ModelManifest.isAuxiliary("my-model.gguf"));
        assertTrue(ModelManifest.isDefaultFamily("gemma-4-E2B-it-Q4_K_M.gguf"));
        assertFalse(ModelManifest.isDefaultFamily("gemma-4-E2B-it-finetuned.gguf"));
        assertFalse(ModelManifest.isDefaultFamily("my-model.gguf"));
    }
}
