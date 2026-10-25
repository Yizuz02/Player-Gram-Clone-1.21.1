package net.yizuz.playerogrammod.ai;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import net.yizuz.playerogrammod.Config;
import org.slf4j.Logger;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class AiService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AiService INSTANCE = new AiService();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ExecutorService lifecycle = Executors.newSingleThreadExecutor(
            runnable -> daemon("playerogram-ai-lifecycle", runnable));
    private final ExecutorService io = Executors.newCachedThreadPool(
            runnable -> daemon("playerogram-ai-io", runnable));
    private final HttpClient downloadClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    private final HttpClient apiClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final LlamaClient llama = new LlamaClient(apiClient);
    private final Object processLock = new Object();
    private final List<Runnable> readyListeners = new CopyOnWriteArrayList<>();

    private volatile AiState state = AiState.STOPPED;
    private volatile Process process;
    private volatile Path runtimeBinary;
    private volatile Path modelPath;
    private volatile boolean modelIsDefault;
    private volatile String runtimeLabel = LlamaManifest.SUPPORTED_VERSION;
    private volatile String host = "127.0.0.1";
    private volatile int port = -1;
    private volatile String apiKey;
    private volatile Instant startedAt;
    private volatile String lastError;
    private volatile String activeSignature;
    private volatile boolean cancelRequested;
    private volatile boolean initialized;

    private AiService() {
    }

    public static AiService get() {
        return INSTANCE;
    }

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopNow, "playerogram-ai-shutdown"));
    }

    public void onServerStarted() {
        Settings settings;
        try {
            settings = settings();
        } catch (RuntimeException e) {
            LOGGER.warn("Player-O-Gram AI config is unavailable: {}", rootMessage(e));
            return;
        }
        if (settings.enabled() && settings.autoStart()) {
            start().whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    LOGGER.warn("Player-O-Gram AI auto-start failed: {}", rootMessage(throwable));
                }
            });
        }
    }

    public void onServerStopping() {
        cancelRequested = true;
        try {
            stop().get(12, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Player-O-Gram AI did not stop cleanly: {}", rootMessage(e));
            stopNow();
        }
    }

    public void onConfigChanged() {
        lifecycle.execute(() -> {
            try {
                Settings settings = settings();
                if (!settings.enabled()) {
                    if (state != AiState.STOPPED) {
                        cancelRequested = true;
                        stopInternal();
                    }
                    return;
                }
                if (state == AiState.READY && activeSignature != null && modelPath != null
                        && !activeSignature.equals(settings.launchSignature() + "|" + modelPath.toAbsolutePath())) {
                    LOGGER.info("Player-O-Gram AI settings changed; restarting llama-server.");
                    cancelRequested = true;
                    stopInternal();
                    try {
                        startInternal(false, true, null);
                    } catch (RuntimeException e) {
                        LOGGER.warn("Could not restart the AI: {}", rootMessage(e));
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Could not apply the Player-O-Gram AI config change: {}", rootMessage(e));
            }
        });
    }

    public CompletableFuture<Void> install() {
        return install(null);
    }

    public CompletableFuture<Void> install(Consumer<String> progress) {
        return submit(() -> startInternal(true, false, progress));
    }

    public CompletableFuture<Void> start() {
        return start(null);
    }

    public CompletableFuture<Void> start(Consumer<String> progress) {
        return submit(() -> startInternal(false, true, progress));
    }

    public CompletableFuture<Void> stop() {
        cancelRequested = true;
        return submit(this::stopInternal);
    }

    public CompletableFuture<String> complete(List<LlamaClient.Message> messages) {
        List<LlamaClient.Message> request = List.copyOf(messages);
        return submit(() -> startInternal(false, true, null)).thenApplyAsync(ignored -> {
            int currentPort = port;
            String currentKey = apiKey;
            if (state != AiState.READY || currentPort <= 0) {
                throw new CompletionException(new AiException("The AI is not running."));
            }
            Settings settings;
            try {
                settings = settings();
            } catch (RuntimeException e) {
                throw new CompletionException(e);
            }
            try {
                return llama.chat(host, currentPort, currentKey, request, settings.temperature(),
                        settings.maxTokens(), settings.enableThinking(),
                        Duration.ofSeconds(settings.requestTimeoutSeconds()));
            } catch (RuntimeException e) {
                throw new CompletionException(userFacing(e));
            }
        }, io);
    }

    public void addReadyListener(Runnable listener) {
        if (listener == null) {
            return;
        }
        readyListeners.add(listener);
        if (state == AiState.READY) {
            runReadyListener(listener);
        }
    }

    private void notifyReady() {
        for (Runnable listener : readyListeners) {
            runReadyListener(listener);
        }
    }

    private static void runReadyListener(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException e) {
            LOGGER.warn("AI ready listener failed.", e);
        }
    }

    public AiStatus status() {
        try {
            Settings settings = settings();
            AiPlatform target = AiPlatform.detect();
            Path binary = runtimeBinary;
            if (binary == null) {
                if (!settings.runtimePath().isBlank()) {
                    Path custom = Path.of(settings.runtimePath());
                    if (!custom.isAbsolute()) {
                        custom = gameDir().resolve(custom);
                    }
                    if (Files.isDirectory(custom)) {
                        custom = custom.resolve(target.binaryName());
                    }
                    binary = custom;
                } else {
                    binary = RuntimeInstaller.findInstalledBinary(llmRoot(), settings.runtimeVersion(), target);
                }
            }
            boolean installed = binary != null && Files.isRegularFile(binary);
            Instant started = startedAt;
            long uptime = started == null ? 0 : Math.max(0, Duration.between(started, Instant.now()).toSeconds());
            Process running = process;
            long pid = running != null && running.isAlive() ? running.pid() : -1;
            return new AiStatus(state, target.id(), settings.runtimeVersion(),
                    binary == null ? null : binary.toString(), installed,
                    modelPath == null ? null : modelPath.toString(), modelIsDefault,
                    host, port, pid, uptime, lastError);
        } catch (RuntimeException e) {
            Process running = process;
            return new AiStatus(state, "unknown", runtimeLabel,
                    runtimeBinary == null ? null : runtimeBinary.toString(),
                    runtimeBinary != null && Files.isRegularFile(runtimeBinary),
                    modelPath == null ? null : modelPath.toString(), modelIsDefault,
                    host, port, running != null && running.isAlive() ? running.pid() : -1, 0, rootMessage(e));
        }
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            message = current == null ? "unknown error" : current.getClass().getSimpleName();
        }
        return message;
    }

    private CompletableFuture<Void> submit(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        lifecycle.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(userFacing(throwable));
            }
        });
        return future;
    }

    private void startInternal(boolean forceInstall, boolean launch, Consumer<String> progress) {
        cancelRequested = false;
        Settings settings;
        try {
            settings = settings();
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }
        if (!settings.enabled()) {
            AiException error = new AiException("The AI is disabled in the config (aiEnabled=false).");
            fail(error);
            throw error;
        }
        if (state == AiState.READY && process != null && process.isAlive()) {
            return;
        }

        state = AiState.INSTALLING;
        lastError = null;
        Path binary;
        ModelResolver.Result modelResult;
        try {
            binary = ensureRuntime(settings, forceInstall, progress);
            progressMessage(progress, "Checking the GGUF model…");
            modelResult = new ModelResolver(gameDir(), modelsDir(),
                    new ModelDownloader(modelsDir(), downloadClient))
                    .resolve(settings.modelPath(), settings.defaultModelQuant(),
                            settings.modelDownloadBaseUrl(), hfToken(settings),
                            forceInstall || settings.autoDownloadModel(),
                            downloadProgress(progress, "Downloading model"), () -> cancelRequested);
        } catch (RuntimeException e) {
            fail(e);
            throw e;
        }

        runtimeBinary = binary;
        runtimeLabel = settings.runtimeVersion();
        modelPath = modelResult.path();
        modelIsDefault = modelResult.isDefault();

        if (!launch) {
            state = AiState.STOPPED;
            LOGGER.info("Player-O-Gram AI runtime and model are installed (model {}).", modelPath.getFileName());
            return;
        }

        state = AiState.STARTING;
        Process started = null;
        try {
            LlamaProcess.reapStale(pidFile());
            int chosenPort = LlamaProcess.choosePort(settings.host(), settings.port());
            String key = newApiKey();
            Path log = logFile(chosenPort);
            LlamaProcess.Preflight preflight = LlamaProcess.preflight(binary, binary.getParent(),
                    settings.extraLibraryPath());
            started = LlamaProcess.spawn(binary, binary.getParent(), modelPath, settings.host(), chosenPort,
                    settings.contextSize(), settings.threads(), settings.gpuLayers(), settings.webUi(),
                    settings.enableThinking(), key, preflight.libraryPath(), log);

            synchronized (processLock) {
                process = started;
                port = chosenPort;
                apiKey = key;
                host = settings.host();
                startedAt = Instant.now();
            }
            LlamaProcess.writePid(pidFile(), started.pid(), chosenPort);
            progressMessage(progress, "Loading the model into memory…");
            LlamaProcess.waitForReady(started, apiClient, settings.host(), chosenPort,
                    Duration.ofSeconds(settings.startupTimeoutSeconds()), log);

            activeSignature = settings.launchSignature() + "|" + modelPath.toAbsolutePath();
            lastError = null;
            state = AiState.READY;
            notifyReady();
            LOGGER.info("Player-O-Gram AI is ready at http://{}:{} (model {}, pid {}).",
                    LlamaProcess.connectHost(settings.host()), chosenPort, modelPath.getFileName(), started.pid());
        } catch (RuntimeException e) {
            if (started != null) {
                LlamaProcess.stop(started, 5);
            }
            synchronized (processLock) {
                if (process == started) {
                    process = null;
                }
            }
            LlamaProcess.deletePid(pidFile());
            clearRunState();
            fail(e);
            throw e;
        }
    }

    private Path ensureRuntime(Settings settings, boolean forceInstall, Consumer<String> progress) {
        if (!settings.runtimePath().isBlank()) {
            Path path = Path.of(settings.runtimePath());
            if (!path.isAbsolute()) {
                path = gameDir().resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                path = path.resolve(AiPlatform.detect().binaryName());
            }
            if (!Files.isRegularFile(path)) {
                throw new AiException("runtimePath does not point to a llama-server binary: " + path);
            }
            return path;
        }
        AiPlatform target = AiPlatform.detect();
        boolean allowDownload = forceInstall || settings.autoInstallRuntime();
        if (allowDownload) {
            progressMessage(progress, "Checking the llama.cpp runtime for " + target.id() + "…");
        }
        return new RuntimeInstaller(llmRoot(), downloadClient)
                .ensureInstalled(settings.runtimeVersion(), settings.downloadBaseUrl(), target, allowDownload,
                        downloadProgress(progress, "Downloading llama.cpp runtime"), () -> cancelRequested);
    }

    private void stopInternal() {
        synchronized (processLock) {
            Process running = process;
            if (running == null) {
                state = AiState.STOPPED;
                clearRunState();
                return;
            }
            state = AiState.STOPPING;
            LOGGER.info("Stopping llama-server (pid {})…", running.pid());
            LlamaProcess.stop(running, 8);
            process = null;
            LlamaProcess.deletePid(pidFile());
            clearRunState();
            state = AiState.STOPPED;
            lastError = null;
            LOGGER.info("llama-server stopped.");
        }
    }

    private void stopNow() {
        try {
            cancelRequested = true;
            Process running;
            synchronized (processLock) {
                running = process;
            }
            if (running != null) {
                LlamaProcess.stop(running, 3);
            }
            LlamaProcess.deletePid(pidFileQuietly());
            synchronized (processLock) {
                process = null;
                state = AiState.STOPPED;
            }
        } catch (Throwable ignored) {
            // Nothing useful can be done during JVM shutdown.
        }
    }

    private void clearRunState() {
        port = -1;
        apiKey = null;
        startedAt = null;
        activeSignature = null;
    }

    private void fail(Throwable throwable) {
        String message = rootMessage(throwable);
        state = AiState.FAILED;
        lastError = message;
        LOGGER.warn("Player-O-Gram AI error: {}", message, throwable);
    }

    private static RuntimeException userFacing(Throwable throwable) {
        if (throwable instanceof AiException ai) {
            return ai;
        }
        return new AiException(rootMessage(throwable), throwable);
    }

    private static Settings settings() {
        try {
            return Settings.fromConfig();
        } catch (IllegalStateException e) {
            throw new AiException("The Player-O-Gram config is not loaded yet. Try again in a moment.", e);
        }
    }

    private static String hfToken(Settings settings) {
        String environment = System.getenv("HF_TOKEN");
        if (environment != null && !environment.isBlank()) {
            return environment.trim();
        }
        return settings.huggingFaceToken().isBlank() ? null : settings.huggingFaceToken().trim();
    }

    private static DownloadProgress downloadProgress(Consumer<String> sink, String label) {
        AtomicInteger lastLogged = new AtomicInteger(-5);
        AtomicInteger lastBucket = new AtomicInteger(0);
        return (done, total) -> {
            if (total <= 0) {
                return;
            }
            int percent = (int) Math.min(100, done * 100 / total);
            int last = lastLogged.get();
            if ((percent >= last + 5 || percent == 100) && lastLogged.compareAndSet(last, percent)) {
                LOGGER.debug("{}: {}% ({}/{} MiB)", label, percent, done >> 20, total >> 20);
            }
            if (sink != null) {
                int bucket = percent / 25;
                int previous = lastBucket.get();
                if (bucket > previous && bucket >= 1 && bucket <= 3 && lastBucket.compareAndSet(previous, bucket)) {
                    sink.accept(label + ": " + (bucket * 25) + "%");
                }
            }
        };
    }

    private static void progressMessage(Consumer<String> progress, String message) {
        if (progress != null) {
            try {
                progress.accept(message);
            } catch (RuntimeException ignored) {
                // The command source may no longer be valid.
            }
        }
    }

    private static String newApiKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    private static Path gameDir() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    }

    private static Path rootDir() {
        return gameDir().resolve("playerogram");
    }

    private static Path llmRoot() {
        return rootDir().resolve("llm");
    }

    private static Path modelsDir() {
        return rootDir().resolve("models");
    }

    private static Path pidFile() {
        return llmRoot().resolve("runtime.pid");
    }

    private static Path pidFileQuietly() {
        try {
            return pidFile();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path logFile(int port) {
        return llmRoot().resolve("logs").resolve("llama-server-" + port + ".log");
    }

    private static Thread daemon(String name, Runnable runnable) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private record Settings(
            boolean enabled,
            boolean autoStart,
            boolean autoInstallRuntime,
            boolean autoDownloadModel,
            String runtimeVersion,
            String runtimePath,
            String downloadBaseUrl,
            String modelPath,
            String defaultModelQuant,
            String modelDownloadBaseUrl,
            String huggingFaceToken,
            String host,
            int port,
            int contextSize,
            int threads,
            int gpuLayers,
            double temperature,
            int maxTokens,
            boolean enableThinking,
            String extraLibraryPath,
            int startupTimeoutSeconds,
            int requestTimeoutSeconds,
            boolean webUi
    ) {
        static Settings fromConfig() {
            String configuredHost = Config.HOST.get().trim();
            return new Settings(
                    Config.AI_ENABLED.get(),
                    Config.AUTO_START.get(),
                    Config.AUTO_INSTALL_RUNTIME.get(),
                    Config.AUTO_DOWNLOAD_MODEL.get(),
                    Config.RUNTIME_VERSION.get(),
                    Config.RUNTIME_PATH.get(),
                    Config.DOWNLOAD_BASE_URL.get(),
                    Config.MODEL_PATH.get(),
                    Config.DEFAULT_MODEL_QUANT.get(),
                    Config.MODEL_DOWNLOAD_BASE_URL.get(),
                    Config.HUGGING_FACE_TOKEN.get(),
                    configuredHost.isEmpty() ? "127.0.0.1" : configuredHost,
                    Config.PORT.get(),
                    Config.CONTEXT_SIZE.get(),
                    Config.THREADS.get(),
                    Config.GPU_LAYERS.get(),
                    Config.TEMPERATURE.get(),
                    Config.MAX_TOKENS.get(),
                    Config.ENABLE_THINKING.get(),
                    Config.EXTRA_LIBRARY_PATH.get(),
                    Config.STARTUP_TIMEOUT_SECONDS.get(),
                    Config.REQUEST_TIMEOUT_SECONDS.get(),
                    Config.WEB_UI.get()
            );
        }

        String launchSignature() {
            return String.join("|", runtimeVersion, runtimePath, modelPath, defaultModelQuant, host,
                    Integer.toString(port), Integer.toString(contextSize), Integer.toString(threads),
                    Integer.toString(gpuLayers), Boolean.toString(webUi),
                    Boolean.toString(enableThinking),
                    extraLibraryPath == null ? "" : extraLibraryPath);
        }
    }
}
