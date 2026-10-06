package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.hud.HudAnchor;
import net.lunacy.visuals.hud.HudPosition;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Modern glass HUD displaying active status effects and item cooldowns.
 */
public final class PotionHudModule extends HudModule {
    private final BooleanSetting showCooldowns = add(new BooleanSetting(
            "cooldowns", "Item Cooldowns", "Show ender pearl and golden apple cooldowns", true));
    private final ColorSetting accent = add(new ColorSetting(
            "accent", "Accent Color", "Widget highlight accent",
            new ColorValue(0xFF5CE1E6, 0xFFB18CFF, false, 1.0)));

    private final List<HudRow> rows = new ArrayList<>();

    public PotionHudModule() {
        super("potion_hud", "Potion & Cooldown HUD", "Glass HUD widget for active potions and cooldowns",
                new HudPosition(HudAnchor.TOP_RIGHT, -12, 45, 1));
        setEnabled(true);
    }

    @Override
    protected double width() {
        double maxText = 80;
        for (HudRow row : rows) {
            double w = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, row.name + " " + row.time, 7.5);
            if (w > maxText) maxText = w;
        }
        return Math.max(105, maxText + 24);
    }

    @Override
    protected double height() {
        return Math.max(22, rows.size() * 18 + 8);
    }

    @Override
    protected void beforeRender() {
        rows.clear();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Collect status effects
        Collection<StatusEffectInstance> effects = client.player.getStatusEffects();
        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            int amp = effect.getAmplifier();
            if (amp > 0) name += " " + (amp + 1);

            int durationTicks = effect.getDuration();
            String time = formatDuration(durationTicks);
            int color = effect.getEffectType().value().getColor();
            rows.add(new HudRow(name, time, color | 0xFF000000));
        }

        // Collect cooldowns
        if (showCooldowns.get()) {
            var cooldowns = client.player.getItemCooldownManager();
            if (cooldowns.isCoolingDown(client.player.getMainHandStack())) {
                float cd = cooldowns.getCooldownProgress(client.player.getMainHandStack(), 0.0f);
                if (cd > 0.01f) {
                    rows.add(new HudRow("Holding Item", Math.round(cd * 100) + "%", 0xFF4DE2FF));
                }
            }
        }
    }

    private static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    @Override
    protected void render(DrawContext context, double x, double y, double scale, float tickDelta) {
        if (rows.isEmpty()) return;

        double w = width();
        double h = height();
        int brandColor = accent.get().colorAt(0.5, System.currentTimeMillis());

        UiRenderer.roundedRect(context, x, y, w, h, 8, 0xEE0B120F);
        UiRenderer.roundedBorder(context, x, y, w, h, 8, 1, ColorUtil.withAlpha(brandColor, 120));

        double rowY = y + 5;
        for (HudRow row : rows) {
            // Color indicator dot
            UiRenderer.roundedRect(context, x + 6, rowY + 3, 5, 5, 2.5, row.color);

            // Name
            UiRenderer.font(context, FontFace.GOLOS_SEMI, row.name, x + 16, rowY + 1.5, 7.5, 0xFFE2EBF5);

            // Duration time aligned to the right
            double timeW = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, row.time, 7.5);
            UiRenderer.font(context, FontFace.GOLOS_SEMI, row.time, x + w - timeW - 7, rowY + 1.5, 7.5, 0xFF8FA0B8);

            rowY += 18;
        }
    }

    private record HudRow(String name, String time, int color) {}
}
