package net.yizuz.playerogrammod.ai;

import net.yizuz.playerogrammod.ai.archive.TarGzExtractor;
import net.yizuz.playerogrammod.ai.archive.ZipExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Comparator;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class RuntimeInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeInstaller.class);

    private final Path llmRoot;
    private final HttpClient http;

    public RuntimeInstaller(Path llmRoot, HttpClient http) {
        this.llmRoot = llmRoot;
        this.http = http;
    }

    public Path ensureInstalled(String version, String baseUrl, AiPlatform target, boolean allowDownload,
                                DownloadProgress progress, BooleanSupplier cancelled) {
        LlamaManifest.requireSupportedVersion(version);
        LlamaManifest.Asset asset = LlamaManifest.assetFor(target);
        Path dir = llmRoot.resolve(version).resolve(target.id());
        Path binary = installedBinary(dir, target);

        if (isInstalled(dir, binary, version, target, asset)) {
            ensureExecutable(binary, target);
            return binary;
        }
        if (!allowDownload) {
            throw new AiException("The llama.cpp runtime is not installed. Run /playerogram ai install.");
        }
        return withLock(llmRoot.resolve(".install.lock"),
                () -> installLocked(version, baseUrl, target, asset, dir, progress, cancelled));
    }

    private Path installLocked(String version, String baseUrl, AiPlatform target, LlamaManifest.Asset asset,
                               Path dir, DownloadProgress progress, BooleanSupplier cancelled) {
        Path existing = installedBinary(dir, target);
        if (isInstalled(dir, existing, version, target, asset)) {
            ensureExecutable(existing, target);
            return existing;
        }

        Path part = llmRoot.resolve("downloads").resolve(asset.fileName() + ".part");
        Path tmp = llmRoot.resolve(version).resolve(target.id() + ".tmp");
        try {
            Files.createDirectories(part.getParent());
            HttpDownloader.download(http, URI.create(LlamaManifest.downloadUrl(baseUrl, version, asset)),
                    part, asset.size(), asset.sha256(), false, null, progress, cancelled);

            deleteRecursively(tmp);
            Files.createDirectories(tmp);
            int extracted = asset.kind() == LlamaManifest.ArchiveKind.ZIP
                    ? ZipExtractor.extract(part, tmp)
                    : TarGzExtractor.extract(part, tmp);

            Path found = findBinary(tmp, target.binaryName());
            if (found == null) {
                throw new AiException("The downloaded runtime archive did not contain " + target.binaryName() + ".");
            }
            ensureExecutable(found, target);
            writeInstalledProperties(tmp, version, target, asset);

            deleteRecursively(dir);
            Files.createDirectories(dir.getParent());
            atomicMove(tmp, dir);
            Files.deleteIfExists(part);

            Path relative = tmp.relativize(found);
            LOGGER.info("Installed llama.cpp {} for {} ({} files).", version, target.id(), extracted);
            return dir.resolve(relative).normalize();
        } catch (IOException e) {
            deleteRecursively(tmp);
            throw new AiException("Could not install the llama.cpp runtime: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteRecursively(tmp);
            throw e;
        }
    }

    private static boolean isInstalled(Path dir, Path binary, String version, AiPlatform target,
                                       LlamaManifest.Asset asset) {
        if (binary == null || !Files.isRegularFile(binary)) {
            return false;
        }
        Path propertiesFile = dir.resolve(".installed.properties");
        if (!Files.isRegularFile(propertiesFile)) {
            return false;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        } catch (IOException e) {
            return false;
        }
        return version.equals(properties.getProperty("version"))
                && target.id().equals(properties.getProperty("target"))
                && asset.sha256().equalsIgnoreCase(properties.getProperty("sha256"));
    }

    private static void writeInstalledProperties(Path dir, String version, AiPlatform target,
                                                 LlamaManifest.Asset asset) {
        Properties properties = new Properties();
        properties.setProperty("version", version);
        properties.setProperty("target", target.id());
        properties.setProperty("asset", asset.fileName());
        properties.setProperty("sha256", asset.sha256());
        properties.setProperty("installedAt", Instant.now().toString());
        try (OutputStream output = Files.newOutputStream(dir.resolve(".installed.properties"))) {
            properties.store(output, "Player-O-Gram managed llama.cpp runtime");
        } catch (IOException e) {
            throw new AiException("Could not write runtime metadata: " + e.getMessage(), e);
        }
    }

    public static Path findInstalledBinary(Path llmRoot, String version, AiPlatform target) {
        return installedBinary(llmRoot.resolve(version).resolve(target.id()), target);
    }

    private static Path installedBinary(Path dir, AiPlatform target) {
        if (!Files.isRegularFile(dir.resolve(".installed.properties"))) {
            return null;
        }
        try {
            return findBinary(dir, target.binaryName());
        } catch (IOException e) {
            return null;
        }
    }

    private static Path findBinary(Path root, String binaryName) throws IOException {
        try (Stream<Path> walk = Files.walk(root, 6)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(binaryName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void ensureExecutable(Path binary, AiPlatform target) {
        if (target.isWindows()) {
            return;
        }
        try {
            if (!Files.isExecutable(binary)) {
                Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        } catch (UnsupportedOperationException | IOException e) {
            if (!binary.toFile().setExecutable(true, false)) {
                throw new AiException("Could not make llama-server executable: " + binary, e);
            }
        }
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException ignored) {
                    // Best effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static <T> T withLock(Path lockFile, Supplier<T> action) {
        try {
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw new AiException("Another Player-O-Gram process is installing the AI runtime. Try again later.");
                }
                return action.get();
            }
        } catch (OverlappingFileLockException e) {
            throw new AiException("Another Player-O-Gram process is installing the AI runtime. Try again later.", e);
        } catch (IOException e) {
            throw new AiException("Could not lock the AI runtime directory: " + e.getMessage(), e);
        }
    }
}
