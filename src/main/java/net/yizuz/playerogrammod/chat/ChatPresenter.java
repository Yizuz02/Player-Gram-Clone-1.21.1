package net.yizuz.playerogrammod.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.yizuz.playerogrammod.text.MarkdownRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatPresenter {
    public static final String AI_PREFIX = "[Player-O-Gram] ";
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatPresenter.class);
    private static final int MAX_LINES = 80;

    public static List<Component> format(String markdown) {
        List<Component> rendered = MarkdownRenderer.render(markdown);
        List<Component> lines = new ArrayList<>();
        int limit = Math.min(rendered.size(), MAX_LINES);
        for (int i = 0; i < limit; i++) {
            if (i == 0) {
                MutableComponent first = Component.empty()
                        .append(Component.literal(AI_PREFIX).withStyle(ChatFormatting.GOLD))
                        .append(rendered.get(i));
                lines.add(first);
            } else {
                MutableComponent continuation = Component.empty()
                        .append(Component.literal("  "))
                        .append(rendered.get(i));
                lines.add(continuation);
            }
        }
        if (rendered.size() > limit) {
            lines.add(Component.empty()
                    .append(Component.literal(AI_PREFIX).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("… (response truncated)").withStyle(ChatFormatting.GRAY)));
        }
        return lines;
    }

    public static void sendDialogue(MinecraftServer server, UUID playerId, String markdown) {
        if (server == null || playerId == null || markdown == null || markdown.isBlank()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        for (Component line : format(markdown)) {
            player.sendSystemMessage(line);
        }
        LOGGER.info("[Player-O-Gram -> {}] {}", player.getGameProfile().getName(), markdown);
    }

    public static void sendPlayerMessage(MinecraftServer server, ServerPlayer player, String text) {
        if (server == null || player == null || text == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("chat.type.text", player.getDisplayName(),
                Component.literal(text.replace('\u00a7', '&'))));
        LOGGER.debug("AI prompt echo for {}: {}", player.getUUID(), text);
    }

    public static void sendStatus(MinecraftServer server, UUID playerId, String text) {
        if (server == null || playerId == null || text == null || text.isBlank()) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        MutableComponent line = Component.empty()
                .append(Component.literal(AI_PREFIX).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(line);
        LOGGER.debug("AI status to {}: {}", playerId, text);
    }

    private ChatPresenter() {
    }
}
