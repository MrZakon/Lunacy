package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.UUID;

/** Stable client-side fullbright that does not rely on the vanilla gamma clamp. */
public final class FullbrightModule extends Module {
    private final DoubleSetting brightness = add(new DoubleSetting(
            "gamma", "Brightness", "Vanilla gamma contribution before night vision", 1.0, 0.5, 1.0, 0.05));
    private final BooleanSetting smooth = add(new BooleanSetting(
            "smooth", "Smooth night vision", "Keep a long duration to prevent transition flicker", true));
    private MobEffectInstance ownedEffect;
    private UUID owner;
    private double previousGamma = Double.NaN;

    public FullbrightModule() {
        super("fullbright", "Fullbright / NightVision", "Reliable light without gamma clamp or flicker",
                ModuleCategory.WORLD_VISUALS);
    }

    @Override public String displayValue() { return "LIGHTMAP"; }

    @Override
    protected void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if (client.options != null) previousGamma = client.options.gamma().get();
    }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.options != null) client.options.gamma().set(brightness.get());
        if (client.player == null) {
            ownedEffect = null;
            owner = null;
            return;
        }
        if (!client.player.getUUID().equals(owner)) {
            ownedEffect = null;
            owner = client.player.getUUID();
        }

        MobEffectInstance current = client.player.getEffect(MobEffects.NIGHT_VISION);
        if (ownedEffect != null && current != ownedEffect) ownedEffect = null;
        if (current != null && ownedEffect == null) return; // Never overwrite a real server/potion effect.
        if (current == null || current.getDuration() < (smooth.get() ? 300 : 80)) {
            MobEffectInstance next = new MobEffectInstance(MobEffects.NIGHT_VISION,
                    smooth.get() ? 720 : 160, 0, true, false, false);
            client.player.addEffect(next);
            ownedEffect = client.player.getEffect(MobEffects.NIGHT_VISION);
        }
    }

    @Override
    protected void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getEffect(MobEffects.NIGHT_VISION) == ownedEffect) {
            client.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        if (client.options != null && Double.isFinite(previousGamma)) client.options.gamma().set(previousGamma);
        ownedEffect = null;
        owner = null;
        previousGamma = Double.NaN;
    }
}
