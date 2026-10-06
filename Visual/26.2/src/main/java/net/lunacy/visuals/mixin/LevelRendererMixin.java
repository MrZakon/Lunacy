package net.lunacy.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.ItemGlintOverlayModule;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void lunacy$renderBlockGlow(PoseStack matrices, SubmitNodeCollector collector,
                                        LevelRenderState state, CallbackInfo ci) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null || state == null || state.blockOutlineRenderState == null) return;
        ItemGlintOverlayModule glow = runtime.module(ItemGlintOverlayModule.class);
        if (glow != null && glow.isEnabled()
                && glow.renderBlockOverlay(matrices, collector, state.blockOutlineRenderState)) {
            ci.cancel();
        }
    }
}
