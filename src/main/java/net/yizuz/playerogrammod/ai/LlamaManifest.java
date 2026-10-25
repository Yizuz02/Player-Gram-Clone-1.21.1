package net.yizuz.playerogrammod.ai;

import java.util.Map;

public final class LlamaManifest {
    public static final String SUPPORTED_VERSION = "b11669";
    public static final String DEFAULT_BASE_URL = "https://github.com/ggml-org/llama.cpp/releases/download";

    public enum ArchiveKind {
        ZIP,
        TAR_GZ
    }

    public record Asset(String fileName, long size, String sha256, ArchiveKind kind) {
    }

    private static final Map<String, Asset> ASSETS = Map.of(
            "windows-x86_64", new Asset(
                    "llama-b11669-bin-win-cpu-x64.zip", 19_537_886L,
                    "37b3efcf962847ca869745fd4ccf7f5e7e8dacfeff9fa3523b4ca8cdc66d632b", ArchiveKind.ZIP),
            "windows-aarch64", new Asset(
                    "llama-b11669-bin-win-cpu-arm64.zip", 12_442_177L,
                    "9cff39ffed17edcd03a7d876073f74d7b48cd43a1e5eafdd59ee094185ad871b", ArchiveKind.ZIP),
            "linux-x86_64", new Asset(
                    "llama-b11669-bin-ubuntu-x64.tar.gz", 17_851_611L,
                    "cf07c1c112d709bd67e522017471de57ed0ec8bea031d732c5422351c07f65ac", ArchiveKind.TAR_GZ),
            "linux-aarch64", new Asset(
                    "llama-b11669-bin-ubuntu-arm64.tar.gz", 13_900_160L,
                    "9ac6fda2058d35677608a06f751a50122ddfa4ce0ddb7f084466b8e79b6d4a99", ArchiveKind.TAR_GZ),
            "macos-aarch64", new Asset(
                    "llama-b11669-bin-macos-arm64.tar.gz", 12_191_582L,
                    "dbed55493d72bc1ba2babc8770d669c2289b5bc4ee9fb2a4dfc158d45b7d13fd", ArchiveKind.TAR_GZ),
            "macos-x86_64", new Asset(
                    "llama-b11669-bin-macos-x64.tar.gz", 11_677_404L,
                    "cab28238d78158109b48deb27b05d50e0dcef56275d2e76da9ca8281526c7532", ArchiveKind.TAR_GZ)
    );

    public static void requireSupportedVersion(String version) {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new AiException("Unsupported runtimeVersion '" + version + "'. Supported: " + SUPPORTED_VERSION + ".");
        }
    }

    public static Asset assetFor(AiPlatform target) {
        Asset asset = ASSETS.get(target.id());
        if (asset == null) {
            throw new AiException("No llama.cpp CPU build is available for " + target.id()
                    + ". Set runtimePath to a manually installed llama-server, or use a supported platform.");
        }
        return asset;
    }

    public static String downloadUrl(String baseUrl, String version, Asset asset) {
        return trimTrailingSlash(baseUrl) + "/" + version + "/" + asset.fileName();
    }

    private static String trimTrailingSlash(String value) {
        String v = value == null ? "" : value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (v.isEmpty()) {
            throw new AiException("downloadBaseUrl is empty.");
        }
        return v;
    }

    private LlamaManifest() {
    }
}
