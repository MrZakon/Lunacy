package net.lunacy.visuals.mixin;

import net.lunacy.visuals.gui.LunacyVanillaChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only menu backgrounds, retaining every native Minecraft setting and control. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void lunacy$renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY,
                                         float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!LunacyVanillaChrome.shouldTheme(screen)) return;
        LunacyVanillaChrome.renderBackground(screen, context, mouseX, mouseY);
        ci.cancel();
    }

    /** Prevents the one-frame vanilla blur before GameMenuScreen is replaced by Lunacy's pause screen. */
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void lunacy$disableMenuBlur(GuiGraphicsExtractor context, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (LunacyVanillaChrome.shouldTheme(screen)) ci.cancel();
    }

}
