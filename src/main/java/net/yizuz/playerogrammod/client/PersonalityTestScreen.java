package net.yizuz.playerogrammod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.yizuz.playerogrammod.personality.PersonalityOption;
import net.yizuz.playerogrammod.personality.PersonalityProfile;
import net.yizuz.playerogrammod.personality.PersonalityQuestion;
import net.yizuz.playerogrammod.personality.PersonalitySession;
import net.yizuz.playerogrammod.personality.PersonalityTest;

import java.util.ArrayList;
import java.util.List;

/**
 * One-question-at-a-time personality questionnaire. The session owns navigation
 * and answer preservation; this screen only renders the current question.
 */
public final class PersonalityTestScreen extends Screen {
    private static final int PANEL = 0xEE07110B;
    private static final int PANEL_HEADER_TOP = 0xF00D2418;
    private static final int PANEL_HEADER_BOTTOM = 0xE807120C;
    private static final int EDGE = 0xFF3CFF88;
    private static final int EDGE_DIM = 0x663CFF88;
    private static final int TEXT = 0xFFE9FFF2;
    private static final int MUTED = 0xFF8FB9A4;
    private static final int ERROR = 0xFFFF7070;
    private static final int PROGRESS_BACK = 0x5522472F;

    private final RingConfigScreen previous;
    private final PersonalitySession session;

    private GreenButton backButton;
    private GreenButton cancelButton;
    private GreenButton nextButton;
    private final List<GreenButton> optionButtons = new ArrayList<>();
    private String status = "";
    private int statusColor = MUTED;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int optionsTop;
    private int optionWidth;

    public PersonalityTestScreen(RingConfigScreen previous, PersonalityProfile initial) {
        super(Component.translatable("screen.playerogrammod.personality_test.title"));
        this.previous = previous;
        this.session = new PersonalitySession(initial);
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - 16, 480);
        this.panelHeight = Math.min(this.height - 16, 300);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.loadQuestion();
    }

    private void loadQuestion() {
        this.clearWidgets();
        this.optionButtons.clear();
        this.status = "";
        this.statusColor = MUTED;

        PersonalityQuestion question = this.session.current();
        int columns = 3;
        int gap = 6;
        this.optionWidth = Math.max(80, (this.panelWidth - 40 - gap * (columns - 1)) / columns);
        this.optionsTop = this.panelTop + 96;
        int rowGap = 4;
        int rowHeight = 20 + rowGap;
        for (int i = 0; i < question.options().size(); i++) {
            PersonalityOption option = question.options().get(i);
            int column = i % columns;
            int row = i / columns;
            int x = this.panelLeft + 20 + column * (this.optionWidth + gap);
            int y = this.optionsTop + row * rowHeight;
            GreenButton button = new GreenButton(x, y, this.optionWidth, 20,
                    Component.literal(option.label()),
                    ignored -> toggleOption(option), false);
            button.setSelected(this.session.isSelected(question.id(), option.id()));
            this.optionButtons.add(this.addRenderableWidget(button));
        }

        int bottom = this.panelTop + this.panelHeight - 20;
        int right = this.panelLeft + this.panelWidth;
        this.backButton = this.addRenderableWidget(new GreenButton(
                this.panelLeft + 20, bottom, 78, 20,
                Component.translatable("screen.playerogrammod.personality_test.back"),
                ignored -> goBack(), false));
        this.cancelButton = this.addRenderableWidget(new GreenButton(
                this.panelLeft + 104, bottom, 86, 20,
                Component.translatable("screen.playerogrammod.personality_test.cancel"),
                ignored -> onClose(), false));
        this.nextButton = this.addRenderableWidget(new GreenButton(
                right - 20 - 132, bottom, 132, 20,
                Component.translatable(this.session.isLast()
                        ? "screen.playerogrammod.personality_test.finish"
                        : "screen.playerogrammod.personality_test.next"),
                ignored -> nextOrFinish(), false));

        this.backButton.active = !this.session.isFirst();
        this.nextButton.active = this.session.currentAnswered();
    }

    private void toggleOption(PersonalityOption option) {
        this.session.select(this.session.current(), option.id());
        PersonalityQuestion question = this.session.current();
        for (int i = 0; i < question.options().size() && i < this.optionButtons.size(); i++) {
            PersonalityOption current = question.options().get(i);
            this.optionButtons.get(i).setSelected(
                    this.session.isSelected(question.id(), current.id()));
        }
        this.nextButton.active = this.session.currentAnswered();
    }

    private void goBack() {
        if (this.session.back()) {
            this.loadQuestion();
        }
    }

    private void nextOrFinish() {
        if (!this.session.currentAnswered()) {
            return;
        }
        if (this.session.isLast()) {
            finish();
            return;
        }
        if (this.session.advance()) {
            this.loadQuestion();
        }
    }

    private void finish() {
        PersonalityProfile profile = this.session.toProfile();
        String error = PersonalityTest.validate(profile);
        if (error != null) {
            this.status = error;
            this.statusColor = ERROR;
            return;
        }
        if (this.previous != null) {
            this.previous.applyPersonality(profile);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (this.previous != null) {
            minecraft.setScreen(this.previous);
        } else {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        PersonalityQuestion question = this.session.current();
        int right = this.panelLeft + this.panelWidth;
        int progressY = this.panelTop + 40;
        int progressLeft = this.panelLeft + 20;
        int progressRight = right - 20;
        guiGraphics.fill(progressLeft, progressY, progressRight, progressY + 3, PROGRESS_BACK);
        int progressWidth = Math.max(0, (int) ((progressRight - progressLeft)
                * (this.session.index() + 1) / (float) this.session.size()));
        guiGraphics.fill(progressLeft, progressY, progressLeft + progressWidth, progressY + 3, EDGE);

        guiGraphics.drawString(this.font, this.title, this.panelLeft + 20, this.panelTop + 10,
                EDGE, false);
        guiGraphics.drawString(this.font,
                Component.translatable("screen.playerogrammod.personality_test.progress",
                        this.session.number(), this.session.size()),
                this.panelLeft + 20, this.panelTop + 24, MUTED, false);
        guiGraphics.drawString(this.font, question.category(), this.panelLeft + 20,
                this.panelTop + 53, EDGE, false);
        List<FormattedCharSequence> lines = this.font.split(
                Component.literal(question.prompt()), this.panelWidth - 40);
        int lineY = this.panelTop + 65;
        for (int i = 0; i < lines.size() && i < 3; i++) {
            guiGraphics.drawString(this.font, lines.get(i), this.panelLeft + 20, lineY, TEXT, false);
            lineY += 10;
        }

        if (!this.status.isBlank()) {
            String line = this.font.plainSubstrByWidth(this.status, Math.max(80, this.panelWidth / 2));
            guiGraphics.drawString(this.font, line,
                    this.panelLeft + this.panelWidth - 20 - this.font.width(line),
                    this.panelTop + 11, this.statusColor, false);
        }
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
                right, this.panelTop + 47, PANEL_HEADER_TOP, PANEL_HEADER_BOTTOM);
        guiGraphics.fill(this.panelLeft, this.panelTop, right, this.panelTop + 1, EDGE);
        for (int y = this.panelTop + 2; y < bottom; y += 6) {
            guiGraphics.fill(this.panelLeft + 1, y, right - 1, y + 1, 0x083CFF88);
        }
        drawFrame(guiGraphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, EDGE_DIM);
        drawCorners(guiGraphics, this.panelLeft, this.panelTop, right, bottom, EDGE);
    }

    private void drawFrame(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
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

    @Override
    public void onClose() {
        if (this.previous != null) {
            Minecraft.getInstance().setScreen(this.previous);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
