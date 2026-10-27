package net.yizuz.playerogrammod.client.flight;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class RingFlightKeybinds {
    public static final KeyMapping HOVER = new KeyMapping(
            "key.playerogrammod.hover",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.playerogrammod");

    private RingFlightKeybinds() {
    }
}
