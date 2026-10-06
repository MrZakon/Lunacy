package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.HitColorModule;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getMixColor", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$damageColor(LivingEntityRenderState state, CallbackInfoReturnable<Integer> callback) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        HitColorModule module = runtime == null ? null : runtime.module(HitColorModule.class);
        if (module != null && module.isEnabled() && state.hurt) {
            callback.setReturnValue(module.mixColor());
        }
    }
}
