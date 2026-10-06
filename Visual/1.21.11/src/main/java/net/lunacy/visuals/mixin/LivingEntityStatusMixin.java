package net.lunacy.visuals.mixin;

import net.lunacy.visuals.module.impl.hud.TotemCounterModule;
import net.lunacy.visuals.module.impl.visual.DeathEffectsModule;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusMixin {
    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void onHandleStatus(byte status, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (status == EntityStatuses.USE_TOTEM_OF_UNDYING) {
            TotemCounterModule.onPop(entity.getUuid(), entity.getName().getString());
        } else if (status == 3) {
            DeathEffectsModule.trigger(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
            TotemCounterModule.onDeath(entity.getUuid());
        }
    }
}