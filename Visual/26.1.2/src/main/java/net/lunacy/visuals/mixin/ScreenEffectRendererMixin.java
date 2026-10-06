package net.lunacy.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.LowFireModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void lunacyvisuals$onRenderFireHead(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        LowFireModule mod = runtime.module(LowFireModule.class);
        if (mod != null && mod.isEnabled()) {
            if (mod.noFire()) {
                ci.cancel();
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.0f, -mod.heightOffset(), 0.0f);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void lunacyvisuals$onRenderFireReturn(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        LowFireModule mod = runtime.module(LowFireModule.class);
        if (mod != null && mod.isEnabled() && !mod.noFire()) {
            poseStack.popPose();
        }
    }
}
