package net.yizuz.playerogrammod.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BooleanSupplier;

public final class ModelDownloader {
    private final Path modelsDir;
    private final HttpClient http;

    public ModelDownloader(Path modelsDir, HttpClient http) {
        this.modelsDir = modelsDir;
        this.http = http;
    }

    public Path ensure(String baseUrl, String quant, String bearerToken, boolean allowDownload,
                       DownloadProgress progress, BooleanSupplier cancelled) {
        ModelManifest.Entry entry = ModelManifest.forQuant(quant);
        Path finalPath = modelsDir.resolve(entry.fileName());
        try {
            Files.createDirectories(modelsDir);
            if (Files.isRegularFile(finalPath)) {
                long size = Files.size(finalPath);
                if (size == entry.size()) {
                    return finalPath;
                }
                throw new AiException("The default model " + finalPath + " has an unexpected size (" + size
                        + " != " + entry.size() + "). Delete it to download a fresh copy.");
            }
            if (!allowDownload) {
                throw new AiException("The default model is not installed. Run /playerogram ai install.");
            }

            long usable = Files.getFileStore(modelsDir).getUsableSpace();
            if (usable < entry.size() + (1L << 30)) {
                throw new AiException("Not enough free disk space for " + entry.fileName() + ". At least "
                        + ((entry.size() + (1L << 30)) >> 20) + " MiB is required.");
            }

            Path part = modelsDir.resolve(entry.fileName() + ".part");
            HttpDownloader.download(http, URI.create(ModelManifest.downloadUrl(baseUrl, entry)),
                    part, entry.size(), entry.sha256(), true, bearerToken, progress, cancelled);

            if (Files.exists(finalPath)) {
                if (Files.size(finalPath) == entry.size()) {
                    Files.deleteIfExists(part);
                    return finalPath;
                }
                throw new AiException("The default model " + finalPath
                        + " appeared while downloading with an unexpected size. Delete it and retry.");
            }

            try {
                Files.move(part, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(part, finalPath);
            }
            return finalPath;
        } catch (IOException e) {
            throw new AiException("Could not install the default model: " + e.getMessage(), e);
        }
    }
}
