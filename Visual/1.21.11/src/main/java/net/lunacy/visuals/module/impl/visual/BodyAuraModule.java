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

/** Lightweight full-body cosmetics consolidated into one configurable visual. */
public final class BodyAuraModule extends Module {
    public enum Style { ENERGY_SHELL, DOUBLE_HELIX, FLAMES, HEARTS, SHADOW, CELESTIAL }

    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Full-body aura geometry", Style.DOUBLE_HELIX, Style.class));
    private final ColorSetting color = add(new ColorSetting(
            "color", "Color", "Aura gradient",
            new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.58)));
    private final DoubleSetting radius = add(new DoubleSetting(
            "radius", "Radius", "Distance from the player", 0.52, 0.28, 1.0, 0.04));
    private final DoubleSetting speed = add(new DoubleSetting(
            "speed", "Speed", "Aura animation speed", 0.85, 0.15, 2.2, 0.05));
    private final DoubleSetting detail = add(new DoubleSetting(
            "detail", "Detail", "Number of aura points", 24, 12, 44, 2));
    private final BooleanSetting thirdPerson = add(new BooleanSetting(
            "third_person", "Third person only", "Hide the aura from first person", true));
    private int tick;

    public BodyAuraModule() {
        super("body_aura", "Body Aura", "Energy shell, helix, flames, hearts, shadow and celestial rings",
                ModuleCategory.COSMETICS);
    }

    @Override public String displayValue() { return style.get().name(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.world == null) { tick = 0; return; }
        if (thirdPerson.get() && client.options.getPerspective().isFirstPerson()) return;
        if (++tick % VisualQuality.cadence(3) != 0) return;

        int count = VisualQuality.particles(detail.get().intValue());
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        for (int index = 0; index < count; index++) {
            double t = index / (double) Math.max(1, count - 1);
            AuraPoint point = point(style.get(), t, index, count, time, radius.get());
            CosmeticParticleUtil.dust(client, color, t + time * 0.035, point.size(),
                    client.player.getX() + point.x(), client.player.getY() + point.y(),
                    client.player.getZ() + point.z());
        }
    }

    private static AuraPoint point(Style style, double t, int index, int count,
                                   double time, double radius) {
        double a = t * Math.PI * 2.0 + time;
        return switch (style) {
            case ENERGY_SHELL -> {
                double latitude = Math.asin(-1.0 + 2.0 * t);
                double longitude = index * 2.3999632297 + time * 0.42;
                double cos = Math.cos(latitude);
                yield new AuraPoint(Math.cos(longitude) * cos * radius,
                        0.95 + Math.sin(latitude) * 0.96,
                        Math.sin(longitude) * cos * radius, 0.58F);
            }
            case DOUBLE_HELIX -> {
                int strand = index & 1;
                double ht = (index / 2.0) / Math.max(1, count / 2.0 - 1);
                double angle = ht * Math.PI * 5.0 + time + strand * Math.PI;
                yield new AuraPoint(Math.cos(angle) * radius, 0.08 + ht * 1.82,
                        Math.sin(angle) * radius, 0.62F);
            }
            case FLAMES -> {
                double lane = (index % 7) / 6.0;
                double height = ((index * 5) % Math.max(2, count)) / (double) count;
                double angle = lane * Math.PI * 2.0 + time * 0.35;
                double taper = radius * (1.0 - height * 0.72);
                yield new AuraPoint(Math.cos(angle) * taper + Math.sin(time * 3 + index) * 0.05,
                        0.05 + height * 1.75,
                        Math.sin(angle) * taper, 0.72F);
            }
            case HEARTS -> {
                double heart = t * Math.PI * 2.0;
                double x = Math.pow(Math.sin(heart), 3) * radius;
                double y = (13 * Math.cos(heart) - 5 * Math.cos(heart * 2)
                        - 2 * Math.cos(heart * 3) - Math.cos(heart * 4)) / 17.0;
                double spin = time * 0.32;
                yield new AuraPoint(x * Math.cos(spin), 1.02 + y * radius,
                        x * Math.sin(spin), 0.64F);
            }
            case SHADOW -> {
                double spiral = t * Math.PI * 5.0 - time * 0.55;
                double spread = radius * (0.30 + t * 0.75);
                yield new AuraPoint(Math.cos(spiral) * spread, 0.03 + t * 0.82,
                        Math.sin(spiral) * spread, 0.70F);
            }
            case CELESTIAL -> {
                int ring = index % 3;
                double rt = (index / 3.0) / Math.max(1, count / 3.0 - 1);
                double angle = rt * Math.PI * 2.0 + time * (0.32 + ring * 0.16);
                double ringRadius = radius * (0.75 + ring * 0.16);
                yield new AuraPoint(Math.cos(angle) * ringRadius,
                        0.38 + ring * 0.56 + Math.sin(angle * 2.0) * 0.06,
                        Math.sin(angle) * ringRadius, 0.60F);
            }
        };
    }

    @Override protected void onDisable() { tick = 0; }

    private record AuraPoint(double x, double y, double z, float size) {}
}
