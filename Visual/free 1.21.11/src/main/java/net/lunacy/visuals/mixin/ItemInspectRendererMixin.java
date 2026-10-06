package net.lunacy.visuals.mixin;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.ItemInspectModule;
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
@Mixin(HeldItemRenderer.class)
public abstract class ItemInspectRendererMixin {
 @Inject(method="renderFirstPersonItem",at=@At("HEAD")) private void lunacy$push(AbstractClientPlayerEntity player,float tickDelta,float pitch,Hand hand,float swing,ItemStack item,float equip,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){matrices.push();var r=ClientRuntime.getNullable();ItemInspectModule inspect=r==null?null:r.module(ItemInspectModule.class);if(inspect!=null&&hand==Hand.MAIN_HAND){float t=inspect.progress();if(t>0){float lift=(float)Math.sin(t*Math.PI);matrices.translate(-.12*lift,.15*lift,-.15*lift);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35*lift));matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(inspect.roll(t)));}}}
 @Inject(method="renderFirstPersonItem",at=@At("RETURN")) private void lunacy$pop(AbstractClientPlayerEntity player,float tickDelta,float pitch,Hand hand,float swing,ItemStack item,float equip,MatrixStack matrices,OrderedRenderCommandQueue queue,int light,CallbackInfo ci){matrices.pop();}
}
