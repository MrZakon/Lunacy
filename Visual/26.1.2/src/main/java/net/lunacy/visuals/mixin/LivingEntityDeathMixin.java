package net.lunacy.visuals.mixin;

import net.lunacy.visuals.module.impl.visual.DeathEffectsModule;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client receives entity status 3 when a living entity dies. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void lunacyvisuals$death(byte status, CallbackInfo callback) {
        if (status == 3) {
            DeathEffectsModule.trigger((LivingEntity) (Object) this);
        }
    }
}
