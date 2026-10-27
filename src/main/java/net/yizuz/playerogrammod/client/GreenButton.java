package net.yizuz.playerogrammod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * Dark translucent green button with a brighter holographic hover/selected state.
 */
final class GreenButton extends Button {
    private final boolean tab;
    private boolean selected;
    private boolean wasHovered;

    GreenButton(int x, int y, int width, int height, Component message,
                OnPress onPress, boolean tab) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.tab = tab;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        boolean hovered = this.isHovered();
        if (hovered && !this.wasHovered) {
            RingUiSounds.playHover();
        }
        this.wasHovered = hovered;

        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        boolean lit = this.active && (hovered || this.selected);

        int fill = !this.active ? 0x55101812
                : this.selected ? 0xD00A3A24
                : hovered ? 0xC0124A2E
                : 0x66112618;
        int border = !this.active ? 0x333CFF88
                : this.selected ? 0xFFC4FFE0
                : hovered ? 0xFF8CFFC0
                : 0x883CFF88;
        int text = !this.active ? 0xFF5E7E6A
                : this.selected ? 0xFFD8FFE8
                : hovered ? 0xFFE9FFF2
                : 0xFFB8FFD9;

        if (lit) {
            guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2,
                    this.selected ? 0x553CFF88 : 0x333CFF88);
        }
        guiGraphics.fill(x, y, x + width, y + height, fill);
        guiGraphics.fill(x, y, x + width, y + 1, border);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, border);
        guiGraphics.fill(x, y, x + 1, y + height, border);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, border);
        if (this.tab && this.selected) {
            guiGraphics.fill(x + 4, y, x + width - 4, y + 2, 0xFFC4FFE0);
        }
        var font = Minecraft.getInstance().font;
        String fit = font.plainSubstrByWidth(this.getMessage().getString(), Math.max(1, width - 10));
        guiGraphics.drawString(font, fit,
                x + (width - font.width(fit)) / 2, y + (height - 8) / 2, text, false);
    }

    @Override
    public void playDownSound(SoundManager handler) {
        RingUiSounds.playPress();
    }
}
