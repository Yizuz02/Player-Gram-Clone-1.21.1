package net.yizuz.playerogrammod;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue AI_ENABLED = BUILDER
            .comment("Master switch for the Player-O-Gram AI subsystem.",
                    "Disabling this while the AI is running stops the llama-server process.")
            .define("aiEnabled", true);

    public static final ModConfigSpec.IntValue HISTORY_MESSAGES = BUILDER
            .comment("Maximum user/assistant messages retained per player. 0 disables history.")
            .defineInRange("historyMessages", 12, 0, 200);

    public static final ModConfigSpec.IntValue HISTORY_MAX_CHARS = BUILDER
            .comment("Maximum total characters of retained history per player. 0 disables the character cap.")
            .defineInRange("historyMaxChars", 6000, 0, 100000);

    public static final ModConfigSpec.BooleanValue AUTO_START = BUILDER
            .comment("Start the AI when the server starts, provided the runtime and model are already installed.")
            .define("autoStart", false);

    public static final ModConfigSpec.BooleanValue AUTO_INSTALL_RUNTIME = BUILDER
            .comment("Allow /playerogram ai ask and autoStart to download the ~18 MB llama.cpp runtime.",
                    "Set to false to require an explicit /playerogram ai install first.")
            .define("autoInstallRuntime", true);

    public static final ModConfigSpec.BooleanValue AUTO_DOWNLOAD_MODEL = BUILDER
            .comment("Allow /playerogram ai ask and autoStart to download the default Gemma GGUF (~2.9 GiB).",
                    "Set to false to require an explicit /playerogram ai install first.")
            .define("autoDownloadModel", true);

    public static final ModConfigSpec.ConfigValue<String> RUNTIME_VERSION = BUILDER
            .comment("Pinned llama.cpp build. Only versions listed in LlamaManifest are supported.")
            .define("runtimeVersion", "b11669");

    public static final ModConfigSpec.ConfigValue<String> RUNTIME_PATH = BUILDER
            .comment("Optional path to a pre-installed llama-server binary or its directory.",
                    "When set, the runtime download is skipped entirely.")
            .define("runtimePath", "");

    public static final ModConfigSpec.ConfigValue<String> DOWNLOAD_BASE_URL = BUILDER
            .comment("Base URL for llama.cpp release assets. Change this to use a mirror.")
            .define("downloadBaseUrl", "https://github.com/ggml-org/llama.cpp/releases/download");

    public static final ModConfigSpec.ConfigValue<String> MODEL_PATH = BUILDER
            .comment("Optional explicit GGUF path (absolute, or relative to the game directory).",
                    "When empty, a custom model in playerogram/models wins; otherwise the default Gemma model is used.")
            .define("modelPath", "");

    public static final ModConfigSpec.ConfigValue<String> DEFAULT_MODEL_QUANT = BUILDER
            .comment("Quant used when the default Gemma model must be downloaded.",
                    "Supported: Q3_K_M, IQ4_XS, Q4_K_S, Q4_K_M, UD-Q4_K_XL, Q5_K_M, Q8_0.")
            .define("defaultModelQuant", "Q4_K_M");

    public static final ModConfigSpec.ConfigValue<String> MODEL_DOWNLOAD_BASE_URL = BUILDER
            .comment("Base URL for Hugging Face model downloads. Change this to use a mirror.")
            .define("modelDownloadBaseUrl", "https://huggingface.co");

    public static final ModConfigSpec.ConfigValue<String> HUGGING_FACE_TOKEN = BUILDER
            .comment("Optional Hugging Face token for mirrors or future gated repositories.",
                    "The HF_TOKEN environment variable takes priority. Never written to logs.")
            .define("huggingFaceToken", "");

    public static final ModConfigSpec.ConfigValue<String> HOST = BUILDER
            .comment("Address llama-server binds to. Keep 127.0.0.1 unless you need LAN access.",
                    "Binding to a non-loopback address exposes the local API to the network.")
            .define("host", "127.0.0.1");

    public static final ModConfigSpec.IntValue PORT = BUILDER
            .comment("Preferred llama-server port. 0 picks a free port; a busy port also falls back to a free one.")
            .defineInRange("port", 9931, 0, 65535);

    public static final ModConfigSpec.IntValue CONTEXT_SIZE = BUILDER
            .comment("Prompt context size. Keep this bounded on CPU: the Gemma model native context is 131072.")
            .defineInRange("contextSize", 4096, 256, 262144);

    public static final ModConfigSpec.IntValue THREADS = BUILDER
            .comment("CPU threads for generation. 0 lets llama.cpp choose.")
            .defineInRange("threads", 0, 0, 1024);

    public static final ModConfigSpec.IntValue GPU_LAYERS = BUILDER
            .comment("Layers to offload to a GPU. 0 keeps everything on the CPU for this first version.")
            .defineInRange("gpuLayers", 0, 0, 1000);

    public static final ModConfigSpec.DoubleValue TEMPERATURE = BUILDER
            .comment("Sampling temperature for chat requests.")
            .defineInRange("temperature", 0.7D, 0.0D, 2.0D);

    public static final ModConfigSpec.IntValue MAX_TOKENS = BUILDER
            .comment("Maximum tokens generated per chat request.")
            .defineInRange("maxTokens", 256, 1, 32768);

    public static final ModConfigSpec.ConfigValue<String> EXTRA_LIBRARY_PATH = BUILDER
            .comment("Optional directory containing a newer libstdc++.so.6 for older Linux distributions",
                    "(for example a gcc-toolset runtime). Prepended to LD_LIBRARY_PATH for llama-server.",
                    "When empty, a compatible library is detected automatically if the bundled runtime needs one.")
            .define("extraLibraryPath", "");

    public static final ModConfigSpec.BooleanValue ENABLE_THINKING = BUILDER
            .comment("Allow the model to emit a reasoning block before its answer.",
                    "Disabled by default: reasoning can consume the whole maxTokens budget and leave the answer empty.",
                    "If enabled, increase maxTokens to leave room for the final answer.")
            .define("enableThinking", false);

    public static final ModConfigSpec.IntValue STARTUP_TIMEOUT_SECONDS = BUILDER
            .comment("How long to wait for llama-server to load the model and pass /health.")
            .defineInRange("startupTimeoutSeconds", 180, 30, 3600);

    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS = BUILDER
            .comment("Timeout for a single chat completion request.")
            .defineInRange("requestTimeoutSeconds", 120, 10, 3600);

    public static final ModConfigSpec.BooleanValue WEB_UI = BUILDER
            .comment("Expose llama-server's built-in web UI. Disabled by default.")
            .define("webUi", false);

    public static final ModConfigSpec.BooleanValue HOLOGRAM_ENABLED = BUILDER
            .comment("Spawn a player-model hologram while a player's Lantern Ring session is active.")
            .define("hologramEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> HOLOGRAM_SKIN_USERNAME = BUILDER
            .comment("Minecraft username whose skin the hologram uses.",
                    "Leave empty to use each ring owner's own username/skin.")
            .define("hologramSkinUsername", "");

    public static final ModConfigSpec.ConfigValue<String> HOLOGRAM_DISPLAY_NAME = BUILDER
            .comment("Name shown above the hologram. Leave empty to use the resolved skin username.")
            .define("hologramDisplayName", "");

    public static final ModConfigSpec.DoubleValue HOLOGRAM_FOLLOW_START_DISTANCE = BUILDER
            .comment("Distance at which the hologram starts following its owner.")
            .defineInRange("hologramFollowStartDistance", 4.0D, 1.0D, 64.0D);

    public static final ModConfigSpec.DoubleValue HOLOGRAM_FOLLOW_STOP_DISTANCE = BUILDER
            .comment("Distance at which the hologram stops following. Must be smaller than the start distance.")
            .defineInRange("hologramFollowStopDistance", 2.25D, 0.5D, 63.0D);

    public static final ModConfigSpec.DoubleValue HOLOGRAM_FOLLOW_SPEED = BUILDER
            .comment("Pathfinding speed multiplier used while following.")
            .defineInRange("hologramFollowSpeed", 1.0D, 0.1D, 3.0D);

    public static final ModConfigSpec.BooleanValue HOLOGRAM_NAMEPLATE = BUILDER
            .comment("Show the display name above the hologram.")
            .define("hologramNameplate", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
