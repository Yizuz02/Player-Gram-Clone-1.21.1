package net.yizuz.playerogrammod.network;

import net.minecraft.server.level.ServerPlayer;
import net.yizuz.playerogrammod.chat.ChatConversationService;
import net.yizuz.playerogrammod.personality.PersonalityProfile;
import net.yizuz.playerogrammod.personality.PersonalityTest;
import net.yizuz.playerogrammod.ring.RingConfig;
import net.yizuz.playerogrammod.ring.RingConfigs;
import net.yizuz.playerogrammod.ring.RingValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-authoritative ring configuration writes. The packet's ring ID is only
 * used to locate the ring; ownership is established by finding the matching stack
 * in the player's actual inventory.
 */
public final class RingConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RingConfigService.class);

    public record Result(boolean success, String message) {
    }

    public static Result save(ServerPlayer player, RingConfigSavePayload payload) {
        if (player == null || payload == null || payload.ringId() == null) {
            return new Result(false, "The ring selection is invalid.");
        }

        RingConfigs.Match match = RingConfigs.findUnique(player, payload.ringId());
        if (match == null) {
            return new Result(false,
                    "This ring is no longer in your inventory, or its identity is ambiguous.");
        }

        PersonalityProfile personality = PersonalityProfile.fromJson(payload.personalityJson());
        String personalityError = PersonalityTest.validate(personality);
        if (personalityError != null) {
            return new Result(false, personalityError);
        }

        RingConfig current = match.config();
        String prompt = payload.systemPrompt();
        boolean customPrompt = payload.customPrompt();
        if (personality.completed()) {
            RingConfig named = current.withSettings(
                    payload.displayName(), payload.skinUsername(), true, "");
            prompt = personality.buildSystemPrompt(
                    named.effectiveDisplayName(player.getGameProfile().getName()));
            customPrompt = true;
        }

        String error = RingValidation.validateSettings(
                payload.displayName(), payload.skinUsername(), prompt);
        if (error != null) {
            return new Result(false, error);
        }

        RingConfig updated = current.withSettings(
                payload.displayName(), payload.skinUsername(), customPrompt, prompt)
                .withPersonality(personality);
        RingConfigs.set(match.stack(), updated);
        ChatConversationService.get().onRingConfigSaved(player, updated);
        LOGGER.info("Ring {} configuration saved for {} (personality={}).",
                updated.ringId(), player.getUUID(), personality.completed());
        return new Result(true, "Settings saved.");
    }

    private RingConfigService() {
    }
}
