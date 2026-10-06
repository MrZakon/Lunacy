package net.lunacy.visuals.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Add to lunacyvisuals.mixins.json client: "WindowMixin"
 *
 * Vanilla Auto GUI scale on 1080p becomes 4x (480x270 GUI pixels).
 * Russian onboarding labels + icon buttons overflow. Cap Auto so the
 * GUI surface stays at least 700x400.
 */
@Mixin(Window.class)
public class WindowMixin {
    private static final int MIN_GUI_WIDTH = 700;
    private static final int MIN_GUI_HEIGHT = 400;

    @Inject(method = "calculateScale", at = @At("RETURN"), cancellable = true)
    private void lunacyvisuals$readableAutoScale(int guiScale, boolean forceUnicode, CallbackInfoReturnable<Integer> cir) {
        if (guiScale != 0) {
            return;
        }
        Window window = (Window) (Object) this;
        int scale = cir.getReturnValueI();
        if (scale <= 1) {
            return;
        }
        int fbW = window.getWidth();
        int fbH = window.getHeight();
        if (fbW <= 0 || fbH <= 0) {
            return;
        }
        while (scale > 1 && (fbW / scale < MIN_GUI_WIDTH || fbH / scale < MIN_GUI_HEIGHT)) {
            scale--;
        }
        cir.setReturnValue(scale);
    }
}
