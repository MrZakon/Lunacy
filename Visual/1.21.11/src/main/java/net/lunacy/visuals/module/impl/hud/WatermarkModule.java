package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.hud.BossBarLayout;
import net.lunacy.visuals.hud.HudAnchor;
import net.lunacy.visuals.hud.HudBounds;
import net.lunacy.visuals.hud.HudPosition;
import net.lunacy.visuals.mixin.BossBarHudAccessor;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class WatermarkModule extends HudModule {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final Identifier LOGO = Identifier.of(LunacyVisuals.MOD_ID, "textures/gui/lv_logo.png");
    private static final double DEFAULT_Y = 7;
    private static final double BOSS_BAR_GAP = 8;
    private static final double MAX_TEXT_WIDTH = 260;

    private final BooleanSetting fps = add(new BooleanSetting("fps", "FPS", "Show current framerate", true));
    private final BooleanSetting ping = add(new BooleanSetting("ping", "Ping", "Show network latency", true));
    private final BooleanSetting clock = add(new BooleanSetting("clock", "Clock", "Show local time", true));
    private final ColorSetting accent = add(new ColorSetting("accent", "Accent", "Brand accent",
            new ColorValue(0xFF5CE1E6, 0xFFB18CFF, false, 1.0)));

    private String normalText = "Lunacy";
    private double adaptiveY = DEFAULT_Y;

    public WatermarkModule() {
        super("watermark", "Watermark", "Brand, FPS, ping, and time tracker",
                new HudPosition(HudAnchor.TOP_CENTER, 0, 7, 1));
    }

    @Override public boolean isLocked() { return true; }

    @Override
    public HudPosition position() {
        return new HudPosition(HudAnchor.TOP_CENTER, 0, adaptiveY, 1);
    }

    @Override public void moveTo(double x, double y) {}
    @Override public void setScale(double scale) {}

    @Override
    protected double width() {
        double textW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, normalText, 8.4);
        return Math.max(132, textW + 42);
    }

    @Override
    protected double height() {
        return 28;
    }

    @Override
    protected void beforeRender() {
        MinecraftClient client = MinecraftClient.getInstance();
        adaptiveY = resolveAdaptiveY(client);

        StringBuilder value = new StringBuilder("Lunacy");
        if (fps.get() && client != null) value.append("  ·  ").append(client.getCurrentFps()).append(" FPS");
        if (ping.get() && client != null && client.player != null && client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) value.append("  ·  ").append(entry.getLatency()).append(" ms");
        }
        if (clock.get()) value.append("  ·  ").append(LocalTime.now().format(CLOCK));
        normalText = value.toString();
    }

    private static double resolveAdaptiveY(MinecraftClient client) {
        if (client == null || client.inGameHud == null) return DEFAULT_Y;
        int count = ((BossBarHudAccessor) client.inGameHud.getBossBarHud()).lunacy$getBossBars().size();
        if (count == 0) return DEFAULT_Y;

        int bottom = BossBarLayout.visibleBottom(count, client.getWindow().getScaledHeight(), false);
        return bottom <= 0 ? DEFAULT_Y : Math.max(DEFAULT_Y, bottom + BOSS_BAR_GAP);
    }

    @Override
    protected void render(DrawContext context, double x, double y, double scale, float tickDelta) {
        double w = width();
        double h = height();
        int color = accent.get().colorAt(0.25, System.currentTimeMillis());

        // Card background and anti-aliased border
        UiRenderer.roundedRect(context, x, y, w, h, 12, 0xEE0D1410);
        UiRenderer.roundedBorder(context, x, y, w, h, 12, 1, ColorUtil.withAlpha(color, 125));

        // Logo on left
        UiRenderer.texture(context, LOGO, x + 5, y + 3, 22, 22, 128, 128);

        UiRenderer.font(context, FontFace.GOLOS_SEMI, normalText, x + 32, y + 8.7, 8.4, 0xFFF4FFF8);
    }
}