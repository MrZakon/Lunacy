package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Мышь и колесо публикуются как отменяемые client-only события. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$onMouseButton(long window, MouseInput input, int action, CallbackInfo callback) {
        if (action == 0) {
            net.lunacy.visuals.hud.HudEditorHandler.onMouseRelease();
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseButton(input.button(), action, input.modifiers())) callback.cancel();
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseScroll(horizontal, vertical)) callback.cancel();
    }
}
