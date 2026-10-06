package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.UUID;

/** Stable client-side fullbright that does not rely on the vanilla gamma clamp. */
public final class FullbrightModule extends Module {
    private final DoubleSetting brightness = add(new DoubleSetting(
            "gamma", "Brightness", "Vanilla gamma contribution before night vision", 1.0, 0.5, 1.0, 0.05));
    private final BooleanSetting smooth = add(new BooleanSetting(
            "smooth", "Smooth night vision", "Keep a long duration to prevent transition flicker", true));
    private StatusEffectInstance ownedEffect;
    private UUID owner;
    private double previousGamma = Double.NaN;

    public FullbrightModule() {
        super("fullbright", "Fullbright / NightVision", "Reliable light without gamma clamp or flicker",
                ModuleCategory.WORLD_VISUALS);
    }

    @Override public String displayValue() { return "LIGHTMAP"; }

    @Override
    protected void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) previousGamma = client.options.getGamma().getValue();
    }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        var client = event.client();
        if (client.options != null) client.options.getGamma().setValue(brightness.get());
        if (client.player == null) {
            ownedEffect = null;
            owner = null;
            return;
        }
        if (!client.player.getUuid().equals(owner)) {
            ownedEffect = null;
            owner = client.player.getUuid();
        }

        StatusEffectInstance current = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (ownedEffect != null && current != ownedEffect) ownedEffect = null;
        if (current != null && ownedEffect == null) return; // Never overwrite a real server/potion effect.
        if (current == null || current.getDuration() < (smooth.get() ? 300 : 80)) {
            StatusEffectInstance next = new StatusEffectInstance(StatusEffects.NIGHT_VISION,
                    smooth.get() ? 720 : 160, 0, true, false, false);
            client.player.addStatusEffect(next);
            ownedEffect = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.getStatusEffect(StatusEffects.NIGHT_VISION) == ownedEffect) {
            client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        if (client.options != null && Double.isFinite(previousGamma)) client.options.getGamma().setValue(previousGamma);
        ownedEffect = null;
        owner = null;
        previousGamma = Double.NaN;
    }
}
