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

public final class CompanionSpiritModule extends Module {
    public enum Spirit { WISP, FAIRY, DRAGON, RAVEN, FOX, SLIME }
    public enum Side { LEFT, RIGHT }
    public enum Reaction { FOLLOW, SLEEP, WORRIED, CELEBRATE }

    private final EnumSetting<Spirit> style = add(new EnumSetting<>(
            "style", "Spirit", "Companion silhouette", Spirit.WISP, Spirit.class));
    private final EnumSetting<Side> side = add(new EnumSetting<>(
            "side", "Side", "Side followed by the spirit", Side.RIGHT, Side.class));
    private final ColorSetting color = add(new ColorSetting(
            "color", "Color", "Spirit gradient", new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, 0.65)));
    private final DoubleSetting distance = add(new DoubleSetting("distance", "Distance", "Follower distance", 0.82, 0.45, 1.5, 0.05));
    private final DoubleSetting scale = add(new DoubleSetting("scale", "Scale", "Spirit size", 1, 0.55, 1.8, 0.05));
    private final BooleanSetting thirdPerson = add(new BooleanSetting(
            "third_person", "Third person only", "Hide the spirit from first person", true));
    private final BooleanSetting reactions = add(new BooleanSetting(
            "reactions", "Реакции", "Спутник спит, волнуется и празднует вместе с игроком", true));
    private int tick;
    private int idleTicks;
    private int celebrateTicks;
    private Reaction reaction = Reaction.FOLLOW;

    public CompanionSpiritModule() {
        super("companion_spirit", "Companion Spirit", "Six animated particle companions that follow the player",
                ModuleCategory.COSMETICS);
    }

    @Override public String displayValue() { return reactions.get() ? reaction.name() : style.get().name(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.player == null || client.world == null) { tick = 0; return; }
        double horizontalSpeed = Math.hypot(client.player.getVelocity().x, client.player.getVelocity().z);
        idleTicks = horizontalSpeed < .008 ? idleTicks + 1 : 0;
        if (client.targetedEntity instanceof net.minecraft.entity.LivingEntity living && living.isDead()) celebrateTicks = 35;
        if (celebrateTicks > 0) celebrateTicks--;
        reaction = !reactions.get() ? Reaction.FOLLOW
                : client.player.getHealth() <= 6 ? Reaction.WORRIED
                : celebrateTicks > 0 ? Reaction.CELEBRATE
                : idleTicks > 100 ? Reaction.SLEEP : Reaction.FOLLOW;
        if (thirdPerson.get() && client.options.getPerspective().isFirstPerson()) return;
        if (++tick % VisualQuality.cadence(2) != 0) return;
        int count = VisualQuality.particles(18);
        double time = System.currentTimeMillis() / 1000.0;
        double yaw = Math.toRadians(client.player.getYaw());
        double sideSign = side.get() == Side.LEFT ? -1 : 1;
        double reactionY = reaction == Reaction.SLEEP ? -.38 : reaction == Reaction.CELEBRATE ? Math.abs(Math.sin(time * 7)) * .35 : 0;
        double reactionDistance = reaction == Reaction.WORRIED ? distance.get() * .55 : distance.get();
        var anchor = CosmeticParticleUtil.local(client.player.getX(), client.player.getY(), client.player.getZ(), yaw,
                sideSign * reactionDistance, 1.10 + Math.sin(time * (reaction == Reaction.WORRIED ? 5 : 2.4)) * 0.12 + reactionY, -0.30);
        for (int i = 0; i < count; i++) {
            double t = i / (double) Math.max(1, count - 1);
            SpiritPoint p = point(style.get(), t, i, time, scale.get());
            CosmeticParticleUtil.dust(client, color, t + time * 0.04, p.size(),
                    anchor.x() + p.x(), anchor.y() + p.y(), anchor.z() + p.z());
        }
    }

    private static SpiritPoint point(Spirit style, double t, int index, double time, double scale) {
        double a = t * Math.PI * 2;
        return switch (style) {
            case WISP -> new SpiritPoint(Math.cos(a + time * 2) * 0.16 * scale,
                    (t - 0.5) * 0.42 * scale, Math.sin(a + time * 2) * 0.16 * scale, 0.70F);
            case FAIRY -> {
                double side = index % 2 == 0 ? -1 : 1;
                double wt = (index / 2.0) / 8.0;
                yield new SpiritPoint(side * Math.sin(wt * Math.PI) * 0.32 * scale,
                        (0.5 - wt) * 0.34 * scale,
                        -Math.cos(wt * Math.PI) * 0.06, 0.56F);
            }
            case DRAGON -> new SpiritPoint(Math.sin(t * Math.PI * 2 + time * 2) * 0.24 * scale,
                    (0.5 - t) * 0.34 * scale, -t * 0.42 * scale, 0.66F);
            case RAVEN -> {
                double side = index % 2 == 0 ? -1 : 1;
                double wt = (index / 2.0) / 8.0;
                yield new SpiritPoint(side * wt * 0.34 * scale,
                        Math.sin(wt * Math.PI + time * 4) * 0.11 * scale,
                        -wt * 0.12, 0.62F);
            }
            case FOX -> {
                double segment = t * 3;
                double x = segment < 1 ? segment * 0.22 : segment < 2 ? 0.22 - (segment - 1) * 0.44 : -0.22 + (segment - 2) * 0.22;
                double y = segment < 1 ? segment * 0.25 : segment < 2 ? 0.25 - (segment - 1) * 0.25 : 0;
                yield new SpiritPoint(x * scale, y * scale, Math.sin(a) * 0.06, 0.65F);
            }
            case SLIME -> {
                double edge = t * 4;
                double x = edge < 1 ? -0.18 + edge * 0.36 : edge < 2 ? 0.18 : edge < 3 ? 0.18 - (edge - 2) * 0.36 : -0.18;
                double y = edge < 1 ? -0.14 : edge < 2 ? -0.14 + (edge - 1) * 0.28 : edge < 3 ? 0.14 : 0.14 - (edge - 3) * 0.28;
                yield new SpiritPoint(x * scale, y * scale, 0, 0.76F);
            }
        };
    }

    @Override protected void onDisable() { tick = idleTicks = celebrateTicks = 0; reaction = Reaction.FOLLOW; }

    private record SpiritPoint(double x, double y, double z, float size) {}
}
