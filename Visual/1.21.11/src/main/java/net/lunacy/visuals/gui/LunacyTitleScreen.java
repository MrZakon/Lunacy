package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.animation.Easing;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

import static net.lunacy.visuals.localization.LunacyI18n.tr;

/** Responsive Lunacy title menu with dynamic non-overlapping layout. */
public final class LunacyTitleScreen extends Screen {
    private static final Identifier LV_LOGO = Identifier.of(LunacyVisuals.MOD_ID, "textures/gui/lv_logo.png");
    private static final String TELEGRAM_URL = "https://t.me/LunacyVisuals";
    private static final String WEBSITE_URL = "https://lunacyvisual.fun";
    private static final int TEXT = 0xFFF1F4FF;
    private static final int TEXT_2 = 0xFFE0E5FF;
    private static final int TEXT_3 = 0xFFB9C2E3;
    private static final int MUTED = 0xFF7683AA;
    private static final int MUTED_2 = 0xFF5F6D94;
    private static final int MUTED_4 = 0xFF3D4868;
    private static final int ACCENT = 0xFF5CE1E6;
    private static final int INK = 0xFF05080E;
    private static final int DANGER = 0xFFFF5C77;

    private long openedAt;
    private long exitArmedUntil;

    public LunacyTitleScreen() {
        super(Text.translatable("lunacyvisuals.screen.title"));
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client.getOverlay() != null) {
            context.fill(0, 0, width, height, LunacyTheme.background());
            return;
        }
        LunacyMenuChrome.titleBackground(context, width, height, mouseX, mouseY);
        if (ClientRuntime.get().startupWelcome().render(context, width, height)) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        Layout layout = layout();
        double reveal = ClientRuntime.get().settings().reducedMotion() ? 1
                : Easing.EASE_OUT_CUBIC.applyAsDouble(Math.clamp(
                (System.currentTimeMillis() - openedAt) / 450.0, 0.0, 1.0));
        drawLogo(context, layout);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, (float) ((1 - reveal) * 12 * layout.s));
        drawClock(context, layout);
        drawWelcomeBlock(context, layout, mouseX, mouseY);
        drawSocialsAndDisclaimer(context, layout, mouseX, mouseY);
        context.getMatrices().popMatrix();
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLogo(DrawContext context, Layout layout) {
        UiRenderer.texture(context, LV_LOGO, (width - layout.logo) / 2.0, layout.logoY,
                layout.logo, layout.logo, 128, 128);
    }

    private void drawClock(DrawContext context, Layout layout) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String hh = time.substring(0, 2);
        String mm = time.substring(3, 5);
        double size = layout.clockSize;
        double digitGap = 6.6 * layout.s;
        double dot = 4.4 * layout.s;
        double total = UiRenderer.fontWidth(FontFace.PLEX_MONO, hh, size) + digitGap + dot + digitGap
                + UiRenderer.fontWidth(FontFace.PLEX_MONO, mm, size);
        double right = width - layout.left;
        double x = right - total;
        double y = layout.clockY;
        UiRenderer.font(context, FontFace.PLEX_MONO, hh, x, y, size, TEXT);
        double dotsX = x + UiRenderer.fontWidth(FontFace.PLEX_MONO, hh, size) + digitGap;
        UiRenderer.roundedRect(context, dotsX, y + size * 0.52, dot, dot, dot / 2, TEXT);
        UiRenderer.roundedRect(context, dotsX, y + size * 0.74, dot, dot, dot / 2, TEXT);
        UiRenderer.font(context, FontFace.PLEX_MONO, mm, dotsX + dot + digitGap, y, size, TEXT);

        Locale locale = Locale.forLanguageTag(client.options.language.replace('_', '-'));
        LocalDate now = LocalDate.now();
        String day = cap(now.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale), locale) + ", ";
        String rest = now.getDayOfMonth() + " " + now.getMonth().getDisplayName(TextStyle.FULL, locale);
        double dateSize = 12.5 * layout.s;
        double dayW = UiRenderer.spacedFontWidth(FontFace.GOLOS, day, dateSize, 0.75);
        double restW = UiRenderer.spacedFontWidth(FontFace.GOLOS, rest, dateSize, 0.75);
        UiRenderer.spacedFont(context, FontFace.GOLOS, day, right - dayW - restW, y + size + 6 * layout.s,
                dateSize, 0.75, MUTED_2);
        UiRenderer.spacedFont(context, FontFace.GOLOS, rest, right - restW, y + size + 6 * layout.s,
                dateSize, 0.75, TEXT_3);
    }

    private void drawWelcomeBlock(DrawContext context, Layout layout, double mouseX, double mouseY) {
        String username = client.getSession().getUsername();
        double y = layout.welcomeY;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, tr("title.welcome_back"), layout.left, y, layout.welcomeSize, TEXT);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, tr("title.again_user", username),
                layout.left, y + layout.welcomeSize * 1.15, layout.welcomeSize, TEXT);

        double buttonsY = layout.buttonY;
        sourceButton(context, layout, Icon.GLOBE, tr("title.multiplayer"), layout.left, buttonsY,
                layout.buttonW, true, false, false, mouseX, mouseY);
        buttonsY += layout.buttonH + layout.gap;
        sourceButton(context, layout, Icon.PERSON, tr("title.singleplayer"), layout.left, buttonsY,
                layout.buttonW, false, false, false, mouseX, mouseY);
        buttonsY += layout.buttonH + layout.gap;
        sourceButton(context, layout, Icon.GEAR, tr("title.settings"), layout.left, buttonsY,
                layout.settingsW, false, false, false, mouseX, mouseY);
        sourceButton(context, layout, Icon.POWER,
                System.currentTimeMillis() < exitArmedUntil ? tr("action.confirm") : tr("action.quit"),
                layout.left + layout.settingsW + layout.gap, buttonsY, layout.exitW, false, true, true, mouseX, mouseY);
    }

    private void sourceButton(DrawContext context, Layout layout, Icon icon, String label,
                              double x, double y, double w, boolean primary, boolean danger, boolean centered,
                              double mouseX, double mouseY) {
        boolean hover = UiRenderer.inside(mouseX, mouseY, x, y, w, layout.buttonH);
        double dy = y - (hover ? 1 : 0);
        if (primary) {
            int bright = ColorUtil.mix(ACCENT, 0xFFFFFFFF, hover ? 0.07F : 0);
            UiRenderer.roundedRect(context, x + 4, dy + layout.buttonH - 6, w - 8, 12, 8,
                    ColorUtil.withAlpha(ACCENT, hover ? 100 : 60));
            UiRenderer.roundedRect(context, x, dy, w, layout.buttonH, 8, bright);
            UiRenderer.roundedRect(context, x + 8, dy + 1, w - 16, 1, 0.5, 0x5AFFFFFF);
        } else {
            UiRenderer.verticalGradientRoundedRect(context, x, dy, w, layout.buttonH, 8,
                    0xD9101426, 0xD9070A15);
            UiRenderer.roundedRect(context, x + 8, dy + 1, w - 16, 1, 0.5, 0x0DFFFFFF);
            UiRenderer.roundedBorder(context, x, dy, w, layout.buttonH, 8, 1,
                    hover ? ColorUtil.withAlpha(danger ? DANGER : 0xFFFFFFFF, danger ? 140 : 61) : 0x17FFFFFF);
        }
        int color = primary ? INK : danger ? DANGER : TEXT_2;
        FontFace face = primary ? FontFace.UNBOUNDED : FontFace.UNBOUNDED_LIGHT;
        double iconSize = Math.min(13 * layout.s, layout.buttonH * 0.34);
        double fontSize = layout.buttonFont;
        double pad = Math.max(10, 14 * layout.s);
        double iconGap = Math.max(6, 8 * layout.s);
        double maxLabel = Math.max(8, w - pad * 2 - iconSize - iconGap);
        while (fontSize > 6.2 && UiRenderer.fontWidth(face, label, fontSize) > maxLabel) {
            fontSize -= 0.15;
        }
        String fitted = fit(face, label, maxLabel, fontSize);
        double labelW = UiRenderer.fontWidth(face, fitted, fontSize);
        double contentW = iconSize + iconGap + labelW;
        double ix;
        if (centered) {
            ix = x + Math.max(pad, (w - contentW) / 2);
        } else {
            ix = x + pad;
        }
        double iy = dy + (layout.buttonH - iconSize) / 2;
        double ty = dy + (layout.buttonH - fontSize) / 2;
        drawIcon(context, icon, ix, iy, iconSize, primary ? INK : danger ? color : MUTED);
        UiRenderer.font(context, face, fitted, ix + iconSize + iconGap, ty, fontSize, color);
    }

    private void drawIcon(DrawContext context, Icon icon, double x, double y, double size, int color) {
        String glyph = switch (icon) {
            case GLOBE -> "C";
            case PERSON -> "A";
            case GEAR -> "B";
            case POWER -> "D";
        };
        UiRenderer.font(context, FontFace.ICONS_2, glyph, x, y, size, color);
    }

    private void drawSocialsAndDisclaimer(DrawContext context, Layout layout, double mouseX, double mouseY) {
        String[] lines = {tr("title.footer_line_one"), tr("title.footer_line_two")};
        double fontSize = layout.footerSize;
        double lineH = fontSize * 1.45;
        for (int i = 0; i < lines.length; i++) {
            String line = fit(FontFace.GOLOS, lines[i], Math.max(80, width - layout.left * 2), fontSize);
            UiRenderer.font(context, FontFace.GOLOS, line, layout.left, layout.footerY + i * lineH, fontSize, MUTED_4);
        }
        String[] icons = {"E", "LV"};
        for (int i = 0; i < icons.length; i++) {
            double x = layout.left + i * (layout.chip + 9 * layout.s);
            boolean hover = UiRenderer.inside(mouseX, mouseY, x, layout.chipY, layout.chip, layout.chip);
            double y = layout.chipY - (hover ? 2 : 0);
            UiRenderer.verticalGradientRoundedRect(context, x, y, layout.chip, layout.chip, 8,
                    0xD9101426, 0xD9070A15);
            UiRenderer.roundedRect(context, x + 8, y + 1, layout.chip - 16, 1, 0.5, 0x0DFFFFFF);
            UiRenderer.roundedBorder(context, x, y, layout.chip, layout.chip, 8, 1, hover ? 0x3DFFFFFF : 0x17FFFFFF);
            if (i == 1) {
                double leftW = UiRenderer.fontWidth(FontFace.UNBOUNDED, "L", 12 * layout.s);
                double rightW = UiRenderer.fontWidth(FontFace.UNBOUNDED, "V", 12 * layout.s);
                double ix = x + (layout.chip - leftW - rightW) / 2;
                double iy = y + (layout.chip - 12 * layout.s) / 2;
                UiRenderer.font(context, FontFace.UNBOUNDED, "L", ix, iy, 12 * layout.s, hover ? TEXT : TEXT_2);
                UiRenderer.font(context, FontFace.UNBOUNDED, "V", ix + leftW, iy, 12 * layout.s, ACCENT);
            } else {
                double iw = UiRenderer.fontWidth(FontFace.ICONS_2, icons[i], 17 * layout.s);
                UiRenderer.font(context, FontFace.ICONS_2, icons[i], x + (layout.chip - iw) / 2,
                        y + (layout.chip - 17 * layout.s) / 2, 17 * layout.s, hover ? ACCENT : MUTED);
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (ClientRuntime.get().startupWelcome().isActive()) return true;
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        Layout layout = layout();
        double x = click.x();
        double y = click.y();
        double buttonY = layout.buttonY;
        if (hit(x, y, layout.left, buttonY, layout.buttonW, layout.buttonH)) {
            client.setScreen(new MultiplayerScreen(this));
        } else if (hit(x, y, layout.left, buttonY + layout.buttonH + layout.gap, layout.buttonW, layout.buttonH)) {
            client.setScreen(new SelectWorldScreen(this));
        } else if (hit(x, y, layout.left, buttonY + (layout.buttonH + layout.gap) * 2,
                layout.settingsW, layout.buttonH)) {
            client.setScreen(new OptionsScreen(this, client.options));
        } else if (hit(x, y, layout.left + layout.settingsW + layout.gap,
                buttonY + (layout.buttonH + layout.gap) * 2, layout.exitW, layout.buttonH)) {
            if (System.currentTimeMillis() < exitArmedUntil) client.scheduleStop();
            else exitArmedUntil = System.currentTimeMillis() + 3000;
        } else if (hit(x, y, layout.left, layout.chipY, layout.chip, layout.chip)) {
            Util.getOperatingSystem().open(TELEGRAM_URL);
        } else if (hit(x, y, layout.left + layout.chip + 9 * layout.s, layout.chipY, layout.chip, layout.chip)) {
            Util.getOperatingSystem().open(WEBSITE_URL);
        } else {
            return super.mouseClicked(click, doubled);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (ClientRuntime.get().startupWelcome().isActive() && input.key() == GLFW.GLFW_KEY_ESCAPE) {
            ClientRuntime.get().startupWelcome().skip();
            openedAt = System.currentTimeMillis();
            return true;
        }
        return super.keyPressed(input);
    }

    private Layout layout() {
        double s = Math.clamp(Math.min(width / 1600.0, height / 900.0), 0.38, 1.12);
        double left = Math.max(18, 56 * s);
        double gap = Math.max(6, 9 * s);
        double chip = Math.max(28, 38 * s);
        double logo = Math.max(40, 66 * s);
        double footerSize = Math.max(8, 11 * s);
        double welcomeSize = Math.max(18, 38 * s);
        double buttonH = Math.max(32, 50 * s);
        double buttonW = Math.min(340 * s, Math.max(200, width * 0.36));
        double exitW = Math.max(72, 108 * s);
        double settingsW = Math.max(110, buttonW - exitW - gap);
        double logoY = Math.max(8, 18 * s);
        double clockY = Math.max(10, 20 * s);
        double bottomPad = Math.max(12, 22 * s);

        double footerLineH = footerSize * 1.45;
        double footerY = height - bottomPad - footerLineH * 2;
        double chipY = footerY - Math.max(8, 14 * s) - chip;
        double minTop = logoY + logo + Math.max(8, 16 * s);
        double maxBottom = chipY - Math.max(8, 14 * s);

        double welcomeH = welcomeSize * 1.15 * 2 + Math.max(10, 22 * s);
        double buttonsH = buttonH * 3 + gap * 2;
        double blockH = welcomeH + buttonsH;

        int guard = 0;
        while (blockH > Math.max(80, maxBottom - minTop) && guard++ < 32) {
            if (welcomeSize > 15) welcomeSize -= 0.8;
            if (buttonH > 26) buttonH -= 0.7;
            if (gap > 5) gap -= 0.2;
            if (chip > 24) chip -= 0.4;
            if (footerSize > 7.2) footerSize -= 0.15;
            footerLineH = footerSize * 1.45;
            footerY = height - bottomPad - footerLineH * 2;
            chipY = footerY - Math.max(6, 12 * s) - chip;
            maxBottom = chipY - Math.max(6, 12 * s);
            welcomeH = welcomeSize * 1.15 * 2 + Math.max(8, 16 * s);
            buttonsH = buttonH * 3 + gap * 2;
            blockH = welcomeH + buttonsH;
        }

        double welcomeY = minTop;
        if (maxBottom - minTop > blockH) {
            welcomeY = minTop + (maxBottom - minTop - blockH) * 0.28;
        }
        welcomeY = Math.clamp(welcomeY, Math.min(minTop, 8), Math.max(8, maxBottom - blockH));
        double buttonY = welcomeY + welcomeH;
        if (buttonY + buttonsH > chipY - 6) {
            buttonY = Math.max(welcomeY + welcomeH * 0.85, chipY - 6 - buttonsH);
            welcomeY = Math.max(4, buttonY - welcomeH);
        }

        settingsW = Math.max(110, buttonW - exitW - gap);
        double buttonFont = Math.max(6.8, Math.min(buttonH * 0.24, 10.5));
        return new Layout(s, left, buttonW, buttonH, gap, settingsW, exitW, chip, logo, logoY, clockY,
                Math.max(18, 44 * s), welcomeY, welcomeSize, buttonY, buttonFont,
                chipY, footerY, footerSize);
    }

    private static boolean hit(double mx, double my, double x, double y, double w, double h) {
        return UiRenderer.inside(mx, my, x, y, w, h);
    }

    private static String cap(String value, Locale locale) {
        return value == null || value.isEmpty() ? value
                : value.substring(0, 1).toUpperCase(locale) + value.substring(1);
    }

    private static String fit(FontFace face, String value, double maxWidth, double size) {
        if (UiRenderer.fontWidth(face, value, size) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + suffix;
            if (UiRenderer.fontWidth(face, candidate, size) <= maxWidth) return candidate;
        }
        return suffix;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private enum Icon { GLOBE, PERSON, GEAR, POWER }

    private record Layout(double s, double left, double buttonW, double buttonH, double gap,
                          double settingsW, double exitW, double chip, double logo, double logoY,
                          double clockY, double clockSize, double welcomeY, double welcomeSize,
                          double buttonY, double buttonFont, double chipY, double footerY, double footerSize) {}
}
