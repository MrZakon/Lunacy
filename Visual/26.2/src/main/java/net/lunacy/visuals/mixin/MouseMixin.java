package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Мышь и колесо публикуются как отменяемые client-only события. */
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo callback) {
        if (action == 0) {
            net.lunacy.visuals.hud.HudEditorHandler.onMouseRelease();
        }
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseButton(input.button(), action, input.modifiers())) callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lunacyvisuals$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.onMouseScroll(horizontal, vertical)) callback.cancel();
    }
}
