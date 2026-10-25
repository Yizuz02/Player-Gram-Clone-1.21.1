# Player-O-Gram-Clone

**Upload a version of yourself into Minecraft.**

Player-O-Gram-Clone is an experimental Minecraft mod for NeoForge 1.21.1 that explores the idea of creating a digital clone of a player and bringing it into the Minecraft world as an AI-powered NPC.

The goal is to create more than a generic chatbot. Your clone should reflect your personality, communication style, knowledge, opinions, and memories. It should be a digital representation of you that other players can meet, talk to, and get to know inside Minecraft.

The project is in early development. Its architecture and feature set are still evolving.

## The Idea

What if you could leave a version of yourself behind in a Minecraft world?

Imagine creating an NPC that talks like you, remembers information about you, and responds to other players based on your personality and experiences. Instead of relying on a fixed set of dialogue options, the NPC uses a language model to generate responses dynamically.

Each clone could be customized in different ways, from a carefully written system prompt to a fine-tuned model trained to better reflect its creator.

The long-term vision is to make digital clones that feel personal rather than interchangeable.

## Planned Features

The following describes the project's intended direction, not a list of features that are already available.

- **Personal AI clones:** Create NPCs that reflect a player's personality, communication style, knowledge, opinions, and memories.
- **Local LLM inference:** Run GGUF language models through a local inference runtime managed by the Minecraft server.
- **Custom personalities:** Define a clone's behavior and identity through a configurable system prompt.
- **Personal memories:** Provide Markdown files containing information, memories, preferences, and other context about the person being represented.
- **Fine-tuned models:** Allow users to replace the default model with a compatible fine-tuned GGUF model for a more personalized experience.
- **Dynamic dialogue:** Generate responses instead of relying exclusively on predefined dialogue trees.
- **Shared multiplayer NPCs:** Let multiple players interact with the same server-owned clone and see its responses.
- **Persistent memory:** Explore ways for clones to retain relevant information from conversations and experiences in the Minecraft world.

Voice interaction and more advanced world interactions may be explored in future development.

## How Personalization Is Intended to Work

The project is designed to support different levels of customization, depending on the user's technical knowledge and available hardware.

### 1. System prompt customization

The simplest approach is to define the clone's identity through a system prompt. This can describe its personality, tone, background, opinions, preferences, and behavioral rules.

This approach does not require training a new model. It uses the capabilities of an existing language model and the context provided to it.

### 2. Markdown-based memories

Users can provide Markdown files containing personal information that the model should be able to reference during conversations.

For example:

```markdown
# About Me

## Personality
I enjoy dry humor and prefer direct answers.

## Interests
- Programming
- Minecraft
- Artificial intelligence

## Preferences
- Explain technical concepts with practical examples.
- Be skeptical of claims without evidence.

## Memories
I started playing Minecraft with my friends years ago.
```

The intended architecture may retrieve relevant information from these files and include it in the model's context, following an approach similar to retrieval-augmented generation (RAG).

The exact retrieval, indexing, and memory-management implementation is still under development.

### 3. Fine-tuned models

For users who want deeper personalization, the project aims to support compatible fine-tuned language models.

A model fine-tuned on carefully prepared personal data could learn aspects of a person's communication patterns and response preferences that are difficult to reproduce through prompting alone.

Fine-tuning is optional. Users should be able to customize a clone without having to train their own model.

## Technical Direction

| Component                 | Intended approach              |
| ------------------------- | ------------------------------ |
| Game                      | Minecraft Java Edition         |
| Mod loader                | NeoForge                       |
| Target version            | 1.21.1                         |
| Language model format     | GGUF                           |
| Inference                 | Local, server-side LLM runtime |
| Personality configuration | System prompt                  |
| Personal knowledge        | Markdown-based memory files    |
| Advanced personalization  | Compatible fine-tuned models   |
| Multiplayer               | Server-owned NPC interactions  |

These technologies describe the intended architecture. Specific implementations, supported models, and configuration options may change during development.

## Local AI and Privacy

Local inference is a central design goal.

Rather than requiring a hosted AI API for every conversation, the intended architecture runs the language model on the machine hosting the Minecraft server. This can reduce reliance on external services and give server operators greater control over their models and personal data.

Actual privacy depends on the runtime, model, configuration, and any additional integrations. Local inference alone does not guarantee that all data remains private.

Running a language model also requires sufficient memory and processing power. Performance will depend on the selected model, its quantization, context length, hardware, and the number of concurrent interactions.

## Development Status

**Early development — not yet a stable release.**

The project is under active development, and its features and architecture may change.

At this stage, the README documents the project's goals and intended design. It does not imply that model loading, personalized memory retrieval, fine-tuning support, or persistent NPC conversations are already implemented.

Installation instructions, supported-model lists, configuration examples, and troubleshooting documentation will be added as the corresponding functionality becomes available.

## For Developers

Contributions and technical feedback are welcome as the project evolves.

Areas of potential interest include:

- NeoForge NPC and interaction systems.
- GGUF inference runtime integration.
- Prompt engineering and personality configuration.
- Markdown-based knowledge retrieval and memory systems.
- Multiplayer synchronization and conversation management.
- Model evaluation and personalization techniques.

The intended architecture separates Minecraft NPC behavior from the inference backend so the model runtime can evolve without requiring a complete redesign of the game integration.

## Roadmap

The following is a tentative roadmap, not a commitment to specific release dates.

- [ ] Establish server-side LLM lifecycle management.
- [ ] Load and run compatible GGUF models locally.
- [ ] Connect NPC interactions to asynchronous model generation.
- [ ] Synchronize generated dialogue with multiplayer clients.
- [ ] Add configurable system prompts.
- [ ] Implement Markdown-based personal memory retrieval.
- [ ] Support selecting compatible fine-tuned models.
- [ ] Explore persistent memories from conversations and world events.
- [ ] Investigate optional voice interaction.

## The Vision

Player-O-Gram-Clone explores a simple question:

**If you could upload a version of yourself into Minecraft, what would it say, remember, and do?**

The goal is to bring personal AI into the Minecraft world—not just as a tool that answers questions, but as a digital presence shaped by the person behind it.

---

*Player-O-Gram-Clone is an experimental community project. It is not intended to create a perfect or objectively accurate copy of a person. A model's responses depend on its training, configuration, and available context.*
