package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.LowFireModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireHead(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        LowFireModule mod = runtime.module(LowFireModule.class);
        if (mod != null && mod.isEnabled()) {
            if (mod.noFire()) {
                ci.cancel();
                return;
            }
            matrices.push();
            matrices.translate(0.0f, -mod.heightOffset(), 0.0f);
        }
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void onRenderFireReturn(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        LowFireModule mod = runtime.module(LowFireModule.class);
        if (mod != null && mod.isEnabled() && !mod.noFire()) {
            matrices.pop();
        }
    }
}