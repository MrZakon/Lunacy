package net.lunacy.visuals.module;

import net.lunacy.visuals.module.impl.hud.*;
import net.lunacy.visuals.module.impl.visual.*;

/** Единственная декларация визуальных модулей клиента. */
public final class ModuleRegistry {
    private ModuleRegistry() {}

    public static void registerAll(ModuleManager modules) {
        modules.register(new GhostAfterimagesModule());
        modules.register(new SmartHudModule());
        modules.register(new AdaptiveEffectsModule());
        modules.register(new ItemInspectModule());
        modules.register(new WeaponTrailsModule());
        modules.register(new LandingEffectsModule());
        modules.register(new BiomeAtmosphereModule());
        // HUD
        modules.register(new AnimatedHotbarModule());
        modules.register(new EnhancedTooltipsModule());
        modules.register(new WatermarkModule());
        modules.register(new ArmorHudModule());
        modules.register(new CustomCrosshairModule());
        modules.register(new PotionHudModule());
        modules.register(new KeystrokesModule());
        modules.register(new TotemCounterModule());
        modules.register(new DamageIndicatorsModule());

        // Visuals & Combat FX
        modules.register(new ViewModelModule());
        modules.register(new MotionTrailsModule());
        modules.register(new ChinaHatModule());
        modules.register(new JumpHitParticlesModule());
        modules.register(new AmbienceModule());
        modules.register(new FullbrightModule());
        modules.register(new LowFireModule());
        modules.register(new HitColorModule());
        modules.register(new TotemPopModule());
        modules.register(new DeathEffectsModule());
        modules.register(new ProjectilePredictionModule());
        modules.register(new ItemGlintOverlayModule());
        modules.register(new NametagsModule());
        modules.register(new CapeWingsModule());
        modules.register(new OrbitingCosmeticsModule());
        modules.register(new CompanionSpiritModule());
        modules.register(new GroundAuraModule());
        modules.register(new BodyAuraModule());
        modules.register(new ItemScrollerModule());
    }
}
