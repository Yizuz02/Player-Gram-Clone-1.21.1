package net.yizuz.playerogrammod.client.flight;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.yizuz.playerogrammod.PlayerOGramMod;

import java.util.Optional;

/**
 * Player Animator integration: a dedicated high-priority layer per player that
 * switches between the hover pose and the moving flight pose.
 */
public final class RingFlightAnimations {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "ring_flight");
    private static final ResourceLocation IDLE =
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "flight_idle");
    private static final ResourceLocation BOOST =
            ResourceLocation.fromNamespaceAndPath(PlayerOGramMod.MOD_ID, "flight_boost");
    private static final int LAYER_PRIORITY = 5000;
    private static final int FADE_TICKS = 6;

    public static void register() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            ModifierLayer<KeyframeAnimationPlayer> layer = new ModifierLayer<>();
            AdjustmentModifier headAdjustment = new AdjustmentModifier(part -> {
                if (!"head".equals(part)) {
                    return Optional.empty();
                }
                return Optional.of(new AdjustmentModifier.PartModifier(
                        new Vec3f(RingFlightPose.headPitchRadians(player), 0.0F, 0.0F),
                        Vec3f.ZERO));
            });
            layer.addModifierBefore(headAdjustment);
            stack.addAnimLayer(LAYER_PRIORITY, layer);
            PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
        });
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        for (AbstractClientPlayer player : level.players()) {
            ModifierLayer<KeyframeAnimationPlayer> layer = layerFor(player);
            if (layer == null) {
                continue;
            }

            if (!RingFlightClientState.isActive(player.getUUID())) {
                if (layer.getAnimation() != null) {
                    layer.replaceAnimationWithFade(fade(), null);
                }
                continue;
            }

            ResourceLocation desired = chooseAnimation(player);
            IPlayable playable = PlayerAnimationRegistry.getAnimation(desired);
            if (!(playable instanceof KeyframeAnimation animation)) {
                continue;
            }

            KeyframeAnimationPlayer current = layer.getAnimation();
            if (current == null || current.getData() != animation) {
                layer.replaceAnimationWithFade(fade(), new KeyframeAnimationPlayer(animation), true);
            }
        }
    }

    private static ResourceLocation chooseAnimation(AbstractClientPlayer player) {
        return RingFlightPose.isMoving(player) ? BOOST : IDLE;
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<KeyframeAnimationPlayer> layerFor(AbstractClientPlayer player) {
        IAnimation stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        return stored instanceof ModifierLayer<?> ? (ModifierLayer<KeyframeAnimationPlayer>) stored : null;
    }

    private static AbstractFadeModifier fade() {
        return AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE);
    }

    private RingFlightAnimations() {
    }
}
