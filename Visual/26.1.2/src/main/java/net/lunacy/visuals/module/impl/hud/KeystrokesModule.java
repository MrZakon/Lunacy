package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.hud.HudAnchor;
import net.lunacy.visuals.hud.HudPosition;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern glass Keystrokes widget: WASD, LMB, RMB with real-time CPS counter and Space bar.
 */
public final class KeystrokesModule extends HudModule {
    private static final List<Long> LEFT_CLICKS = new ArrayList<>();
    private static final List<Long> RIGHT_CLICKS = new ArrayList<>();
    private static double lastReach = 0.0;
    private static long lastReachTime = 0;

    private final BooleanSetting showCps = add(new BooleanSetting("cps", "Show CPS", "Show clicks per second on mouse buttons", true));
    private final BooleanSetting showSpace = add(new BooleanSetting("space", "Spacebar", "Show jump space bar", true));
    private final BooleanSetting showReach = add(new BooleanSetting("reach", "Reach Display", "Show hit distance upon attack", true));
    private final ColorSetting accent = add(new ColorSetting("accent", "Accent", "Active key glow accent",
            new ColorValue(0xFF42FFA7, 0xFFA866FF, false, 1.0)));

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Displays WASD, mouse buttons, real-time CPS and reach",
                new HudPosition(HudAnchor.BOTTOM_LEFT, 12, -18, 1));
        setEnabled(true);
    }

    public static void onLeftClick() {
        long now = System.currentTimeMillis();
        synchronized (LEFT_CLICKS) { LEFT_CLICKS.add(now); }
    }

    public static void onRightClick() {
        long now = System.currentTimeMillis();
        synchronized (RIGHT_CLICKS) { RIGHT_CLICKS.add(now); }
    }

    public static void onHit(double reach) {
        lastReach = reach;
        lastReachTime = System.currentTimeMillis();
    }

    private static int getCps(List<Long> clicks) {
        long now = System.currentTimeMillis();
        synchronized (clicks) {
            clicks.removeIf(t -> now - t > 1000);
            return clicks.size();
        }
    }

    @Override protected double width() { return 68; }
    @Override protected double height() {
        double h = 68;
        if (showSpace.get()) h += 15;
        if (showReach.get() && System.currentTimeMillis() - lastReachTime < 2500) h += 14;
        return h;
    }

    @Override
    protected void render(GuiGraphicsExtractor context, double x, double y, double scale, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        long window = client.getWindow().handle();
        boolean wDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean aDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean sDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean dDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        boolean spaceDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean lmbDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmbDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        int lmbCps = getCps(LEFT_CLICKS);
        int rmbCps = getCps(RIGHT_CLICKS);

        int glowColor = accent.get().colorAt(0.5, System.currentTimeMillis());

        double curY = y;

        drawKey(context, x + 24, curY, 20, 20, "W", wDown, glowColor);
        curY += 23;

        drawKey(context, x, curY, 20, 20, "A", aDown, glowColor);
        drawKey(context, x + 24, curY, 20, 20, "S", sDown, glowColor);
        drawKey(context, x + 48, curY, 20, 20, "D", dDown, glowColor);
        curY += 23;

        String lmbText = showCps.get() ? lmbCps + " CPS" : "LMB";
        String rmbText = showCps.get() ? rmbCps + " CPS" : "RMB";
        drawKey(context, x, curY, 32, 18, lmbText, lmbDown, glowColor);
        drawKey(context, x + 36, curY, 32, 18, rmbText, rmbDown, glowColor);
        curY += 21;

        if (showSpace.get()) {
            drawKey(context, x, curY, 68, 12, "—", spaceDown, glowColor);
            curY += 15;
        }

        if (showReach.get() && System.currentTimeMillis() - lastReachTime < 2500) {
            String reachStr = String.format("%.2fm", lastReach);
            UiRenderer.roundedRect(context, x, curY, 68, 12, 4, 0xDD0D1410);
            double reachW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, reachStr, 6.5);
            UiRenderer.font(context, FontFace.GOLOS_SEMI, reachStr, x + (68 - reachW) / 2.0, curY + 2.5, 6.5, 0xFF4DE2FF);
        }
    }

    private static void drawKey(GuiGraphicsExtractor context, double kx, double ky, double kw, double kh,
                                String label, boolean pressed, int glowColor) {
        int bg = pressed ? ColorUtil.withAlpha(glowColor, 95) : 0xDD0C1310;
        int border = pressed ? ColorUtil.withAlpha(glowColor, 200) : 0x442C4438;
        int textColor = pressed ? 0xFFFFFFFF : 0xFFCFDCE8;

        UiRenderer.roundedRect(context, kx, ky, kw, kh, 4, bg);
        UiRenderer.roundedBorder(context, kx, ky, kw, kh, 4, 1, border);

        double textW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, label, 7.5);
        double textX = kx + (kw - textW) / 2.0;
        double textY = ky + (kh - 7.5) / 2.0 - 0.5;
        UiRenderer.font(context, FontFace.GOLOS_SEMI, label, textX, textY, 7.5, textColor);
    }
}