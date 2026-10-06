package net.lunacy.visuals.gui;

import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static net.lunacy.visuals.localization.LunacyI18n.tr;

/** Compact in-game menu copied from the supplied Linken main-menu composition. */
public final class LunacyPauseScreen extends Screen {
    private static final double DESIGN_W = 920;
    private static final double DESIGN_H = 540;
    private static final double LEFT = 58;
    private static final double TOP = 108;
    private static final double MENU_W = 338;
    private static final double ROW_H = 44;
    private static final double GAP = 8;

    public LunacyPauseScreen() {
        super(Component.translatable("lunacyvisuals.screen.pause"));
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int guiWidth = context.guiWidth();
        int guiHeight = context.guiHeight();
        LunacyMenuChrome.worldBackdrop(context, guiWidth, guiHeight);
        LunacyMenuChrome.Viewport view = LunacyMenuChrome.windowViewport(guiWidth, guiHeight, DESIGN_W, DESIGN_H);
        double mx = view.mouseX(mouseX);
        double my = view.mouseY(mouseY);
        view.begin(context);
        LunacyMenuChrome.centeredLogo(context, DESIGN_W);

        UiRenderer.spacedFont(context, FontFace.UNBOUNDED_LIGHT, tr("pause.session_suspended"),
                LEFT, TOP, 7.5, 1.5, LunacyMenuChrome.GREEN);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, tr("pause.game_paused"),
                LEFT, TOP + 23, 29, LunacyMenuChrome.TEXT);
        UiRenderer.font(context, FontFace.GOLOS, statusLine(), LEFT, TOP + 64,
                8.5, LunacyMenuChrome.MUTED);

        double accountY = TOP + 93;
        UiRenderer.roundedRect(context, LEFT, accountY, MENU_W, 48, 14, 0xB5121A16);
        UiRenderer.roundedBorder(context, LEFT, accountY, MENU_W, 48, 14, 1,
                ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 36));
        UiRenderer.roundedRect(context, LEFT + 10, accountY + 9, 30, 30, 15, 0xFF26372E);
        String player = minecraft.player == null ? tr("common.player") : minecraft.player.getName().getString();
        UiRenderer.font(context, FontFace.GOLOS_SEMI, player.substring(0, 1).toUpperCase(),
                LEFT + 20, accountY + 18, 8.5, LunacyMenuChrome.GREEN);
        UiRenderer.spacedFont(context, FontFace.UNBOUNDED_LIGHT, tr("pause.current_session"),
                LEFT + 52, accountY + 9, 6.2, 0.8, LunacyMenuChrome.MUTED);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, player, LEFT + 52, accountY + 25,
                10, LunacyMenuChrome.TEXT);
        LunacyMenuChrome.pill(context, tr("state.online"), LEFT + MENU_W - 78, accountY + 14,
                LunacyMenuChrome.GREEN);

        double y = accountY + 62;
        menuButton(context, tr("action.continue"), y, true, false, mx, my);
        y += ROW_H + GAP;
        menuButton(context, tr("title.visual_modules"), y, false, false, mx, my);
        y += ROW_H + GAP;
        menuButton(context, tr("title.minecraft_options"), y, false, false, mx, my);
        y += ROW_H + GAP;
        menuButton(context, tr("action.disconnect"), y, false, true, mx, my);

        view.end(context);
    }

    private void menuButton(GuiGraphicsExtractor context, String label, double y,
                            boolean primary, boolean danger, double mouseX, double mouseY) {
        LunacyMenuChrome.button(context, label, "", LEFT, y, MENU_W, ROW_H, primary, danger,
                UiRenderer.inside(mouseX, mouseY, LEFT, y, MENU_W, ROW_H));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled);
        LunacyMenuChrome.Viewport view = LunacyMenuChrome.windowViewport(
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                DESIGN_W, DESIGN_H);
        double x = view.mouseX(click.x());
        double y = view.mouseY(click.y());
        double first = TOP + 93 + 62;
        if (hit(x, y, LEFT, first, MENU_W, ROW_H)) {
            onClose();
        } else if (hit(x, y, LEFT, first + ROW_H + GAP, MENU_W, ROW_H)) {
            minecraft.setScreen(new ClickGuiScreen(ModuleCategory.HUD));
        } else if (hit(x, y, LEFT, first + (ROW_H + GAP) * 2, MENU_W, ROW_H)) {
            minecraft.setScreen(new OptionsScreen(this, minecraft.options, false));
        } else if (hit(x, y, LEFT, first + (ROW_H + GAP) * 3, MENU_W, ROW_H)) {
            minecraft.disconnect(new LunacyTitleScreen(), false);
        } else {
            return super.mouseClicked(click, doubled);
        }
        GuiSound.play("clickgui_open");
        return true;
    }

    private String statusLine() {
        return location() + "  /  " + ping() + " " + tr("unit.ms") + "  /  "
                + Math.max(0, minecraft.getFps()) + " " + tr("metric.fps");
    }

    private String location() {
        ServerData server = minecraft.getCurrentServer();
        return server == null ? tr("title.singleplayer") : server.ip.toUpperCase();
    }

    private int ping() {
        ServerData server = minecraft.getCurrentServer();
        return server == null ? 0 : (int) Math.max(0, server.ping);
    }

    private static boolean hit(double mx, double my, double x, double y, double w, double h) {
        return UiRenderer.inside(mx, my, x, y, w, h);
    }
}
