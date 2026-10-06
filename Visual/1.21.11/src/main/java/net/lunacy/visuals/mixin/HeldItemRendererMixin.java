package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.ViewModelModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ограничивает трансформацию только стеком конкретной руки. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void lunacyvisuals$pushViewModel(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                             Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                             MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                             CallbackInfo callback) {
        matrices.push();
        var runtime = ClientRuntime.getNullable();
        var inspect = runtime == null ? null : runtime.module(net.lunacy.visuals.module.impl.visual.ItemInspectModule.class);
        if (inspect != null && hand == Hand.MAIN_HAND) {
            float t = inspect.progress();
            if (t > 0) {
                float lift = (float) Math.sin(t * Math.PI);
                matrices.translate(-.12 * lift, .15 * lift, -.15 * lift);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35 * lift));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(inspect.roll(t)));
            }
        }
        ViewModelModule module = module();
        if (module != null && module.isEnabled()) {
            float handSign = hand == Hand.MAIN_HAND ? 1.0F : -1.0F;
            matrices.translate(module.offsetX() * handSign, module.offsetY(), module.offsetZ());
            matrices.scale(module.scale(), module.scale(), module.scale());
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(module.tilt() * handSign));
            float curve = (float) Math.sin(Math.sqrt(Math.clamp(swingProgress, 0.0F, 1.0F)) * Math.PI);
            switch (module.swingStyle()) {
                case VANILLA -> { }
                case SMOOTH -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18.0F * curve));
                case SWIPE -> {
                    matrices.translate(-0.08F * curve * handSign, 0.03F * curve, 0);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(24.0F * curve * handSign));
                }
                case EXHIBITION -> {
                    matrices.translate(0, -0.06F * curve, -0.08F * curve);
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-32.0F * curve * handSign));
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-16.0F * curve));
                }
            }
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void lunacyvisuals$popViewModel(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                            Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                            MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                            CallbackInfo callback) {
        matrices.pop();
    }

    private static ViewModelModule module() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? null : runtime.module(ViewModelModule.class);
    }
}
