package net.yizuz.playerogrammod.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class ModelResolver {
    public record Result(Path path, boolean isDefault) {
    }

    private final Path gameDir;
    private final Path modelsDir;
    private final ModelDownloader downloader;

    public ModelResolver(Path gameDir, Path modelsDir, ModelDownloader downloader) {
        this.gameDir = gameDir;
        this.modelsDir = modelsDir;
        this.downloader = downloader;
    }

    public Result resolve(String explicitPath, String quant, String baseUrl, String bearerToken,
                          boolean allowDownload, DownloadProgress progress, BooleanSupplier cancelled) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path path = Path.of(explicitPath);
            if (!path.isAbsolute()) {
                path = gameDir.resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            ModelSelection.validateGguf(path);
            return new Result(path, false);
        }

        String defaultFileName = ModelManifest.forQuant(quant).fileName();
        ModelSelection.Selection selection = ModelSelection.select(listGgufs(modelsDir), defaultFileName);
        if (selection != null) {
            ModelSelection.validateGguf(selection.path());
            return new Result(selection.path(), selection.isDefault());
        }

        Path downloaded = downloader.ensure(baseUrl, quant, bearerToken, allowDownload, progress, cancelled);
        ModelSelection.validateGguf(downloaded);
        return new Result(downloaded, true);
    }

    private static List<Path> listGgufs(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gguf"))
                    .filter(path -> !ModelManifest.isAuxiliary(path.getFileName().toString()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AiException("Could not list models in " + dir + ": " + e.getMessage(), e);
        }
    }
}
