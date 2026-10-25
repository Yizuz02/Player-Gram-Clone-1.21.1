package net.yizuz.playerogrammod.ai;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class HttpDownloader {
    private static final String USER_AGENT = "PlayerOGramMod/0.0.1 (Minecraft NeoForge)";

    static void download(HttpClient http, URI uri, Path part, long expectedSize, String expectedSha256,
                         boolean resume, String bearerToken, DownloadProgress progress,
                         BooleanSupplier cancelled) throws IOException {
        boolean retryWithoutResume = false;
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean useResume = resume && !retryWithoutResume;
            long offset = 0;
            if (useResume && Files.isRegularFile(part)) {
                offset = Files.size(part);
                if (expectedSize > 0 && offset > expectedSize) {
                    Files.delete(part);
                    offset = 0;
                } else if (expectedSize > 0 && offset == expectedSize) {
                    verify(part, expectedSize, expectedSha256);
                    return;
                }
            } else if (Files.exists(part)) {
                Files.delete(part);
            }

            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .GET()
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(60));
            if (offset > 0) {
                request.header("Range", "bytes=" + offset + "-");
            }
            if (bearerToken != null && !bearerToken.isBlank()) {
                request.header("Authorization", "Bearer " + bearerToken);
            }

            HttpResponse<InputStream> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException("Download interrupted.", e);
            }

            int status = response.statusCode();
            try (InputStream input = response.body()) {
                if (status == 416) {
                    if (expectedSize > 0 && offset == expectedSize) {
                        verify(part, expectedSize, expectedSha256);
                        return;
                    }
                    Files.deleteIfExists(part);
                    retryWithoutResume = true;
                    continue;
                }
                if (status != 200 && status != 206) {
                    String body = readLimited(input, 4096);
                    throw new AiException("Download failed with HTTP " + status
                            + (body.isBlank() ? "" : ": " + body));
                }
                if (status == 206) {
                    String contentRange = response.headers().firstValue("Content-Range").orElse("");
                    if (!contentRange.startsWith("bytes " + offset + "-")) {
                        Files.deleteIfExists(part);
                        retryWithoutResume = true;
                        continue;
                    }
                } else {
                    offset = 0;
                }

                Files.createDirectories(part.getParent());
                Set<OpenOption> options = new HashSet<>();
                options.add(StandardOpenOption.CREATE);
                options.add(StandardOpenOption.WRITE);
                if (status == 200) {
                    options.add(StandardOpenOption.TRUNCATE_EXISTING);
                }

                long done = offset;
                long lastReport = 0L;
                byte[] buffer = new byte[1 << 16];
                try (FileChannel channel = FileChannel.open(part, options)) {
                    channel.position(offset);
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (cancelled.getAsBoolean()) {
                            throw new AiException("Installation cancelled.");
                        }
                        ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                        while (bytes.hasRemaining()) {
                            channel.write(bytes);
                        }
                        done += read;
                        long now = System.nanoTime();
                        if (now - lastReport >= 200_000_000L) {
                            progress.update(done, expectedSize);
                            lastReport = now;
                        }
                    }
                }
                progress.update(done, expectedSize);

                if (expectedSize > 0 && Files.size(part) != expectedSize) {
                    throw new AiException("Download ended early (" + Files.size(part) + " of " + expectedSize
                            + " bytes). Run the command again to resume.");
                }
                verify(part, expectedSize, expectedSha256);
                return;
            }
        }
        throw new AiException("Download failed after retrying without resume.");
    }

    static String sha256(Path file) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new AiException("SHA-256 is not available in this JVM.", e);
        }
    }

    private static void verify(Path file, long expectedSize, String expectedSha256) throws IOException {
        if (expectedSize > 0) {
            long size = Files.size(file);
            if (size != expectedSize) {
                throw new AiException("Downloaded file has the wrong size (" + size + " != " + expectedSize + "): " + file);
            }
        }
        if (expectedSha256 != null && !expectedSha256.isBlank()) {
            String actual = sha256(file);
            if (!actual.equalsIgnoreCase(expectedSha256)) {
                Files.deleteIfExists(file);
                throw new AiException("Downloaded file failed its SHA-256 check and was removed. Expected "
                        + expectedSha256 + ", got " + actual + ".");
            }
        }
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        String text = new String(input.readNBytes(limit), StandardCharsets.UTF_8);
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private HttpDownloader() {
    }
}
