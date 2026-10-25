package net.yizuz.playerogrammod.client;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.yizuz.playerogrammod.entity.HologramEntity;
import net.yizuz.playerogrammod.entity.ModEntities;
import net.yizuz.playerogrammod.hologram.HologramVisuals;
import net.yizuz.playerogrammod.network.RingConfigClientBridge;
import net.yizuz.playerogrammod.network.RingConfigSavePayload;
import net.yizuz.playerogrammod.network.RingConfigSaveResultPayload;
import net.yizuz.playerogrammod.network.RingSkinRequestPayload;
import net.yizuz.playerogrammod.network.RingSkinResponsePayload;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import net.yizuz.playerogrammod.ring.RingConfig;
import net.yizuz.playerogrammod.ring.RingConfigs;
import net.yizuz.playerogrammod.ring.RingValidation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Client-side editor for one specific Lantern Ring. The preview is a client-only
 * HologramEntity (never added to the level) rendered through the same hologram
 * renderer/model as the world entity.
 */
public final class RingConfigScreen extends Screen {
    private static final int PANEL_COLOR = 0xE0101010;
    private static final int PANEL_EDGE = 0xFF3CFF88;
    private static final int PREVIEW_BACK = 0x66000000;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int OK = 0xFF3CFF88;
    private static final int ERROR = 0xFFFF6666;
    private static final long RESET_CONFIRM_MS = 5000L;
    private static final long SKIN_DEBOUNCE_MS = 300L;

    private final Screen previous;
    private final UUID ringId;
    private final String ownerName;
    private final String defaultPrompt;

    private EditBox displayNameField;
    private EditBox skinUsernameField;
    private MultiLineEditBox promptField;
    private Button resetButton;

    private String currentDisplayName;
    private String currentSkinUsername;
    private String currentPromptText;
    private boolean customPrompt;
    private boolean suppressPromptListener;
    private boolean invalidSelection;
    private boolean saving;
    private boolean confirmReset;
    private long resetConfirmAt;
    private String status = "";
    private int statusColor = MUTED;

    private HologramEntity preview;
    private HologramRenderer previewRenderer;
    private UUID skinRequestId;
    private String requestedSkin;
    private long lastSkinRequestAt;
    private boolean skinLoading;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int previewLeft;
    private int previewTop;
    private int previewWidth;
    private int previewHeight;

    public RingConfigScreen(@Nullable Screen previous, RingConfig config, String ownerName) {
        super(Component.translatable("screen.playerogrammod.ring_config.title"));
        this.previous = previous;
        this.ringId = config.ringId();
        this.ownerName = ownerName == null ? "" : ownerName;
        this.defaultPrompt = SystemPromptProvider.DEFAULT_PROMPT;
        this.customPrompt = config.customPrompt();
        this.currentDisplayName = config.displayName();
        this.currentSkinUsername = config.skinUsername();
        this.currentPromptText = config.customPrompt() ? config.systemPrompt() : this.defaultPrompt;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 16, 440);
        this.panelHeight = Math.min(this.height - 16, 280);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        this.previewLeft = this.panelLeft + 12;
        this.previewTop = this.panelTop + 30;
        this.previewWidth = 140;
        this.previewHeight = 160;

        int fieldX = this.panelLeft + 170;
        int fieldWidth = this.panelWidth - 182;
        this.displayNameField = new EditBox(this.font, fieldX, this.panelTop + 43, fieldWidth, 18,
                Component.translatable("screen.playerogrammod.ring_config.display_name"));
        this.displayNameField.setMaxLength(RingValidation.MAX_DISPLAY_NAME);
        this.displayNameField.setValue(this.currentDisplayName);
        this.displayNameField.setResponder(this::onDisplayNameChanged);
        this.addRenderableWidget(this.displayNameField);

        this.skinUsernameField = new EditBox(this.font, fieldX, this.panelTop + 68, fieldWidth, 18,
                Component.translatable("screen.playerogrammod.ring_config.skin_username"));
        this.skinUsernameField.setMaxLength(RingValidation.MAX_SKIN_USERNAME);
        this.skinUsernameField.setFilter(value -> value.matches("[A-Za-z0-9_]*"));
        this.skinUsernameField.setValue(this.currentSkinUsername);
        this.skinUsernameField.setResponder(this::onSkinUsernameChanged);
        this.addRenderableWidget(this.skinUsernameField);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.playerogrammod.ring_config.use_default_prompt"),
                        ignored -> useDefaultPrompt())
                .bounds(fieldX, this.panelTop + 91, 108, 18).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.playerogrammod.ring_config.clear_prompt"),
                        ignored -> clearPrompt())
                .bounds(fieldX + 114, this.panelTop + 91, 76, 18).build());

        this.promptField = new MultiLineEditBox(this.font, fieldX, this.panelTop + 126,
                fieldWidth, 100,
                Component.translatable("screen.playerogrammod.ring_config.prompt_hint"),
                Component.translatable("screen.playerogrammod.ring_config.prompt"));
        this.promptField.setCharacterLimit(RingValidation.MAX_PROMPT);
        this.promptField.setValue(this.currentPromptText);
        this.promptField.setValueListener(this::onPromptChanged);
        this.addRenderableWidget(this.promptField);

        int buttonY = this.panelTop + this.panelHeight - 26;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.playerogrammod.ring_config.save"), ignored -> save())
                .bounds(this.panelLeft + 12, buttonY, 82, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.playerogrammod.ring_config.cancel"), ignored -> onClose())
                .bounds(this.panelLeft + 100, buttonY, 82, 20).build());
        this.resetButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.playerogrammod.ring_config.reset"), ignored -> reset())
                .bounds(this.panelLeft + 188, buttonY, 120, 20).build());

        this.setInitialFocus(this.displayNameField);

        Minecraft minecraft = Minecraft.getInstance();
        if (this.preview == null && minecraft.level != null) {
            this.preview = new HologramEntity(ModEntities.HOLOGRAM.get(), minecraft.level);
            this.preview.setCustomNameVisible(false);
            this.preview.tickCount = HologramVisuals.MATERIALIZE_TICKS;
            this.preview.setSkinUsername(desiredPreviewSkin());
            this.preview.setSkinTexture("");
            this.preview.setSkinTextureSignature("");
            if (minecraft.getEntityRenderDispatcher().getRenderer(this.preview)
                    instanceof HologramRenderer renderer) {
                this.previewRenderer = renderer;
            }
        }

        RingConfigClientBridge.setHandlers(this::handleSaveResult, this::handleSkinResponse);
        this.requestSkin(desiredPreviewSkin());
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || ringId == null) {
            this.invalidSelection = true;
            return;
        }
        RingConfigs.Match match = RingConfigs.findUnique(player, this.ringId);
        boolean invalid = match == null;
        if (invalid != this.invalidSelection) {
            this.invalidSelection = invalid;
            if (invalid) {
                setStatus("This ring is no longer in your inventory.", ERROR);
            }
        }

        String desired = desiredPreviewSkin();
        long now = Util.getMillis();
        if (!desired.equals(this.requestedSkin) && now - this.lastSkinRequestAt >= SKIN_DEBOUNCE_MS) {
            requestSkin(desired);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderPreview(guiGraphics, partialTick);
        renderLabels(guiGraphics);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth,
                this.panelTop + this.panelHeight, PANEL_COLOR);
        guiGraphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth,
                this.panelTop + 1, PANEL_EDGE);
        guiGraphics.fill(this.previewLeft, this.previewTop, this.previewLeft + this.previewWidth,
                this.previewTop + this.previewHeight, PREVIEW_BACK);
    }

    private void renderPreview(GuiGraphics guiGraphics, float partialTick) {
        if (this.preview == null || this.previewRenderer == null) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.preview_unavailable"),
                    this.previewLeft + 8, this.previewTop + 8, MUTED, false);
            return;
        }

        float rotation = (float) ((Util.getMillis() % 12000L) / 12000.0 * 360.0);
        this.preview.setYRot(rotation);
        this.preview.setYBodyRot(rotation);
        this.preview.setYHeadRot(rotation);
        this.preview.setXRot(0.0f);
        this.preview.setSkinUsername(desiredPreviewSkin());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.previewLeft + this.previewWidth / 2.0f,
                this.previewTop + this.previewHeight - 12.0f, 50.0f);
        guiGraphics.pose().scale(52.0f, 52.0f, 52.0f);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0f));
        this.previewRenderer.render(this.preview, rotation, partialTick,
                guiGraphics.pose(), guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);
        guiGraphics.pose().popPose();
        guiGraphics.flush();
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, this.title,
                this.panelLeft + 12, this.panelTop + 10, TEXT, false);
        String id = this.ringId == null ? "?" : this.ringId.toString().substring(0, 8);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.playerogrammod.ring_config.ring_id", id),
                this.panelLeft + 12, this.panelTop + 20, MUTED, false);

        guiGraphics.drawString(this.font,
                Component.translatable("screen.playerogrammod.ring_config.preview"),
                this.previewLeft + 2, this.previewTop + 2, MUTED, false);
        String previewName = previewDisplayName();
        guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(previewName, this.previewWidth),
                this.previewLeft, this.previewTop + this.previewHeight + 4, TEXT, false);
        if (this.skinLoading) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.loading_skin"),
                    this.previewLeft, this.previewTop + this.previewHeight + 16, MUTED, false);
        }

        int fieldX = this.panelLeft + 170;
        guiGraphics.drawString(this.font,
                Component.translatable("screen.playerogrammod.ring_config.display_name"),
                fieldX, this.panelTop + 33, MUTED, false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.playerogrammod.ring_config.skin_username"),
                fieldX, this.panelTop + 58, MUTED, false);
        guiGraphics.drawString(this.font,
                Component.translatable(this.customPrompt
                                ? "screen.playerogrammod.ring_config.prompt_custom"
                                : "screen.playerogrammod.ring_config.prompt_default"),
                fieldX, this.panelTop + 116, MUTED, false);

        guiGraphics.drawString(this.font, Component.literal(this.status),
                this.panelLeft + 12, this.panelTop + this.panelHeight - 38, this.statusColor, false);
    }

    private void onDisplayNameChanged(String value) {
        this.currentDisplayName = value == null ? "" : value;
    }

    private void onSkinUsernameChanged(String value) {
        this.currentSkinUsername = value == null ? "" : value;
        if (this.preview != null) {
            this.preview.setSkinUsername(value == null || value.isBlank() ? this.ownerName : value);
        }
    }

    private void onPromptChanged(String value) {
        this.currentPromptText = value == null ? "" : value;
        if (!this.suppressPromptListener) {
            this.customPrompt = true;
        }
    }

    private void useDefaultPrompt() {
        this.suppressPromptListener = true;
        this.customPrompt = false;
        this.currentPromptText = this.defaultPrompt;
        this.promptField.setValue(this.defaultPrompt);
        this.suppressPromptListener = false;
        setStatus("Using the default system prompt.", MUTED);
    }

    private void clearPrompt() {
        this.suppressPromptListener = true;
        this.customPrompt = true;
        this.currentPromptText = "";
        this.promptField.setValue("");
        this.suppressPromptListener = false;
        setStatus("Custom prompt cleared. This is different from using the default prompt.", MUTED);
    }

    private void reset() {
        long now = Util.getMillis();
        if (!this.confirmReset || now - this.resetConfirmAt > RESET_CONFIRM_MS) {
            this.confirmReset = true;
            this.resetConfirmAt = now;
            this.resetButton.setMessage(Component.translatable(
                    "screen.playerogrammod.ring_config.reset_confirm"));
            setStatus("Click Reset again to confirm. Unsaved custom settings will be discarded.", ERROR);
            return;
        }

        this.confirmReset = false;
        this.resetButton.setMessage(Component.translatable("screen.playerogrammod.ring_config.reset"));
        this.currentDisplayName = "";
        this.currentSkinUsername = "";
        this.currentPromptText = this.defaultPrompt;
        this.displayNameField.setValue("");
        this.skinUsernameField.setValue("");
        this.suppressPromptListener = true;
        this.customPrompt = false;
        this.promptField.setValue(this.defaultPrompt);
        this.suppressPromptListener = false;
        if (this.preview != null) {
            this.preview.setSkinUsername(desiredPreviewSkin());
        }
        requestSkin(desiredPreviewSkin());
        setStatus("Defaults restored. Save to apply them to this ring.", OK);
    }

    private void save() {
        if (this.saving) {
            return;
        }
        if (this.invalidSelection) {
            setStatus("This ring is no longer in your inventory.", ERROR);
            return;
        }
        String display = this.displayNameField.getValue();
        String skin = this.skinUsernameField.getValue();
        String prompt = this.promptField.getValue();
        String error = RingValidation.validateSettings(display, skin, prompt);
        if (error != null) {
            setStatus(error, ERROR);
            return;
        }
        this.saving = true;
        setStatus("Saving…", MUTED);
        PacketDistributor.sendToServer(new RingConfigSavePayload(
                this.ringId, display, skin, prompt, this.customPrompt));
    }

    private void handleSaveResult(RingConfigSaveResultPayload payload) {
        if (payload.ringId() == null || !payload.ringId().equals(this.ringId)) {
            return;
        }
        this.saving = false;
        setStatus(payload.message(), payload.success() ? OK : ERROR);
    }

    private void handleSkinResponse(RingSkinResponsePayload payload) {
        if (payload.requestId() == null || !payload.requestId().equals(this.skinRequestId)) {
            return;
        }
        this.skinLoading = false;
        if (this.preview != null) {
            String username = payload.username() == null || payload.username().isBlank()
                    ? this.requestedSkin : payload.username();
            this.preview.setSkinUsername(username == null ? "" : username);
            this.preview.setSkinTexture(payload.textureValue());
            this.preview.setSkinTextureSignature(payload.textureSignature());
        }
    }

    private void requestSkin(String username) {
        String desired = username == null ? "" : username.trim();
        if (desired.isEmpty()) {
            desired = this.ownerName;
        }
        if (Objects.equals(desired, this.requestedSkin) && !this.skinLoading) {
            return;
        }
        this.requestedSkin = desired;
        this.skinRequestId = UUID.randomUUID();
        this.lastSkinRequestAt = Util.getMillis();
        this.skinLoading = true;
        if (this.preview != null) {
            this.preview.setSkinUsername(desired);
            this.preview.setSkinTexture("");
            this.preview.setSkinTextureSignature("");
        }
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(
                    new RingSkinRequestPayload(this.skinRequestId, desired));
        } else {
            this.skinLoading = false;
        }
    }

    private String desiredPreviewSkin() {
        String value = this.skinUsernameField == null ? "" : this.skinUsernameField.getValue().trim();
        return value.isEmpty() ? this.ownerName : value;
    }

    private String previewDisplayName() {
        String value = this.displayNameField == null ? "" : this.displayNameField.getValue().trim();
        return value.isEmpty() ? desiredPreviewSkin() : value;
    }

    private void setStatus(String text, int color) {
        this.status = text == null ? "" : text;
        this.statusColor = color;
    }

    @Override
    public void onClose() {
        if (this.previous != null) {
            Minecraft.getInstance().setScreen(this.previous);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        super.removed();
        RingConfigClientBridge.clearHandlers();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
