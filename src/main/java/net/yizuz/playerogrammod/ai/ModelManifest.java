package net.yizuz.playerogrammod.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ModelManifest {
    public static final String REPO = "unsloth/gemma-4-E2B-it-GGUF";
    public static final String DEFAULT_QUANT = "Q4_K_M";
    public static final String DEFAULT_BASE_URL = "https://huggingface.co";

    public record Entry(String fileName, long size, String sha256) {
    }

    private static final Map<String, Entry> QUANTS;

    static {
        Map<String, Entry> quants = new LinkedHashMap<>();
        quants.put("Q3_K_M", new Entry(
                "gemma-4-E2B-it-Q3_K_M.gguf", 2_536_786_016L,
                "086e2f5ba85057f8f19712e3160a644728f74f323c9feeac4cd73fab11b43085"));
        quants.put("IQ4_XS", new Entry(
                "gemma-4-E2B-it-IQ4_XS.gguf", 2_983_944_288L,
                "c149620926221669c4941ae6dac259953cc729c48e4a4edb69e9807b782fd1e3"));
        quants.put("Q4_K_S", new Entry(
                "gemma-4-E2B-it-Q4_K_S.gguf", 3_043_934_304L,
                "96ee1ee1d562be52dbbc4c12694ed67de8c6ae09451d420fed7d067e552c25cb"));
        quants.put("Q4_K_M", new Entry(
                "gemma-4-E2B-it-Q4_K_M.gguf", 3_106_738_272L,
                "740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8"));
        quants.put("UD-Q4_K_XL", new Entry(
                "gemma-4-E2B-it-UD-Q4_K_XL.gguf", 3_184_496_736L,
                "b52f438017efaec5debf1c0d8be690571e212a07c312f1102bbce927258cfc32"));
        quants.put("Q5_K_M", new Entry(
                "gemma-4-E2B-it-Q5_K_M.gguf", 3_356_037_216L,
                "90293b8cdaf9c973012bf4df8a1e92bde7d74ad66a4fe56cf905ccd563d660c5"));
        quants.put("Q8_0", new Entry(
                "gemma-4-E2B-it-Q8_0.gguf", 5_048_352_864L,
                "605d3c2647d7c58c1e4b5375ccb5702acf94c2611b4c8d4877812f8fdd32d053"));
        QUANTS = Map.copyOf(quants);
    }

    public static Entry forQuant(String quant) {
        Entry entry = QUANTS.get(quant);
        if (entry == null) {
            throw new AiException("Unknown defaultModelQuant '" + quant + "'. Supported: "
                    + String.join(", ", QUANTS.keySet()) + ".");
        }
        return entry;
    }

    public static String defaultFileName() {
        return forQuant(DEFAULT_QUANT).fileName();
    }

    public static boolean isDefaultFamily(String fileName) {
        if (isAuxiliary(fileName)) {
            return false;
        }
        for (Entry entry : QUANTS.values()) {
            if (entry.fileName().equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAuxiliary(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.startsWith(".")
                || name.startsWith("mmproj")
                || name.startsWith("mtp-")
                || name.startsWith("mtp_")
                || name.startsWith("imatrix")
                || name.endsWith(".part");
    }

    public static String downloadUrl(String baseUrl, Entry entry) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            throw new AiException("modelDownloadBaseUrl is empty.");
        }
        return base + "/" + REPO + "/resolve/main/" + entry.fileName() + "?download=true";
    }

    private ModelManifest() {
    }
}
