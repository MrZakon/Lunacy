package net.lunacy.visuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.ViewModelModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ограничивает трансформацию только стеком конкретной руки. */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void lunacyvisuals$pushViewModel(AbstractClientPlayer player, float tickDelta, float pitch,
                                             InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
                                             PoseStack matrices, SubmitNodeCollector queue, int light,
                                             CallbackInfo callback) {
        matrices.pushPose();
        var runtime = ClientRuntime.getNullable();
        var inspect = runtime == null ? null : runtime.module(net.lunacy.visuals.module.impl.visual.ItemInspectModule.class);
        if (inspect != null && hand == InteractionHand.MAIN_HAND) {
            float t = inspect.progress();
            if (t > 0) {
                float lift = (float) Math.sin(t * Math.PI);
                matrices.translate(-.12 * lift, .15 * lift, -.15 * lift);
                matrices.mulPose(Axis.YP.rotationDegrees(35 * lift));
                matrices.mulPose(Axis.ZP.rotationDegrees(inspect.roll(t)));
            }
        }
        ViewModelModule module = module();
        if (module != null && module.isEnabled()) {
            float handSign = hand == InteractionHand.MAIN_HAND ? 1.0F : -1.0F;
            matrices.translate(module.offsetX() * handSign, module.offsetY(), module.offsetZ());
            matrices.scale(module.scale(), module.scale(), module.scale());
            matrices.mulPose(Axis.ZP.rotationDegrees(module.tilt() * handSign));
            float curve = (float) Math.sin(Math.sqrt(Math.clamp(swingProgress, 0.0F, 1.0F)) * Math.PI);
            switch (module.swingStyle()) {
                case VANILLA -> { }
                case SMOOTH -> matrices.mulPose(Axis.XP.rotationDegrees(-18.0F * curve));
                case SWIPE -> {
                    matrices.translate(-0.08F * curve * handSign, 0.03F * curve, 0);
                    matrices.mulPose(Axis.YP.rotationDegrees(24.0F * curve * handSign));
                }
                case EXHIBITION -> {
                    matrices.translate(0, -0.06F * curve, -0.08F * curve);
                    matrices.mulPose(Axis.ZP.rotationDegrees(-32.0F * curve * handSign));
                    matrices.mulPose(Axis.XP.rotationDegrees(-16.0F * curve));
                }
            }
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void lunacyvisuals$popViewModel(AbstractClientPlayer player, float tickDelta, float pitch,
                                            InteractionHand hand, float swingProgress, ItemStack item, float equipProgress,
                                            PoseStack matrices, SubmitNodeCollector queue, int light,
                                            CallbackInfo callback) {
        matrices.popPose();
    }

    private static ViewModelModule module() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? null : runtime.module(ViewModelModule.class);
    }
}
