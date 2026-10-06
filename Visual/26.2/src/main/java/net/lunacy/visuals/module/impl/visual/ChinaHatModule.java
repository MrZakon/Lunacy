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

/** Smooth head cosmetics with a real China-hat silhouette as the primary style. */
public final class ChinaHatModule extends Module {
    public enum Style {
        CHINA_HAT, HALO, DOUBLE_HALO, CROWN, WITCH_HAT, ORBITAL,
        HORNS, CAT_EARS, ANTLERS, SAMURAI, SAKURA_CROWN, BUNNY_EARS,
        CELESTIAL, DEMON_CROWN
    }

    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Head cosmetic silhouette", Style.CHINA_HAT, Style.class));
    private final ColorSetting color = add(new ColorSetting(
            "color", "Color", "Animated cosmetic gradient",
            new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.7)));
    private final DoubleSetting radius = add(new DoubleSetting(
            "radius", "Radius", "Cosmetic radius", 0.48, 0.25, 0.85, 0.05));
    private final DoubleSetting height = add(new DoubleSetting(
            "height", "Height", "Offset above the head", 0.22, 0.05, 0.65, 0.05));
    private final DoubleSetting detail = add(new DoubleSetting(
            "detail", "Detail", "Number of points in the silhouette", 42, 20, 64, 2));
    private final DoubleSetting speed = add(new DoubleSetting(
            "speed", "Speed", "Rotation and breathing speed", 1, 0.15, 3, 0.05));
    private final BooleanSetting thirdPerson = add(new BooleanSetting(
            "third_person", "Third person only", "Hide the cosmetic from first person", true));
    private int tick;

    public ChinaHatModule() {
        super("china_hat", "China Hat & Head Cosmetics", "Fourteen smooth hats, halos, crowns, ears and horns",
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
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        double yaw = Math.toRadians(client.player.getYRot());
        double baseY = client.player.getY() + client.player.getBbHeight() + height.get();
        for (int index = 0; index < points; index++) {
            double t = index / (double) Math.max(1, points - 1);
            HeadPoint point = point(style.get(), t, index, points, time, radius.get());
            var world = CosmeticParticleUtil.local(client.player.getX(), baseY, client.player.getZ(), yaw,
                    point.right(), point.up(), point.forward());
            CosmeticParticleUtil.dust(client, color, t + time * 0.04, 0.72F,
                    world.x(), world.y(), world.z());
        }
    }

    private static HeadPoint point(Style style, double t, int index, int count,
                                   double time, double radius) {
        double a = t * Math.PI * 2.0 + time;
        return switch (style) {
            case CHINA_HAT -> layeredHat(index, count, time, radius, false);
            case HALO -> new HeadPoint(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            case DOUBLE_HALO -> {
                int ring = index & 1;
                double rt = (index / 2.0) / Math.max(1, count / 2.0 - 1);
                double ra = rt * Math.PI * 2.0 + time * (ring == 0 ? 1 : -0.75);
                double tilt = ring == 0 ? 0.15 : -0.15;
                yield new HeadPoint(Math.cos(ra) * radius,
                        Math.sin(ra) * tilt + (ring == 0 ? 0.02 : 0.13),
                        Math.sin(ra) * radius * 0.92);
            }
            case CROWN -> {
                double spike = Math.pow(Math.max(0, Math.sin(a * 5.0)), 3) * radius * 0.68;
                yield new HeadPoint(Math.cos(a) * radius, 0.02 + spike, Math.sin(a) * radius);
            }
            case WITCH_HAT -> {
                double spiral = t * Math.PI * 8.0 + time * 0.65;
                double taper = radius * (1.0 - t * 0.82);
                yield new HeadPoint(Math.cos(spiral) * taper, t * radius * 1.35,
                        Math.sin(spiral) * taper);
            }
            case ORBITAL -> {
                int ring = index % 3;
                double rt = (index / 3.0) / Math.max(1, count / 3.0 - 1);
                double ra = rt * Math.PI * 2.0 + time * (0.55 + ring * 0.2);
                double x = Math.cos(ra) * radius;
                double z = Math.sin(ra) * radius;
                double tilt = (ring - 1) * 0.72;
                yield new HeadPoint(x, Math.sin(ra) * radius * tilt,
                        z * Math.cos(tilt));
            }
            case HORNS -> horn(t, radius, false);
            case CAT_EARS -> catEar(t, radius);
            case ANTLERS -> horn(t, radius, true);
            case SAMURAI -> layeredHat(index, count, time * 0.35, radius, true);
            case SAKURA_CROWN -> {
                double petal = 0.56 + 0.34 * Math.pow(Math.abs(Math.sin(a * 5.0)), 1.6);
                yield new HeadPoint(Math.cos(a) * radius * petal,
                        0.05 + Math.pow(Math.max(0, Math.sin(a * 5.0)), 2.0) * radius * 0.46,
                        Math.sin(a) * radius * petal);
            }
            case BUNNY_EARS -> bunnyEar(t, radius);
            case CELESTIAL -> {
                int ring = index % 4;
                double rt = (index / 4.0) / Math.max(1, count / 4.0 - 1);
                double ra = rt * Math.PI * 2.0 + time * (0.30 + ring * 0.11);
                double rr = radius * (0.58 + ring * 0.12);
                double tilt = (ring - 1.5) * 0.34;
                yield new HeadPoint(Math.cos(ra) * rr,
                        0.08 + Math.sin(ra) * rr * tilt,
                        Math.sin(ra) * rr * Math.cos(tilt));
            }
            case DEMON_CROWN -> {
                HeadPoint base = horn(t, radius, false);
                double curl = Math.sin(t * Math.PI * 6.0) * radius * 0.10;
                yield new HeadPoint(base.right() + curl, base.up() + Math.abs(curl), base.forward());
            }
        };
    }

    private static HeadPoint layeredHat(int index, int count, double time, double radius, boolean samurai) {
        int band = index % 4;
        double ringT = (index / 4.0) / Math.max(1, count / 4.0 - 1);
        double a = ringT * Math.PI * 2.0 + time * (samurai ? 0.12 : 0.24);
        double level = band / 3.0;
        double brim = samurai ? 1.12 : 1.0;
        double ringRadius = radius * (brim - level * (samurai ? 0.58 : 0.78));
        double crown = level * radius * (samurai ? 0.68 : 0.92);
        double sweep = samurai ? Math.cos(a * 2.0) * radius * 0.08 * (1.0 - level) : 0;
        return new HeadPoint(Math.cos(a) * ringRadius,
                crown + sweep,
                Math.sin(a) * ringRadius * (samurai ? 0.72 : 0.94));
    }

    private static HeadPoint horn(double t, double radius, boolean branched) {
        double half = t < 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
        double side = t < 0.5 ? -1 : 1;
        double branch = branched ? Math.sin(half * Math.PI * 5.0) * 0.07 * half : 0;
        return new HeadPoint(side * (0.16 + half * radius * 0.72 + branch),
                0.04 + half * radius * (branched ? 1.05 : 0.82) - half * half * 0.15,
                -0.04 - half * radius * 0.38 + (branched ? Math.abs(branch) : 0));
    }

    private static HeadPoint catEar(double t, double radius) {
        double earT = t < 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
        double side = t < 0.5 ? -1 : 1;
        double segment = earT * 3.0;
        double horizontal;
        double vertical;
        if (segment < 1) { horizontal = segment * 0.16; vertical = segment * 0.32; }
        else if (segment < 2) { horizontal = 0.16 - (segment - 1) * 0.32; vertical = 0.32 - (segment - 1) * 0.32; }
        else { horizontal = -0.16 + (segment - 2) * 0.16; vertical = 0; }
        return new HeadPoint(side * (radius * 0.42 + horizontal), vertical, -radius * 0.08);
    }

    private static HeadPoint bunnyEar(double t, double radius) {
        double earT = t < 0.5 ? t * 2.0 : (t - 0.5) * 2.0;
        double side = t < 0.5 ? -1 : 1;
        double loop = earT * Math.PI * 2.0;
        return new HeadPoint(side * radius * 0.36 + Math.sin(loop) * radius * 0.13,
                radius * 0.42 + (0.5 - 0.5 * Math.cos(loop)) * radius * 1.05,
                -radius * 0.08 + Math.cos(loop) * radius * 0.05);
    }

    @Override protected void onDisable() { tick = 0; }

    private record HeadPoint(double right, double up, double forward) {}
}
