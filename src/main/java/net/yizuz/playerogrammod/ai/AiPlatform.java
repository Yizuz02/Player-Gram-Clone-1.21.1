package net.yizuz.playerogrammod.ai;

import java.util.Locale;

public record AiPlatform(String os, String arch) {
    public static AiPlatform detect() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static AiPlatform detect(String osName, String archName) {
        String os = normalizeOs(osName);
        String arch = normalizeArch(archName);
        return new AiPlatform(os, arch);
    }

    public String id() {
        return os + "-" + arch;
    }

    public String binaryName() {
        return os.equals("windows") ? "llama-server.exe" : "llama-server";
    }

    public boolean isWindows() {
        return os.equals("windows");
    }

    private static String normalizeOs(String value) {
        String v = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (v.contains("mac") || v.contains("darwin") || v.contains("os x")) {
            return "macos";
        }
        if (v.contains("win")) {
            return "windows";
        }
        if (v.contains("linux")) {
            return "linux";
        }
        throw new AiException("Unsupported operating system: " + value + ". Supported: Windows, Linux, macOS.");
    }

    private static String normalizeArch(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "amd64", "x86_64", "x86-64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new AiException("Unsupported CPU architecture: " + value + ". Supported: x86_64, aarch64.");
        };
    }
}
