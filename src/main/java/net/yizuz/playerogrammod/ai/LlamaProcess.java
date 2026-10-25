package net.yizuz.playerogrammod.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class LlamaProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(LlamaProcess.class);

    public static Process spawn(Path binary, Path runtimeDir, Path model, String host, int port,
                                int contextSize, int threads, int gpuLayers, boolean webUi,
                                boolean enableThinking, String apiKey, String extraLibraryPath, Path logFile) {
        List<String> command = new ArrayList<>();
        command.add(binary.toAbsolutePath().toString());
        command.add("-m");
        command.add(model.toAbsolutePath().toString());
        command.add("--host");
        command.add(host);
        command.add("--port");
        command.add(Integer.toString(port));
        command.add("-c");
        command.add(Integer.toString(contextSize));
        command.add("-ngl");
        command.add(Integer.toString(gpuLayers));
        command.add("--alias");
        command.add("playerogram");
        command.add("--jinja");
        command.add("--reasoning");
        command.add(enableThinking ? "on" : "off");
        if (!webUi) {
            command.add("--no-webui");
        }
        if (threads > 0) {
            command.add("-t");
            command.add(Integer.toString(threads));
        }

        try {
            Files.createDirectories(logFile.getParent());
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(runtimeDir.toAbsolutePath().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

            configureEnvironment(builder, runtimeDir, apiKey, extraLibraryPath);

            Process process = builder.start();
            LOGGER.info("Started llama-server (pid {}) with model {}", process.pid(), model.getFileName());
            return process;
        } catch (IOException e) {
            throw new AiException("Could not start llama-server: " + e.getMessage(), e);
        }
    }

    public record Preflight(String libraryPath) {
    }

    public static Preflight preflight(Path binary, Path runtimeDir, String extraLibraryPath) {
        String error = runVersion(binary, runtimeDir, extraLibraryPath);
        if (error == null) {
            return new Preflight(extraLibraryPath == null ? "" : extraLibraryPath);
        }
        String required = LibraryResolver.requiredVersion(error);
        if (required == null) {
            throw new AiException("llama-server cannot run on this system: " + error);
        }
        Path compatible = LibraryResolver.findCompatible(required, extraLibraryPath);
        if (compatible == null) {
            throw new AiException("llama-server cannot run on this system: " + error
                    + " Set extraLibraryPath to a directory containing a newer libstdc++.so.6.");
        }
        if (!sameDirectory(extraLibraryPath, compatible)) {
            String retry = runVersion(binary, runtimeDir, compatible.toString());
            if (retry == null) {
                LOGGER.warn("llama-server requires GLIBCXX_{} but the system libstdc++ is older; using {}.",
                        required, compatible);
                return new Preflight(compatible.toString());
            }
            throw new AiException("llama-server cannot run on this system: " + error
                    + " (also tried " + compatible + ": " + retry + ")");
        }
        throw new AiException("llama-server cannot run on this system: " + error
                + " Set extraLibraryPath to a directory containing a newer libstdc++.so.6.");
    }

    private static String runVersion(Path binary, Path runtimeDir, String extraLibraryPath) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(binary.toAbsolutePath().toString(), "--version")
                    .directory(runtimeDir.toAbsolutePath().toFile())
                    .redirectErrorStream(true);
            configureEnvironment(builder, runtimeDir, null, extraLibraryPath);
            process = builder.start();
        } catch (IOException e) {
            return "could not run llama-server --version: " + e.getMessage();
        }
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "llama-server --version timed out";
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() == 0) {
                return null;
            }
            return "exit " + process.exitValue() + ": " + tailText(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "interrupted while checking llama-server";
        } catch (IOException e) {
            process.destroyForcibly();
            return "could not read llama-server output: " + e.getMessage();
        }
    }

    private static boolean sameDirectory(String configuredPath, Path directory) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return false;
        }
        try {
            return Path.of(configuredPath.trim()).toAbsolutePath().normalize()
                    .equals(directory.toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void waitForReady(Process process, HttpClient http, String host, int port,
                                    Duration timeout, Path logFile) {
        URI uri = URI.create("http://" + connectHost(host) + ":" + port + "/health");
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new AiException("llama-server exited with code " + process.exitValue()
                        + " before it was ready." + tail(logFile));
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // The server is still loading or not listening yet.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException("Interrupted while waiting for llama-server.", e);
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException("Interrupted while waiting for llama-server.", e);
            }
        }
        throw new AiException("llama-server did not become ready within " + timeout.toSeconds() + "s." + tail(logFile));
    }

    public static int choosePort(String host, int preferred) {
        InetAddress address = bindAddress(host);
        if (preferred > 0 && canBind(address, preferred)) {
            return preferred;
        }
        if (preferred > 0) {
            LOGGER.warn("Port {} is busy; selecting a free port instead.", preferred);
        }
        try (ServerSocket socket = new ServerSocket(0, 1, address)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new AiException("Could not find a free port: " + e.getMessage(), e);
        }
    }

    public static void stop(Process process, int graceSeconds) {
        if (process == null) {
            return;
        }
        try {
            process.descendants().forEach(ProcessHandle::destroy);
        } catch (RuntimeException ignored) {
            // The process tree may already be gone.
        }
        process.destroy();
        try {
            if (!process.waitFor(graceSeconds, TimeUnit.SECONDS)) {
                try {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                } catch (RuntimeException ignored) {
                    // Best effort.
                }
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    public static void reapStale(Path pidFile) {
        if (pidFile == null || !Files.isRegularFile(pidFile)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(pidFile)) {
            properties.load(input);
        } catch (IOException e) {
            deletePid(pidFile);
            return;
        }

        long pid;
        try {
            pid = Long.parseLong(properties.getProperty("pid", "-1").trim());
        } catch (NumberFormatException e) {
            pid = -1;
        }
        if (pid > 0) {
            ProcessHandle.of(pid).ifPresent(handle -> {
                if (!handle.isAlive()) {
                    return;
                }
                String commandLine = handle.info().commandLine().orElse("");
                String command = handle.info().command().orElse("");
                if (commandLine.contains("llama-server") || command.contains("llama-server")) {
                    LOGGER.warn("Reaping orphaned llama-server process (pid {}).", handle.pid());
                    handle.descendants().forEach(ProcessHandle::destroy);
                    handle.destroy();
                    try {
                        handle.onExit().get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        handle.destroyForcibly();
                    }
                }
            });
        }
        deletePid(pidFile);
    }

    public static void writePid(Path pidFile, long pid, int port) {
        Properties properties = new Properties();
        properties.setProperty("pid", Long.toString(pid));
        properties.setProperty("port", Integer.toString(port));
        properties.setProperty("started", Instant.now().toString());
        try {
            Files.createDirectories(pidFile.getParent());
            try (OutputStream output = Files.newOutputStream(pidFile,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                properties.store(output, "Player-O-Gram llama-server");
            }
        } catch (IOException e) {
            LOGGER.warn("Could not write {}: {}", pidFile, e.getMessage());
        }
    }

    public static void deletePid(Path pidFile) {
        if (pidFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(pidFile);
        } catch (IOException ignored) {
            // Best effort.
        }
    }

    private static void configureEnvironment(ProcessBuilder builder, Path runtimeDir, String apiKey,
                                             String extraLibraryPath) {
        Map<String, String> environment = builder.environment();
        if (apiKey != null) {
            environment.put("LLAMA_API_KEY", apiKey);
        }
        String runtimePath = runtimeDir.toAbsolutePath().toString();
        if (extraLibraryPath != null && !extraLibraryPath.isBlank()) {
            runtimePath = runtimePath + File.pathSeparator + extraLibraryPath.trim();
        }
        String pathKey = isWindows() ? "Path" : "PATH";
        environment.put(pathKey, runtimePath + File.pathSeparator + environment.getOrDefault(pathKey, ""));
        environment.put("LD_LIBRARY_PATH",
                runtimePath + File.pathSeparator + environment.getOrDefault("LD_LIBRARY_PATH", ""));
        environment.put("DYLD_LIBRARY_PATH",
                runtimePath + File.pathSeparator + environment.getOrDefault("DYLD_LIBRARY_PATH", ""));
    }

    public static String connectHost(String bindHost) {
        String host = bindHost == null ? "127.0.0.1" : bindHost.trim();
        int comma = host.indexOf(',');
        if (comma >= 0) {
            host = host.substring(0, comma).trim();
        }
        if (host.isEmpty() || host.equals("0.0.0.0")) {
            return "127.0.0.1";
        }
        if (host.equals("::") || host.equals("[::]")) {
            return "[::1]";
        }
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }

    public static String tail(Path logFile) {
        if (logFile == null || !Files.isRegularFile(logFile)) {
            return "";
        }
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            long start = Math.max(0, length - 8192);
            file.seek(start);
            byte[] bytes = new byte[(int) (length - start)];
            file.readFully(bytes);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) {
                int newline = text.indexOf('\n');
                if (newline >= 0) {
                    text = text.substring(newline + 1);
                }
            }
            String[] lines = text.split("\\R");
            int from = Math.max(0, lines.length - 30);
            String tail = String.join("\n", Arrays.copyOfRange(lines, from, lines.length)).trim();
            return tail.isEmpty() ? "" : "\nLast llama-server log lines:\n" + tail;
        } catch (IOException e) {
            return "";
        }
    }

    private static String tailText(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() > 800 ? text.substring(text.length() - 800) : text;
    }

    private static InetAddress bindAddress(String host) {
        String value = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
        int comma = value.indexOf(',');
        if (comma >= 0) {
            value = value.substring(0, comma).trim();
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException e) {
            throw new AiException("Invalid host '" + host + "': " + e.getMessage(), e);
        }
    }

    private static boolean canBind(InetAddress address, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(address, port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private LlamaProcess() {
    }
}
