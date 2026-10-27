package net.yizuz.playerogrammod.client;

import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
import net.yizuz.playerogrammod.personality.PersonalityProfile;
import net.yizuz.playerogrammod.personality.PersonalityTest;
import net.yizuz.playerogrammod.prompt.SystemPromptProvider;
import net.yizuz.playerogrammod.ring.RingConfig;
import net.yizuz.playerogrammod.ring.RingConfigs;
import net.yizuz.playerogrammod.ring.RingValidation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Tabbed Green Lantern style editor for one Lantern Ring. The preview is a
 * client-only HologramEntity rendered through the existing hologram renderer.
 */
public final class RingConfigScreen extends Screen {
    private static final int PANEL = 0xEE07110B;
    private static final int PANEL_HEADER_TOP = 0xF00D2418;
    private static final int PANEL_HEADER_BOTTOM = 0xE807120C;
    private static final int EDGE = 0xFF3CFF88;
    private static final int EDGE_DIM = 0x663CFF88;
    private static final int TEXT = 0xFFE9FFF2;
    private static final int MUTED = 0xFF8FB9A4;
    private static final int ACCENT = 0xFF3CFF88;
    private static final int OK = 0xFF3CFF88;
    private static final int ERROR = 0xFFFF7070;
    private static final int FIELD_BACK = 0xDD07140C;
    private static final int PREVIEW_BACK = 0xCC07140C;
    private static final long RESET_CONFIRM_MS = 5000L;
    private static final long SKIN_DEBOUNCE_MS = 300L;

    private enum Tab {
        APPEARANCE,
        BEHAVIOR,
        PERSONALITY
    }

    private final Screen previous;
    private final UUID ringId;
    private final String ownerName;
    private final String defaultPrompt;

    private EditBox displayNameField;
    private EditBox skinUsernameField;
    private GreenButton appearanceTab;
    private GreenButton behaviorTab;
    private GreenButton personalityTab;
    private GreenButton personalityTestButton;
    private GreenButton saveButton;
    private GreenButton cancelButton;
    private GreenButton resetButton;

    private Tab selectedTab = Tab.APPEARANCE;
    private String currentDisplayName;
    private String currentSkinUsername;
    private String currentPromptText;
    private PersonalityProfile currentPersonality;
    private boolean customPrompt;
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
    private float previewYaw;
    private boolean previewDragging;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int contentTop;
    private int actionsY;
    private int statusY;
    private int previewLeft;
    private int previewTop;
    private int previewWidth;
    private int previewHeight;
    private int fieldX;
    private int fieldWidth;
    private int summaryX;
    private int summaryY;
    private int summaryWidth;
    private int summaryHeight;

    public RingConfigScreen(@Nullable Screen previous, RingConfig config, String ownerName) {
        super(Component.translatable("screen.playerogrammod.ring_config.title"));
        this.previous = previous;
        this.ringId = config.ringId();
        this.ownerName = ownerName == null ? "" : ownerName;
        this.defaultPrompt = SystemPromptProvider.DEFAULT_PROMPT;
        this.customPrompt = config.customPrompt();
        this.currentDisplayName = config.displayName();
        this.currentSkinUsername = config.skinUsername();
        this.currentPersonality = config.personality();
        this.currentPromptText = config.customPrompt() ? config.systemPrompt() : this.defaultPrompt;
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 16, 480);
        this.panelHeight = Math.min(this.height - 16, 300);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.contentTop = this.panelTop + 70;
        this.actionsY = this.panelTop + this.panelHeight - 28;
        this.statusY = this.actionsY - 15;

        int tabSpacing = 8;
        int tabWidth = Math.min(104, Math.max(66, (this.panelWidth - 44 - tabSpacing * 2) / 3));
        int tabsWidth = tabWidth * 3 + tabSpacing * 2;
        int tabX = this.panelLeft + (this.panelWidth - tabsWidth) / 2;
        int tabY = this.panelTop + 38;
        this.appearanceTab = this.createTab(Tab.APPEARANCE,
                Component.translatable("screen.playerogrammod.ring_config.tab_appearance"),
                tabX, tabY, tabWidth);
        this.behaviorTab = this.createTab(Tab.BEHAVIOR,
                Component.translatable("screen.playerogrammod.ring_config.tab_behavior"),
                tabX + tabWidth + tabSpacing, tabY, tabWidth);
        this.personalityTab = this.createTab(Tab.PERSONALITY,
                Component.translatable("screen.playerogrammod.ring_config.tab_personality"),
                tabX + (tabWidth + tabSpacing) * 2, tabY, tabWidth);

        this.previewLeft = this.panelLeft + 16;
        this.previewTop = this.contentTop + 4;
        this.previewWidth = Math.max(92, Math.min(150, this.panelWidth / 3));
        this.previewHeight = Math.max(70, Math.min(168,
                this.statusY - this.previewTop - 26));
        this.fieldX = this.previewLeft + this.previewWidth + 18;
        this.fieldWidth = Math.max(90, this.panelLeft + this.panelWidth - 16 - this.fieldX);

        this.displayNameField = new EditBox(this.font, this.fieldX, this.contentTop + 24,
                this.fieldWidth, 20,
                Component.translatable("screen.playerogrammod.ring_config.display_name"));
        this.displayNameField.setBordered(true);
        this.displayNameField.setTextColor(0xFFFFFFFF);
        this.displayNameField.setTextColorUneditable(0xFFFFFFFF);
        this.displayNameField.setTextShadow(false);
        this.displayNameField.setMaxLength(RingValidation.MAX_DISPLAY_NAME);
        this.displayNameField.setValue(this.currentDisplayName);
        this.displayNameField.setResponder(this::onDisplayNameChanged);
        this.addRenderableWidget(this.displayNameField);

        this.skinUsernameField = new EditBox(this.font, this.fieldX, this.contentTop + 66,
                this.fieldWidth, 20,
                Component.translatable("screen.playerogrammod.ring_config.skin_username"));
        this.skinUsernameField.setBordered(true);
        this.skinUsernameField.setTextColor(0xFFFFFFFF);
        this.skinUsernameField.setTextColorUneditable(0xFFFFFFFF);
        this.skinUsernameField.setTextShadow(false);
        this.skinUsernameField.setMaxLength(RingValidation.MAX_SKIN_USERNAME);
        this.skinUsernameField.setFilter(value -> value.matches("[A-Za-z0-9_]*"));
        this.skinUsernameField.setValue(this.currentSkinUsername);
        this.skinUsernameField.setResponder(this::onSkinUsernameChanged);
        this.addRenderableWidget(this.skinUsernameField);

        this.summaryX = this.panelLeft + 16;
        this.summaryY = this.contentTop + 8;
        this.summaryWidth = this.panelWidth - 32;
        int testY = this.actionsY - 46;
        this.summaryHeight = Math.max(40, testY - this.summaryY - 8);
        this.personalityTestButton = this.addRenderableWidget(new GreenButton(
                this.panelLeft + 16, testY, 176, 20,
                Component.translatable("screen.playerogrammod.ring_config.start_test"),
                ignored -> startPersonalityTest(), false));

        int gap = 8;
        int actionWidth = Math.max(70, (this.panelWidth - 32 - gap * 2) / 3);
        int actionX = this.panelLeft + 16;
        this.saveButton = this.addRenderableWidget(new GreenButton(
                actionX, this.actionsY, actionWidth, 20,
                Component.translatable("screen.playerogrammod.ring_config.save"),
                ignored -> save(), false));
        this.cancelButton = this.addRenderableWidget(new GreenButton(
                actionX + actionWidth + gap, this.actionsY, actionWidth, 20,
                Component.translatable("screen.playerogrammod.ring_config.cancel"),
                ignored -> onClose(), false));
        this.resetButton = this.addRenderableWidget(new GreenButton(
                actionX + (actionWidth + gap) * 2, this.actionsY, actionWidth, 20,
                Component.translatable("screen.playerogrammod.ring_config.reset"),
                ignored -> reset(), false));

        Minecraft minecraft = Minecraft.getInstance();
        this.previewDragging = false;
        if (this.preview == null && minecraft.level != null) {
            this.preview = new HologramEntity(ModEntities.HOLOGRAM.get(), minecraft.level);
            this.preview.setCustomNameVisible(false);
            this.preview.tickCount = HologramVisuals.MATERIALIZE_TICKS;
            this.preview.setYRot(0.0f);
            this.preview.setYBodyRot(0.0f);
            this.preview.setYHeadRot(0.0f);
            this.preview.setXRot(0.0f);
            this.preview.setSkinUsername(desiredPreviewSkin());
            this.preview.setSkinTexture("");
            this.preview.setSkinTextureSignature("");
            if (minecraft.getEntityRenderDispatcher().getRenderer(this.preview)
                    instanceof HologramRenderer renderer) {
                this.previewRenderer = renderer;
            }
        }

        RingConfigClientBridge.setHandlers(this::handleSaveResult, this::handleSkinResponse);
        this.selectTab(this.selectedTab, true);
        this.requestSkin(desiredPreviewSkin());
    }

    private GreenButton createTab(Tab tab, Component label, int x, int y, int width) {
        GreenButton button = new GreenButton(x, y, width, 22, label,
                ignored -> selectTab(tab, true), true);
        this.addRenderableWidget(button);
        return button;
    }

    private void selectTab(Tab tab, boolean focus) {
        this.selectedTab = tab;
        boolean appearance = tab == Tab.APPEARANCE;
        boolean personality = tab == Tab.PERSONALITY;

        this.displayNameField.visible = appearance;
        this.displayNameField.active = appearance;
        this.skinUsernameField.visible = appearance;
        this.skinUsernameField.active = appearance;
        this.personalityTestButton.visible = personality;
        this.personalityTestButton.active = personality && !this.invalidSelection;
        this.updatePersonalityButton();
        if (!appearance) {
            this.displayNameField.setFocused(false);
            this.skinUsernameField.setFocused(false);
        }
        this.appearanceTab.setSelected(appearance);
        this.behaviorTab.setSelected(tab == Tab.BEHAVIOR);
        this.personalityTab.setSelected(personality);
        this.previewDragging = false;

        if (focus) {
            if (appearance) {
                this.setInitialFocus(this.displayNameField);
            } else if (personality) {
                this.setInitialFocus(this.personalityTestButton);
            } else {
                this.setInitialFocus(this.behaviorTab);
            }
        }
    }

    private void updatePersonalityButton() {
        if (this.personalityTestButton != null) {
            this.personalityTestButton.setMessage(Component.translatable(
                    this.currentPersonality != null && this.currentPersonality.completed()
                            ? "screen.playerogrammod.ring_config.retake_test"
                            : "screen.playerogrammod.ring_config.start_test"));
        }
    }

    private void startPersonalityTest() {
        Minecraft.getInstance().setScreen(
                new PersonalityTestScreen(this, this.currentPersonality));
    }

    void applyPersonality(PersonalityProfile profile) {
        this.currentPersonality = profile == null ? PersonalityProfile.defaultProfile() : profile;
        this.currentPromptText = this.currentPersonality.buildSystemPrompt(
                effectiveDisplayNameForPrompt());
        this.customPrompt = true;
        this.updatePersonalityButton();
        setStatus("Personality profile updated. Saving…", MUTED);
        this.save();
    }

    private String effectiveDisplayNameForPrompt() {
        String display = this.displayNameField == null
                ? this.currentDisplayName : this.displayNameField.getValue();
        display = display == null ? "" : display.trim();
        if (!display.isEmpty()) {
            return display;
        }
        String skin = this.skinUsernameField == null
                ? this.currentSkinUsername : this.skinUsernameField.getValue();
        skin = skin == null ? "" : skin.trim();
        return skin.isEmpty() ? this.ownerName : skin;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.preview != null) {
            this.preview.tickCount++;
        }
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
            if (this.personalityTestButton != null) {
                this.personalityTestButton.active = this.selectedTab == Tab.PERSONALITY && !invalid;
            }
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
        if (this.selectedTab == Tab.APPEARANCE) {
            renderPreview(guiGraphics, partialTick);
        }
        renderDecorations(guiGraphics);
        renderLabels(guiGraphics);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int right = this.panelLeft + this.panelWidth;
        int bottom = this.panelTop + this.panelHeight;

        guiGraphics.fillGradient(this.panelLeft - 7, this.panelTop - 7,
                right + 7, bottom + 7, 0x333CFF88, 0x00000000);
        guiGraphics.fill(this.panelLeft, this.panelTop, right, bottom, PANEL);
        guiGraphics.fillGradient(this.panelLeft, this.panelTop,
                right, this.panelTop + 54, PANEL_HEADER_TOP, PANEL_HEADER_BOTTOM);
        guiGraphics.fill(this.panelLeft, this.panelTop, right, this.panelTop + 1, EDGE);
        guiGraphics.fill(this.panelLeft, this.panelTop + 1, right, this.panelTop + 2, 0x553CFF88);
        for (int y = this.panelTop + 2; y < bottom; y += 6) {
            guiGraphics.fill(this.panelLeft + 1, y, right - 1, y + 1, 0x083CFF88);
        }
        guiGraphics.fill(this.panelLeft + 16, this.contentTop - 7,
                right - 16, this.contentTop - 6, EDGE_DIM);

        if (this.selectedTab == Tab.APPEARANCE) {
            guiGraphics.fill(this.previewLeft, this.previewTop,
                    this.previewLeft + this.previewWidth, this.previewTop + this.previewHeight,
                    PREVIEW_BACK);
            guiGraphics.fillGradient(this.previewLeft, this.previewTop,
                    this.previewLeft + this.previewWidth, this.previewTop + this.previewHeight,
                    0x331B5B38, 0x00000000);
            drawFieldBack(guiGraphics, this.displayNameField.getX(), this.displayNameField.getY(),
                    this.displayNameField.getWidth(), this.displayNameField.getHeight());
            drawFieldBack(guiGraphics, this.skinUsernameField.getX(), this.skinUsernameField.getY(),
                    this.skinUsernameField.getWidth(), this.skinUsernameField.getHeight());
        }
        if (this.selectedTab == Tab.PERSONALITY) {
            guiGraphics.fill(this.summaryX, this.summaryY,
                    this.summaryX + this.summaryWidth, this.summaryY + this.summaryHeight,
                    FIELD_BACK);
        }
    }

    private void drawFieldBack(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, FIELD_BACK);
    }

    private void renderPreview(GuiGraphics guiGraphics, float partialTick) {
        if (this.preview == null || this.previewRenderer == null) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.preview_unavailable"),
                    this.previewLeft + 8, this.previewTop + 8, MUTED, false);
            return;
        }

        this.preview.setYRot(0.0f);
        this.preview.setYBodyRot(0.0f);
        this.preview.setYHeadRot(0.0f);
        this.preview.setXRot(0.0f);
        this.preview.setSkinUsername(desiredPreviewSkin());

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.previewLeft + this.previewWidth / 2.0f,
                this.previewTop + this.previewHeight - 12.0f, 50.0f);
        guiGraphics.pose().scale(52.0f, 52.0f, 52.0f);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(180.0f));
        guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(this.previewYaw));
        this.previewRenderer.render(this.preview, 0.0f, partialTick,
                guiGraphics.pose(), guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);
        guiGraphics.pose().popPose();
        guiGraphics.flush();
    }

    private void renderDecorations(GuiGraphics guiGraphics) {
        int right = this.panelLeft + this.panelWidth;
        int bottom = this.panelTop + this.panelHeight;
        drawFrame(guiGraphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, EDGE_DIM);
        drawCorners(guiGraphics, this.panelLeft, this.panelTop, right, bottom, EDGE);

        if (this.selectedTab == Tab.APPEARANCE) {
            drawFrame(guiGraphics, this.previewLeft - 1, this.previewTop - 1,
                    this.previewWidth + 2, this.previewHeight + 2, EDGE_DIM);
            drawCorners(guiGraphics, this.previewLeft - 4, this.previewTop - 4,
                    this.previewLeft + this.previewWidth + 4,
                    this.previewTop + this.previewHeight + 4, EDGE);
            drawFieldFrame(guiGraphics, this.displayNameField.getX(), this.displayNameField.getY(),
                    this.displayNameField.getWidth(), this.displayNameField.getHeight(),
                    this.displayNameField.isFocused());
            drawFieldFrame(guiGraphics, this.skinUsernameField.getX(), this.skinUsernameField.getY(),
                    this.skinUsernameField.getWidth(), this.skinUsernameField.getHeight(),
                    this.skinUsernameField.isFocused());
        }
        if (this.selectedTab == Tab.PERSONALITY) {
            drawFrame(guiGraphics, this.summaryX, this.summaryY,
                    this.summaryWidth, this.summaryHeight, EDGE_DIM);
        }
    }

    private void drawFrame(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void drawFieldFrame(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                boolean focused) {
        if (focused) {
            guiGraphics.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0x223CFF88);
        }
        drawFrame(guiGraphics, x - 1, y - 1, width + 2, height + 2,
                focused ? 0xFFC4FFE0 : EDGE_DIM);
    }

    private void drawCorners(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        int length = 10;
        guiGraphics.fill(x1, y1, x1 + length, y1 + 1, color);
        guiGraphics.fill(x1, y1, x1 + 1, y1 + length, color);
        guiGraphics.fill(x2 - length, y1, x2, y1 + 1, color);
        guiGraphics.fill(x2 - 1, y1, x2, y1 + length, color);
        guiGraphics.fill(x1, y2 - 1, x1 + length, y2, color);
        guiGraphics.fill(x1, y2 - length, x1 + 1, y2, color);
        guiGraphics.fill(x2 - length, y2 - 1, x2, y2, color);
        guiGraphics.fill(x2 - 1, y2 - length, x2, y2, color);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        String id = this.ringId == null ? "?" : this.ringId.toString().substring(0, 8);
        guiGraphics.drawString(this.font, this.title, this.panelLeft + 16, this.panelTop + 10,
                0x663CFF88, false);
        guiGraphics.drawString(this.font, this.title, this.panelLeft + 16, this.panelTop + 10,
                ACCENT, false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.playerogrammod.ring_config.ring_id", id),
                this.panelLeft + 16, this.panelTop + 23, MUTED, false);

        if (this.selectedTab == Tab.APPEARANCE) {
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.preview"),
                    this.previewLeft + 2, this.previewTop + 3, MUTED, false);
            String previewName = previewDisplayName();
            guiGraphics.drawString(this.font,
                    this.font.plainSubstrByWidth(previewName, this.previewWidth),
                    this.previewLeft, this.previewTop + this.previewHeight + 6, TEXT, false);
            if (this.skinLoading) {
                guiGraphics.drawString(this.font,
                        Component.translatable("screen.playerogrammod.ring_config.loading_skin"),
                        this.previewLeft + 2, this.previewTop + this.previewHeight - 11, MUTED, false);
            }
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.display_name"),
                    this.fieldX, this.contentTop + 10, MUTED, false);
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.skin_username"),
                    this.fieldX, this.contentTop + 52, MUTED, false);
            guiGraphics.drawString(this.font,
                    this.font.plainSubstrByWidth(
                            Component.translatable("screen.playerogrammod.ring_config.appearance_hint")
                                    .getString(), this.fieldWidth),
                    this.fieldX, this.contentTop + 92, MUTED, false);
        } else if (this.selectedTab == Tab.BEHAVIOR) {
            int center = this.contentTop + (this.actionsY - this.contentTop) / 2;
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.behavior_placeholder"),
                    this.panelLeft + this.panelWidth / 2, center - 8, TEXT);
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.behavior_reserved"),
                    this.panelLeft + this.panelWidth / 2, center + 8, MUTED);
        } else {
            PersonalityProfile profile = this.currentPersonality == null
                    ? PersonalityProfile.defaultProfile() : this.currentPersonality;
            guiGraphics.drawString(this.font,
                    Component.translatable("screen.playerogrammod.ring_config.profile_title"),
                    this.summaryX + 8, this.summaryY + 6, MUTED, false);
            List<String> lines = profile.summaryLines();
            int lineY = this.summaryY + 20;
            int maxLines = Math.max(1, (this.summaryHeight - 22) / 11);
            for (int i = 0; i < lines.size() && i < maxLines; i++) {
                guiGraphics.drawString(this.font,
                        this.font.plainSubstrByWidth(lines.get(i), this.summaryWidth - 16),
                        this.summaryX + 8, lineY, TEXT, false);
                lineY += 11;
            }
            int hintY = this.personalityTestButton.getY() + 21;
            if (hintY < this.statusY - 2) {
                guiGraphics.drawString(this.font,
                        this.font.plainSubstrByWidth(
                                Component.translatable("screen.playerogrammod.ring_config.personality_hint")
                                        .getString(), this.panelWidth - 32),
                        this.panelLeft + 16, hintY, MUTED, false);
            }
        }

        String statusLine = this.font.plainSubstrByWidth(this.status, this.panelWidth - 32);
        guiGraphics.drawString(this.font, statusLine, this.panelLeft + 16, this.statusY,
                this.statusColor, false);
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
        this.currentPersonality = PersonalityProfile.defaultProfile();
        this.currentPromptText = this.defaultPrompt;
        this.customPrompt = false;
        this.displayNameField.setValue("");
        this.skinUsernameField.setValue("");
        this.updatePersonalityButton();
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
        PersonalityProfile profile = this.currentPersonality == null
                ? PersonalityProfile.defaultProfile() : this.currentPersonality;
        String profileError = PersonalityTest.validate(profile);
        if (profileError != null) {
            setStatus(profileError, ERROR);
            return;
        }
        String prompt = this.currentPromptText == null ? "" : this.currentPromptText;
        String error = RingValidation.validateSettings(display, skin, prompt);
        if (error != null) {
            setStatus(error, ERROR);
            return;
        }
        this.saving = true;
        setStatus("Saving…", MUTED);
        PacketDistributor.sendToServer(new RingConfigSavePayload(
                this.ringId, display, skin, prompt, this.customPrompt, profile.toJson()));
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && this.selectedTab == Tab.APPEARANCE && isOverPreview(mouseX, mouseY)) {
            this.previewDragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (this.previewDragging && button == 0) {
            this.previewYaw = (this.previewYaw + (float) dragX * 0.7f) % 360.0f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.previewDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isOverPreview(double mouseX, double mouseY) {
        return mouseX >= this.previewLeft && mouseX < this.previewLeft + this.previewWidth
                && mouseY >= this.previewTop && mouseY < this.previewTop + this.previewHeight;
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
