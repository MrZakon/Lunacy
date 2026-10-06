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

public final class OrbitingCosmeticsModule extends Module {
    public enum Style { ORBS, SPIRAL, RUNES, ATOM, CONSTELLATION, DOUBLE_HELIX, SATELLITES, HEART, CYBER_GRID }

    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Orbit geometry", Style.ATOM, Style.class));
    private final ColorSetting color = add(new ColorSetting(
            "color", "Color", "Orbit gradient", new ColorValue(0xFF5CE1E6, 0xFFFF67CE, false, 0.8)));
    private final DoubleSetting radius = add(new DoubleSetting("radius", "Radius", "Orbit radius", 0.78, 0.4, 1.5, 0.05));
    private final DoubleSetting speed = add(new DoubleSetting("speed", "Speed", "Orbit speed", 1, 0.1, 3, 0.05));
    private final DoubleSetting detail = add(new DoubleSetting("detail", "Detail", "Orbit point count", 26, 10, 48, 2));
    private final BooleanSetting thirdPerson = add(new BooleanSetting(
            "third_person", "Third person only", "Hide the orbit from first person", true));
    private int tick;

    public OrbitingCosmeticsModule() {
        super("orbiting_cosmetics", "Orbiting Cosmetics", "Nine smooth orbital effects around the player",
                ModuleCategory.COSMETICS);
    }

    @Override public String displayValue() { return style.get().name(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.level == null) { tick = 0; return; }
        if (thirdPerson.get() && client.options.getCameraType().isFirstPerson()) return;
        if (++tick % VisualQuality.cadence(2) != 0) return;
        int count = VisualQuality.particles(detail.get().intValue());
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            double a = t * Math.PI * 2 + time;
            double x;
            double y;
            double z;
            switch (style.get()) {
                case ORBS -> {
                    int orb = i % 4;
                    double oa = time * (0.7 + orb * 0.13) + orb * Math.PI * 0.5;
                    double jitter = Math.sin(t * Math.PI * 8) * 0.08;
                    x = Math.cos(oa) * (radius.get() + jitter);
                    z = Math.sin(oa) * (radius.get() + jitter);
                    y = 0.38 + orb * 0.35 + Math.sin(time * 2 + orb) * 0.08;
                }
                case SPIRAL -> {
                    x = Math.cos(a * 2.5) * radius.get();
                    z = Math.sin(a * 2.5) * radius.get();
                    y = 0.1 + t * 1.75;
                }
                case RUNES -> {
                    double stepped = Math.floor(t * 12) / 12.0;
                    double ra = stepped * Math.PI * 2 + time * 0.45;
                    x = Math.cos(ra) * radius.get();
                    z = Math.sin(ra) * radius.get();
                    y = 0.08 + ((i & 1) == 0 ? 0 : 0.08);
                }
                case ATOM -> {
                    int plane = i % 3;
                    double aa = (i / 3.0) / Math.max(1, count / 3.0) * Math.PI * 2 + time;
                    x = Math.cos(aa) * radius.get();
                    z = Math.sin(aa) * radius.get() * Math.cos((plane - 1) * 0.85);
                    y = 0.95 + Math.sin(aa) * radius.get() * Math.sin((plane - 1) * 0.85);
                }
                case CONSTELLATION -> {
                    double band = (i % 5) / 4.0;
                    x = Math.cos(a + band) * radius.get() * (0.75 + band * 0.3);
                    z = Math.sin(a + band) * radius.get() * (0.75 + band * 0.3);
                    y = 0.25 + ((i * 7) % Math.max(2, count)) / (double) count * 1.55;
                }
                case DOUBLE_HELIX -> {
                    int strand = i & 1;
                    double ht = (i / 2.0) / Math.max(1, count / 2.0 - 1);
                    double ha = ht * Math.PI * 5.0 + time + strand * Math.PI;
                    x = Math.cos(ha) * radius.get();
                    z = Math.sin(ha) * radius.get();
                    y = 0.08 + ht * 1.82;
                }
                case SATELLITES -> {
                    int satellite = i % 5;
                    double orbit = time * (0.36 + satellite * 0.12) + satellite * Math.PI * 0.4;
                    double rr = radius.get() * (0.68 + satellite * 0.10);
                    x = Math.cos(orbit) * rr;
                    z = Math.sin(orbit) * rr;
                    y = 0.36 + satellite * 0.31 + Math.sin(orbit * 2.0) * 0.08;
                }
                case HEART -> {
                    double heart = t * Math.PI * 2.0;
                    double hx = Math.pow(Math.sin(heart), 3) * radius.get();
                    double hy = (13 * Math.cos(heart) - 5 * Math.cos(heart * 2)
                            - 2 * Math.cos(heart * 3) - Math.cos(heart * 4)) / 17.0;
                    x = hx * Math.cos(time * 0.28);
                    z = hx * Math.sin(time * 0.28);
                    y = 1.0 + hy * radius.get();
                }
                case CYBER_GRID -> {
                    int ring = i % 4;
                    double gt = (i / 4.0) / Math.max(1, count / 4.0 - 1);
                    double ga = Math.floor(gt * 8.0) / 8.0 * Math.PI * 2.0 + time * 0.32;
                    x = Math.cos(ga) * radius.get() * (0.72 + ring * 0.08);
                    z = Math.sin(ga) * radius.get() * (0.72 + ring * 0.08);
                    y = 0.18 + ring * 0.48;
                }
                default -> throw new IllegalStateException();
            }
            CosmeticParticleUtil.dust(client, color, t + time * 0.05, 0.62F,
                    client.player.getX() + x, client.player.getY() + y, client.player.getZ() + z);
        }
    }

    @Override protected void onDisable() { tick = 0; }
}
