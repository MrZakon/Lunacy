package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.LowFireModule;
import net.lunacy.visuals.module.impl.visual.TotemPopModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Shadow private ItemStack floatingItem;

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

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void onRenderFloatingItem(MatrixStack matrices, float tickDelta, OrderedRenderCommandQueue queue, CallbackInfo ci) {
        if (floatingItem == null || !floatingItem.isOf(Items.TOTEM_OF_UNDYING)) return;

        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        TotemPopModule mod = runtime.module(TotemPopModule.class);
        if (mod == null || !mod.isEnabled()) return;

        if (mod.mode() == TotemPopModule.PopMode.OFF) {
            ci.cancel();
        } else if (mod.mode() == TotemPopModule.PopMode.MINIMAL) {
            matrices.scale(0.35f, 0.35f, 0.35f);
            matrices.translate(1.4f, -1.2f, 0.0f);
        }
    }
}