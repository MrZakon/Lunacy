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

public final class GroundAuraModule extends Module {
    public enum Style { RUNE, PULSE, BLOOM, COMET, FOOTSTEPS, HEXAGRAM, LOTUS, VORTEX, SNOWFLAKE }

    private final EnumSetting<Style> style = add(new EnumSetting<>(
            "style", "Style", "Ground effect shape", Style.RUNE, Style.class));
    private final ColorSetting color = add(new ColorSetting(
            "color", "Color", "Ground effect gradient", new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.6)));
    private final DoubleSetting radius = add(new DoubleSetting("radius", "Radius", "Ground effect radius", 0.72, 0.3, 1.4, 0.05));
    private final DoubleSetting speed = add(new DoubleSetting("speed", "Speed", "Animation speed", 1, 0.1, 3, 0.05));
    private final DoubleSetting detail = add(new DoubleSetting("detail", "Detail", "Ground point count", 24, 10, 44, 2));
    private final BooleanSetting thirdPerson = add(new BooleanSetting(
            "third_person", "Third person only", "Hide the aura from first person", true));
    private int tick;

    public GroundAuraModule() {
        super("ground_aura", "Ground Aura", "Nine runes, flowers, vortices and movement effects",
                ModuleCategory.COSMETICS);
    }

    @Override public String displayValue() { return style.get().name(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.world == null) { tick = 0; return; }
        if (thirdPerson.get() && client.options.getPerspective().isFirstPerson()) return;
        if (!client.player.isOnGround() || ++tick % VisualQuality.cadence(3) != 0) return;
        int count = VisualQuality.particles(detail.get().intValue());
        double time = System.currentTimeMillis() / 1000.0 * speed.get();
        double movement = client.player.getVelocity().horizontalLength();
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            double a = t * Math.PI * 2 + time * 0.55;
            double r = radius.get();
            double x;
            double z;
            double y = 0.035;
            switch (style.get()) {
                case RUNE -> {
                    double notch = i % 3 == 0 ? 0.64 : 1.0;
                    x = Math.cos(a) * r * notch;
                    z = Math.sin(a) * r * notch;
                }
                case PULSE -> {
                    double pulse = 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(time * 3));
                    x = Math.cos(a) * r * pulse;
                    z = Math.sin(a) * r * pulse;
                }
                case BLOOM -> {
                    double petal = r * (0.5 + 0.5 * Math.sin(a * 5));
                    x = Math.cos(a) * petal;
                    z = Math.sin(a) * petal;
                }
                case COMET -> {
                    double tail = t * r;
                    x = Math.cos(time) * tail;
                    z = Math.sin(time) * tail;
                    y += Math.sin(t * Math.PI) * 0.08;
                }
                case FOOTSTEPS -> {
                    if (movement < 0.035) continue;
                    double yaw = Math.toRadians(client.player.getYaw());
                    double side = (i & 1) == 0 ? -0.16 : 0.16;
                    double behind = -(i / 2.0) * 0.055;
                    var point = CosmeticParticleUtil.local(0, 0, 0, yaw, side, 0, behind);
                    x = point.x();
                    z = point.z();
                }
                case HEXAGRAM -> {
                    int triangle = i & 1;
                    double stepped = Math.floor(t * 3.0) / 3.0 * Math.PI * 2.0
                            + triangle * Math.PI;
                    double edgeT = (t * 3.0) % 1.0;
                    double next = stepped + Math.PI * 2.0 / 3.0;
                    x = (Math.cos(stepped) * (1.0 - edgeT) + Math.cos(next) * edgeT) * r;
                    z = (Math.sin(stepped) * (1.0 - edgeT) + Math.sin(next) * edgeT) * r;
                }
                case LOTUS -> {
                    double petal = r * (0.32 + 0.68 * Math.abs(Math.sin(a * 4.0)));
                    x = Math.cos(a) * petal;
                    z = Math.sin(a) * petal;
                    y += Math.pow(Math.abs(Math.sin(a * 4.0)), 2.0) * 0.08;
                }
                case VORTEX -> {
                    double vr = r * (0.18 + t * 0.82);
                    double va = t * Math.PI * 7.0 - time * 0.85;
                    x = Math.cos(va) * vr;
                    z = Math.sin(va) * vr;
                    y += t * 0.18;
                }
                case SNOWFLAKE -> {
                    int arm = i % 6;
                    double armT = (i / 6.0) / Math.max(1, count / 6.0 - 1);
                    double sa = arm * Math.PI / 3.0 + time * 0.16;
                    x = Math.cos(sa) * r * armT;
                    z = Math.sin(sa) * r * armT;
                }
                default -> throw new IllegalStateException();
            }
            CosmeticParticleUtil.dust(client, color, t + time * 0.04, 0.66F,
                    client.player.getX() + x, client.player.getY() + y, client.player.getZ() + z);
        }
    }

    @Override protected void onDisable() { tick = 0; }
}
