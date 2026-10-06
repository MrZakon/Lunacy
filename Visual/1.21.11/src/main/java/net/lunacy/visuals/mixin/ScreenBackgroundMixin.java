package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only menu backgrounds, retaining every native Minecraft setting and control. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void lunacy$renderBackground(DrawContext context, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!LunacyVanillaChrome.shouldTheme(screen)) return;
        LunacyVanillaChrome.renderBackground(screen, context, mouseX, mouseY);
        ci.cancel();
    }

    /** Prevents the one-frame vanilla blur before GameMenuScreen is replaced by Lunacy's pause screen. */
    @Inject(method = "applyBlur", at = @At("HEAD"), cancellable = true)
    private void lunacy$disableMenuBlur(DrawContext context, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (LunacyVanillaChrome.shouldTheme(screen)) ci.cancel();
    }

}
