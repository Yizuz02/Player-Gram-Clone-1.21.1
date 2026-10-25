package net.yizuz.playerogrammod.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpDownloaderTest {
    private final byte[] payload = "0123456789abcdefghijklmnopqrstuvwxyz"
            .repeat(200).getBytes(StandardCharsets.UTF_8);
    private final List<String> rangeHeaders = new CopyOnWriteArrayList<>();
    private volatile boolean honorRanges = true;
    private HttpServer server;
    private URI uri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            rangeHeaders.add(range == null ? "" : range);
            int start = 0;
            if (honorRanges && range != null && range.startsWith("bytes=")) {
                start = Integer.parseInt(range.substring("bytes=".length(), range.indexOf('-')));
            }
            int status = start > 0 ? 206 : 200;
            byte[] body = Arrays.copyOfRange(payload, start, payload.length);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            if (status == 206) {
                exchange.getResponseHeaders().set("Content-Range",
                        "bytes " + start + "-" + (payload.length - 1) + "/" + payload.length);
            }
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void downloadsAndVerifies(@TempDir Path dir) throws Exception {
        Path part = dir.resolve("file.part");

        HttpDownloader.download(client(), uri, part, payload.length, sha256(payload), true, null,
                DownloadProgress.NONE, () -> false);

        assertArrayEquals(payload, Files.readAllBytes(part));
        assertEquals(1, rangeHeaders.size());
        assertEquals("", rangeHeaders.get(0));
    }

    @Test
    void resumesFromPartialFile(@TempDir Path dir) throws Exception {
        Path part = dir.resolve("file.part");
        Files.write(part, Arrays.copyOf(payload, 1000));

        HttpDownloader.download(client(), uri, part, payload.length, sha256(payload), true, null,
                DownloadProgress.NONE, () -> false);

        assertArrayEquals(payload, Files.readAllBytes(part));
        assertTrue(rangeHeaders.contains("bytes=1000-"));
    }

    @Test
    void restartsWhenServerIgnoresRange(@TempDir Path dir) throws Exception {
        honorRanges = false;
        Path part = dir.resolve("file.part");
        Files.write(part, Arrays.copyOf(payload, 1000));

        HttpDownloader.download(client(), uri, part, payload.length, sha256(payload), true, null,
                DownloadProgress.NONE, () -> false);

        assertArrayEquals(payload, Files.readAllBytes(part));
    }

    @Test
    void rejectsAndDeletesCorruptCompletedPart(@TempDir Path dir) throws Exception {
        Path part = dir.resolve("file.part");
        byte[] corrupt = payload.clone();
        corrupt[0] ^= 0xFF;
        Files.write(part, corrupt);

        assertThrows(AiException.class, () -> HttpDownloader.download(client(), uri, part, payload.length,
                sha256(payload), true, null, DownloadProgress.NONE, () -> false));
        assertFalse(Files.exists(part));
    }

    @Test
    void honoursCancellation(@TempDir Path dir) {
        Path part = dir.resolve("file.part");
        assertThrows(AiException.class, () -> HttpDownloader.download(client(), uri, part, payload.length,
                sha256(payload), true, null, DownloadProgress.NONE, () -> true));
    }

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }
}
