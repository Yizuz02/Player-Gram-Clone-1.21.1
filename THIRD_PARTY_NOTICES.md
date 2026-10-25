# Third-party notices

This mod downloads third-party components at runtime. It does not redistribute them
inside the mod JAR.

## llama.cpp

- Project: https://github.com/ggml-org/llama.cpp
- License: MIT
- Downloaded: official CPU release binaries (`b11669`) into the game instance folder.
- Downloaded file names and SHA-256 digests are pinned in `LlamaManifest.java`.

## Gemma 4 E2B IT GGUF

- Repository: https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF
- Base model: https://huggingface.co/google/gemma-4-E2B-it
- License: see the model card and the Gemma 4 license linked from it.
- Downloaded: the selected quant (default `Q4_K_M`) into `<gameDir>/playerogram/models/`.
- File names, sizes, and SHA-256 digests are pinned in `ModelManifest.java`.

## Minecraft / NeoForge

Minecraft and NeoForge are not distributed by this mod. See their respective licenses.
