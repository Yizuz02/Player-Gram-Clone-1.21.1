package net.yizuz.playerogrammod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.yizuz.playerogrammod.PlayerOGramMod;
import net.yizuz.playerogrammod.ai.AiService;
import net.yizuz.playerogrammod.ai.AiStatus;
import net.yizuz.playerogrammod.text.MarkdownRenderer;

import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = PlayerOGramMod.MOD_ID)
public final class PlayerOGramCommands {
    private static final String PREFIX = "[Player-O-Gram AI] ";

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("playerogram")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ai")
                        .then(Commands.literal("status")
                                .executes(PlayerOGramCommands::status))
                        .then(Commands.literal("install")
                                .executes(PlayerOGramCommands::install))
                        .then(Commands.literal("start")
                                .executes(PlayerOGramCommands::start))
                        .then(Commands.literal("stop")
                                .executes(PlayerOGramCommands::stop))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                        .executes(PlayerOGramCommands::ask)))));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        AiStatus status = AiService.get().status();
        context.getSource().sendSuccess(() -> Component.literal(PREFIX + status.describe()), false);
        return 1;
    }

    private static int install(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(PREFIX
                + "Installing the llama.cpp runtime and default Gemma model (first run downloads about 3 GiB)…"), false);
        AiService.get().install(progressSink(source)).whenComplete((ignored, throwable) ->
                complete(source, throwable, "Runtime and model are installed."));
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(PREFIX
                + "Starting the AI (the first run may install the runtime and model)…"), false);
        AiService.get().start(progressSink(source)).whenComplete((ignored, throwable) ->
                complete(source, throwable, "The AI is ready."));
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(PREFIX + "Stopping the AI…"), false);
        AiService.get().stop().whenComplete((ignored, throwable) ->
                complete(source, throwable, "The AI is stopped."));
        return 1;
    }

    private static int ask(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String prompt = StringArgumentType.getString(context, "prompt");
        if (prompt.isBlank()) {
            source.sendFailure(Component.literal(PREFIX + "The prompt is empty."));
            return 0;
        }
        Component promptLine = Component.translatable("chat.type.text", source.getDisplayName(),
                Component.literal(prompt.replace('\u00a7', '&')));
        source.sendSystemMessage(promptLine);
        source.sendSuccess(() -> Component.literal(PREFIX + "Thinking…")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        AiService.get().ask(prompt, progressSink(source)).whenComplete((answer, throwable) -> {
            if (throwable != null) {
                complete(source, throwable, "");
                return;
            }
            List<Component> lines = MarkdownRenderer.render(answer);
            runOnServer(source, () -> {
                int limit = Math.min(lines.size(), 80);
                for (int i = 0; i < limit; i++) {
                    Component line = lines.get(i);
                    if (i == 0) {
                        MutableComponent prefixed = Component.empty()
                                .append(Component.literal(PREFIX).withStyle(ChatFormatting.GRAY))
                                .append(line);
                        source.sendSuccess(() -> prefixed, false);
                    } else {
                        MutableComponent continued = Component.empty()
                                .append(Component.literal("  "))
                                .append(line);
                        source.sendSuccess(() -> continued, false);
                    }
                }
                if (lines.size() > limit) {
                    source.sendSuccess(() -> Component.literal(PREFIX + "… (response truncated)"), false);
                }
            });
        });
        return 1;
    }

    private static void complete(CommandSourceStack source, Throwable throwable, String successMessage) {
        if (throwable != null) {
            String message = AiService.rootMessage(throwable);
            runOnServer(source, () -> source.sendFailure(Component.literal(PREFIX + message)));
            return;
        }
        runOnServer(source, () ->
                source.sendSuccess(() -> Component.literal(PREFIX + successMessage), false));
    }

    private static Consumer<String> progressSink(CommandSourceStack source) {
        return message -> runOnServer(source,
                () -> source.sendSuccess(() -> Component.literal(PREFIX + message), false));
    }

    private static void runOnServer(CommandSourceStack source, Runnable task) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException ignored) {
                // The command source may no longer be valid.
            }
        });
    }

    private PlayerOGramCommands() {
    }
}
