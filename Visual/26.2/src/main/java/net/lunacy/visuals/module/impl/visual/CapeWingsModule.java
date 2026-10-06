package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.VisualQuality;

/** Back cosmetics with separate geometry for every style. */
public final class CapeWingsModule extends Module {
    public enum Style {
        CAPE, ANGEL, DRAGON, FAIRY, PHOENIX, NEBULA, TAILS, AURA,
        BUTTERFLY, CYBER, SERAPH, KITSUNE
    }

    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Back cosmetic silhouette", Style.ANGEL, Style.class));
    private final ColorSetting color = add(new ColorSetting(
            "color", "Color", "Animated wing and cape gradient",
            new ColorValue(0xFF5CE1E6, 0xFFA66BFF, false, 0.75)));
    private final DoubleSetting scale = add(new DoubleSetting("scale", "Scale", "Cosmetic size", 1, 0.5, 2, 0.05));
    private final DoubleSetting motion = add(new DoubleSetting(
            "motion", "Motion", "Flap and breathing amplitude", 0.25, 0, 1, 0.05));
    private final DoubleSetting detail = add(new DoubleSetting(
            "detail", "Detail", "Number of silhouette points", 34, 16, 60, 2));
    private final BooleanSetting thirdPerson = add(new BooleanSetting(
            "third_person", "Third person only", "Hide the cosmetic from first person", true));
    private int tick;

    public CapeWingsModule() {
        super("cape_wings", "Back Cosmetics", "Twelve capes, wings, tails and aura silhouettes",
                ModuleCategory.COSMETICS);
    }

    @Override public String displayValue() { return style.get().name(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.level == null) { tick = 0; return; }
        if (thirdPerson.get() && client.options.getCameraType().isFirstPerson()) return;
        if (++tick % VisualQuality.cadence(2) != 0) return;

        int points = VisualQuality.particles(detail.get().intValue());
        double time = System.currentTimeMillis() / 1000.0;
        double yaw = Math.toRadians(client.player.getYRot());
        for (int index = 0; index < points; index++) {
            double t = index / (double) Math.max(1, points - 1);
            BackPoint point = point(style.get(), t, index, points, time, scale.get(), motion.get());
            var world = CosmeticParticleUtil.local(client.player.getX(), client.player.getY(), client.player.getZ(), yaw,
                    point.right(), point.up(), point.forward());
            CosmeticParticleUtil.dust(client, color, t + time * 0.035, point.size(),
                    world.x(), world.y(), world.z());
        }
    }

    private static BackPoint point(Style style, double t, int index, int count,
                                   double time, double scale, double motion) {
        double flap = Math.sin(time * 4.2) * motion;
        if (style == Style.AURA) {
            double a = t * Math.PI * 2.0 + time * 0.7;
            return new BackPoint(Math.cos(a) * 0.62 * scale,
                    0.92 + Math.sin(a) * 0.76 * scale,
                    -0.08 + Math.sin(a * 2) * 0.08, 0.62F);
        }
        if (style == Style.NEBULA) {
            double a = t * Math.PI * 6.0 + time * 0.6;
            double radius = (0.18 + t * 0.72) * scale;
            return new BackPoint(Math.cos(a) * radius, 0.38 + t * 1.2,
                    -0.28 - Math.sin(a) * radius * 0.35, 0.72F);
        }
        if (style == Style.CAPE) {
            int columns = 5;
            int column = index % columns;
            double row = (index / columns) / (double) Math.max(1, count / columns);
            return new BackPoint((column - 2) * 0.12 * scale, 1.48 - row * 1.28 * scale,
                    -0.18 - row * (0.16 + flap * 0.08), 0.76F);
        }
        if (style == Style.TAILS || style == Style.KITSUNE) {
            int tails = style == Style.KITSUNE ? 5 : 3;
            int tail = index % tails;
            double tt = (index / (double) tails) / Math.max(1, count / (double) tails - 1);
            double side = (tail - (tails - 1) * 0.5) * (style == Style.KITSUNE ? 0.19 : 0.28);
            return new BackPoint((side + Math.sin(tt * Math.PI * 2 + time * 2 + tail) * 0.18) * scale,
                    0.82 - tt * 0.76 + Math.sin(tt * Math.PI) * (style == Style.KITSUNE ? 0.34 : 0.22),
                    -0.22 - tt * (style == Style.KITSUNE ? 0.92 : 0.72) * scale, 0.70F);
        }

        double wingT = t < 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
        double side = t < 0.5 ? -1 : 1;
        double reach;
        double up;
        double back;
        float size = 0.72F;
        switch (style) {
            case ANGEL -> {
                reach = (0.18 + Math.sin(wingT * Math.PI * 0.78) * 0.96 + flap * wingT * 0.22) * scale;
                up = 1.34 - wingT * 0.48 + Math.sin(wingT * Math.PI) * 0.38;
                back = -0.20 - wingT * 0.12;
            }
            case DRAGON -> {
                reach = (0.16 + wingT * 1.05 + flap * wingT * 0.16) * scale;
                up = 1.48 - wingT * 0.78 + Math.sin(wingT * Math.PI * 3) * 0.12;
                back = -0.18 - wingT * 0.25;
                size = 0.78F;
            }
            case FAIRY -> {
                double lobe = Math.sin(wingT * Math.PI * 2.0);
                reach = (0.25 + Math.abs(lobe) * 0.72) * scale;
                up = 1.02 + lobe * 0.54 + Math.sin(time * 5) * 0.03;
                back = -0.17 - wingT * 0.09;
                size = 0.58F;
            }
            case PHOENIX -> {
                reach = (0.16 + Math.sin(wingT * Math.PI) * 1.08) * scale;
                up = 1.50 - wingT * 1.18 + Math.sin(wingT * Math.PI) * 0.36;
                back = -0.22 - wingT * 0.38;
                size = 0.82F;
            }
            case BUTTERFLY -> {
                double lobe = Math.sin(wingT * Math.PI * 2.0);
                reach = (0.22 + Math.abs(lobe) * 0.94 + flap * 0.08) * scale;
                up = 1.04 + lobe * 0.68 - wingT * 0.10;
                back = -0.16 - Math.abs(lobe) * 0.08;
                size = 0.62F;
            }
            case CYBER -> {
                double step = Math.floor(wingT * 5.0) / 5.0;
                reach = (0.18 + step * 1.16 + flap * step * 0.06) * scale;
                up = 1.48 - step * 0.83 + ((int) (wingT * 5) % 2 == 0 ? 0.12 : -0.08);
                back = -0.19 - step * 0.28;
                size = 0.66F;
            }
            case SERAPH -> {
                int lobeIndex = index % 3;
                double lobe = Math.sin(wingT * Math.PI);
                reach = (0.18 + lobe * (0.72 + lobeIndex * 0.18) + flap * 0.10) * scale;
                up = 1.54 - wingT * 0.64 - lobeIndex * 0.24 + lobe * 0.26;
                back = -0.18 - lobeIndex * 0.10;
                size = 0.64F;
            }
            default -> throw new IllegalStateException("Unexpected back style " + style);
        }
        return new BackPoint(side * reach, up, back, size);
    }

    @Override protected void onDisable() { tick = 0; }

    private record BackPoint(double right, double up, double forward, float size) {}
}
