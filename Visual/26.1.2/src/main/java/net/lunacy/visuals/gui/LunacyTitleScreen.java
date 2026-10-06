package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.animation.Easing;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import static net.lunacy.visuals.localization.LunacyI18n.tr;

/** Literal 1.21.11 adaptation of the supplied CustomMainMenuScreen. */
public final class LunacyTitleScreen extends Screen {
    private static final Identifier LV_LOGO = Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "textures/gui/lv_logo.png");
    private static final String TELEGRAM_URL = "https://t.me/LunacyVisuals";
    private static final String WEBSITE_URL = "https://lunacyvisual.fun";
    private static final double DESIGN_H = 1080;
    private static final double LEFT = 56;
    private static final double BLOCK_Y = 300;
    private static final double BUTTON_W = 340;
    private static final double BUTTON_H = 50;
    private static final double GAP = 9;
    private static final double EXIT_W = 108;
    private static final int TEXT = 0xFFF1F4FF;
    private static final int TEXT_2 = 0xFFE0E5FF;
    private static final int TEXT_3 = 0xFFB9C2E3;
    private static final int TEXT_4 = 0xFF95A0C6;
    private static final int MUTED = 0xFF7683AA;
    private static final int MUTED_2 = 0xFF5F6D94;
    private static final int MUTED_4 = 0xFF3D4868;
    private static final int ACCENT = 0xFF5CE1E6;
    private static final int INK = 0xFF05080E;
    private static final int DANGER = 0xFFFF5C77;

    private long openedAt;
    private long exitArmedUntil;

    public LunacyTitleScreen() {
        super(Component.translatable("lunacyvisuals.screen.title"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int guiWidth = context.guiWidth();
        int guiHeight = context.guiHeight();
        if (minecraft.getOverlay() != null) {
            context.fill(0, 0, guiWidth, guiHeight, LunacyTheme.background());
            return;
        }
        LunacyMenuChrome.titleBackground(context, guiWidth, guiHeight, mouseX, mouseY);
        if (ClientRuntime.get().startupWelcome().render(context, guiWidth, guiHeight)) {
            super.extractRenderState(context, mouseX, mouseY, delta);
            return;
        }

        View view = view(guiWidth, guiHeight);
        double mx = view.mouseX(mouseX);
        double my = view.mouseY(mouseY);
        view.begin(context);
        double reveal = ClientRuntime.get().settings().reducedMotion() ? 1
                : Easing.EASE_OUT_CUBIC.applyAsDouble(Math.clamp(
                (System.currentTimeMillis() - openedAt) / 450.0, 0.0, 1.0));
        drawLogo(context, view.designWidth);
        context.pose().pushMatrix();
        context.pose().translate(0, (float) ((1 - reveal) * 22));
        drawClock(context, view.designWidth);
        drawWelcomeBlock(context, mx, my);
        drawSocialsAndDisclaimer(context, view.designHeight, mx, my);
        context.pose().popMatrix();
        view.end(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawLogo(GuiGraphicsExtractor context, double designWidth) {
        double size = 66;
        UiRenderer.texture(context, LV_LOGO, designWidth / 2.0 - size / 2.0, 24,
                size, size, 128, 128);
    }

    private void drawClock(GuiGraphicsExtractor context, double designWidth) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String hh = time.substring(0, 2);
        String mm = time.substring(3, 5);
        double digitGap = 6.6;
        double dot = 4.4;
        double total = UiRenderer.fontWidth(FontFace.PLEX_MONO, hh, 44) + digitGap + dot + digitGap
                + UiRenderer.fontWidth(FontFace.PLEX_MONO, mm, 44);
        double right = designWidth - 56;
        double x = right - total;
        UiRenderer.font(context, FontFace.PLEX_MONO, hh, x, 38, 44, TEXT);
        double dotsX = x + UiRenderer.fontWidth(FontFace.PLEX_MONO, hh, 44) + digitGap;
        UiRenderer.roundedRect(context, dotsX, 38 + 44 * 0.52, dot, dot, dot / 2, TEXT);
        UiRenderer.roundedRect(context, dotsX, 38 + 44 * 0.74, dot, dot, dot / 2, TEXT);
        UiRenderer.font(context, FontFace.PLEX_MONO, mm, dotsX + dot + digitGap, 38, 44, TEXT);

        Locale locale = Locale.forLanguageTag(minecraft.options.languageCode.replace('_', '-'));
        LocalDate now = LocalDate.now();
        String day = cap(now.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale), locale) + ", ";
        String rest = now.getDayOfMonth() + " " + now.getMonth().getDisplayName(TextStyle.FULL, locale);
        double dayW = UiRenderer.spacedFontWidth(FontFace.GOLOS, day, 12.5, 0.75);
        double restW = UiRenderer.spacedFontWidth(FontFace.GOLOS, rest, 12.5, 0.75);
        UiRenderer.spacedFont(context, FontFace.GOLOS, day, right - dayW - restW, 88,
                12.5, 0.75, MUTED_2);
        UiRenderer.spacedFont(context, FontFace.GOLOS, rest, right - restW, 88,
                12.5, 0.75, TEXT_3);
    }

    private void drawWelcomeBlock(GuiGraphicsExtractor context, double mouseX, double mouseY) {
        String username = minecraft.getUser().getName();
        double cursorY = BLOCK_Y;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, tr("title.welcome_back"), LEFT, cursorY, 38, TEXT);
        UiRenderer.font(context, FontFace.GOLOS_SEMI,
                tr("title.again_user", username), LEFT, cursorY + 43.7, 38, TEXT);
        cursorY += 43.7 * 2 + 28;

        double buttonsY = cursorY;
        sourceButton(context, Icon.GLOBE, tr("title.multiplayer"), LEFT, buttonsY,
                BUTTON_W, true, false, false, mouseX, mouseY);
        buttonsY += BUTTON_H + GAP;
        sourceButton(context, Icon.PERSON, tr("title.singleplayer"), LEFT, buttonsY,
                BUTTON_W, false, false, false, mouseX, mouseY);
        buttonsY += BUTTON_H + GAP;
        double settingsW = BUTTON_W - EXIT_W - GAP;
        sourceButton(context, Icon.GEAR, tr("title.settings"), LEFT, buttonsY,
                settingsW, false, false, false, mouseX, mouseY);
        sourceButton(context, Icon.POWER,
                System.currentTimeMillis() < exitArmedUntil ? tr("action.confirm") : tr("action.quit"),
                LEFT + settingsW + GAP, buttonsY, EXIT_W, false, true, true, mouseX, mouseY);
    }

    private void sourceButton(GuiGraphicsExtractor context, Icon icon, String label, double x, double y, double w,
                              boolean primary, boolean danger, boolean centered,
                              double mouseX, double mouseY) {
        boolean hover = UiRenderer.inside(mouseX, mouseY, x, y, w, BUTTON_H);
        double dy = y - (hover ? 1 : 0);
        if (primary) {
            int bright = ColorUtil.mix(ACCENT, 0xFFFFFFFF, hover ? 0.07F : 0);
            UiRenderer.roundedRect(context, x + 4, dy + BUTTON_H - 6, w - 8, 12, 8,
                    ColorUtil.withAlpha(ACCENT, hover ? 100 : 60));
            UiRenderer.roundedRect(context, x, dy, w, BUTTON_H, 8, bright);
            UiRenderer.roundedRect(context, x + 8, dy + 1, w - 16, 1, 0.5, 0x5AFFFFFF);
        } else {
            UiRenderer.verticalGradientRoundedRect(context, x, dy, w, BUTTON_H, 8,
                    0xD9101426, 0xD9070A15);
            UiRenderer.roundedRect(context, x + 8, dy + 1, w - 16, 1, 0.5, 0x0DFFFFFF);
            UiRenderer.roundedBorder(context, x, dy, w, BUTTON_H, 8, 1,
                    hover ? ColorUtil.withAlpha(danger ? DANGER : 0xFFFFFFFF, danger ? 140 : 61) : 0x17FFFFFF);
        }
        int color = primary ? INK : danger ? DANGER : TEXT_2;
        double iconSize = 15;
        double labelW = UiRenderer.fontWidth(primary ? FontFace.UNBOUNDED : FontFace.UNBOUNDED_LIGHT,
                label, 12.5);
        if (centered) {
            double total = iconSize + 9 + labelW;
            double ix = x + (w - total) / 2;
            drawIcon(context, icon, ix, dy + (BUTTON_H - iconSize) / 2, iconSize, color);
            UiRenderer.font(context, FontFace.UNBOUNDED_LIGHT, label, ix + iconSize + 9,
                    dy + (BUTTON_H - 12.5 * 1.24) / 2, 12.5, color);
        } else {
            double ix = x + 16;
            drawIcon(context, icon, ix, dy + (BUTTON_H - iconSize) / 2, iconSize,
                    primary ? INK : MUTED);
            UiRenderer.font(context, primary ? FontFace.UNBOUNDED : FontFace.UNBOUNDED_LIGHT,
                    label, ix + iconSize + 12, dy + (BUTTON_H - 12.5 * 1.24) / 2, 12.5, color);
        }
    }

    private void drawIcon(GuiGraphicsExtractor context, Icon icon, double x, double y, double size, int color) {
        String glyph = switch (icon) {
            case GLOBE -> "C";
            case PERSON -> "A";
            case GEAR -> "B";
            case POWER -> "D";
        };
        UiRenderer.font(context, FontFace.ICONS_2, glyph, x, y, size, color);
    }

    private void drawSocialsAndDisclaimer(GuiGraphicsExtractor context, double designHeight,
                                          double mouseX, double mouseY) {
        String[] lines = {tr("title.footer_line_one"), tr("title.footer_line_two")};
        double lineH = 11 * 1.5;
        double textY = designHeight - 44 - lineH * lines.length;
        for (int i = 0; i < lines.length; i++) {
            UiRenderer.font(context, FontFace.GOLOS, lines[i], LEFT, textY + i * lineH, 11, MUTED_4);
        }
        double chipY = textY - 16 - 38;
        String[] icons = {"E", "LV"};
        for (int i = 0; i < icons.length; i++) {
            double x = LEFT + i * 47;
            boolean hover = UiRenderer.inside(mouseX, mouseY, x, chipY, 38, 38);
            double y = chipY - (hover ? 2 : 0);
            UiRenderer.verticalGradientRoundedRect(context, x, y, 38, 38, 8,
                    0xD9101426, 0xD9070A15);
            UiRenderer.roundedRect(context, x + 8, y + 1, 22, 1, 0.5, 0x0DFFFFFF);
            UiRenderer.roundedBorder(context, x, y, 38, 38, 8, 1, hover ? 0x3DFFFFFF : 0x17FFFFFF);
            if (i == 1) {
                double leftW = UiRenderer.fontWidth(FontFace.UNBOUNDED, "L", 12);
                double rightW = UiRenderer.fontWidth(FontFace.UNBOUNDED, "V", 12);
                double ix = x + (38 - leftW - rightW) / 2;
                double iy = y + (38 - 12 * 1.24) / 2;
                UiRenderer.font(context, FontFace.UNBOUNDED, "L", ix, iy, 12,
                        hover ? TEXT : TEXT_2);
                UiRenderer.font(context, FontFace.UNBOUNDED, "V", ix + leftW, iy, 12, ACCENT);
            } else {
                double iw = UiRenderer.fontWidth(FontFace.ICONS_2, icons[i], 17);
                UiRenderer.font(context, FontFace.ICONS_2, icons[i], x + (38 - iw) / 2,
                        y + (38 - 17) / 2, 17, hover ? ACCENT : MUTED);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        // Mouse input must not accidentally fast-forward the startup animation.
        if (ClientRuntime.get().startupWelcome().isActive()) return true;
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        View view = view();
        double x = view.mouseX(click.x());
        double y = view.mouseY(click.y());
        double buttonY = buttonStartY();
        if (hit(x, y, LEFT, buttonY, BUTTON_W, BUTTON_H)) {
            minecraft.setScreen(new LunacyMultiplayerScreen(this));
        } else if (hit(x, y, LEFT, buttonY + BUTTON_H + GAP, BUTTON_W, BUTTON_H)) {
            minecraft.setScreen(new LunacyWorldSelectScreen(this));
        } else if (hit(x, y, LEFT, buttonY + (BUTTON_H + GAP) * 2,
                BUTTON_W - EXIT_W - GAP, BUTTON_H)) {
            minecraft.setScreen(new OptionsScreen(this, minecraft.options, false));
        } else if (hit(x, y, LEFT + BUTTON_W - EXIT_W,
                buttonY + (BUTTON_H + GAP) * 2, EXIT_W, BUTTON_H)) {
            if (System.currentTimeMillis() < exitArmedUntil) minecraft.stop();
            else exitArmedUntil = System.currentTimeMillis() + 3000;
        } else if (hit(x, y, LEFT, socialChipY(view.designHeight), 38, 38)) {
            Util.getPlatform().openUri(TELEGRAM_URL);
        } else if (hit(x, y, LEFT + 47, socialChipY(view.designHeight), 38, 38)) {
            Util.getPlatform().openUri(WEBSITE_URL);
        } else {
            return super.mouseClicked(click, doubled);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (ClientRuntime.get().startupWelcome().isActive() && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            ClientRuntime.get().startupWelcome().skip();
            openedAt = System.currentTimeMillis();
            return true;
        }
        return super.keyPressed(input);
    }

    private static double socialChipY(double designHeight) {
        double lineHeight = 11 * 1.5;
        double textY = designHeight - 44 - lineHeight * 2;
        return textY - 16 - 38;
    }

    private double buttonStartY() { return BLOCK_Y + 43.7 * 2 + 28; }

    private View view() {
        return view(minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    /**
     * Since 26.1 the Screen dimensions are window pixels while GuiGraphicsExtractor uses
     * logical Minecraft GUI pixels. Keeping the two separate prevents a scale-2 UI from
     * rendering twice as large and overlapping its own rows.
     */
    private static View view(int guiWidth, int guiHeight) {
        double rawScale = Math.max(0.5, guiHeight / DESIGN_H);
        double designWidth = Math.max(1, Math.round(guiWidth / rawScale));
        double designHeight = Math.max(1, Math.round(guiHeight / rawScale));
        return new View(rawScale, designWidth, designHeight);
    }

    private static boolean hit(double mx, double my, double x, double y, double w, double h) {
        return UiRenderer.inside(mx, my, x, y, w, h);
    }

    private static String cap(String value, Locale locale) {
        return value == null || value.isEmpty() ? value
                : value.substring(0, 1).toUpperCase(locale) + value.substring(1);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private enum Icon { GLOBE, PERSON, GEAR, POWER }

    private record View(double scale, double designWidth, double designHeight) {
        void begin(GuiGraphicsExtractor context) {
            context.pose().pushMatrix();
            context.pose().scale((float) scale, (float) scale);
        }
        void end(GuiGraphicsExtractor context) { context.pose().popMatrix(); }
        double mouseX(double x) { return x / scale; }
        double mouseY(double y) { return y / scale; }
    }
}
