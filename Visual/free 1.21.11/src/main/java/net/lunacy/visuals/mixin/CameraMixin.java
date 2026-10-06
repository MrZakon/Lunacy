package net.lunacy.visuals.mixin;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.PhotoModeModule;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Inject(method="setRotation",at=@At("RETURN")) private void lunacy$photoRoll(float yaw,float pitch,CallbackInfo ci){
        var runtime=ClientRuntime.getNullable();var photo=runtime==null?null:runtime.module(PhotoModeModule.class);
        if(photo!=null&&photo.active())rotation.rotateZ((float)Math.toRadians(photo.roll()));
    }
}
