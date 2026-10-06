package net.lunacy.visuals.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.lunacy.visuals.config.GuiScaleMemory;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;

/**
 * Restore the last GUI Scale the player chose. Title-menu plates are authored
 * at scale 1, so a brand-new options.txt still defaults to 1 — but a saved
 * scale must survive restart instead of being forced back to 1.
 */
@Mixin(Options.class)
public class OptionsMixin {
    @Shadow
    @Final
    private OptionInstance<Integer> guiScale;

    @Unique
    private boolean lunacyvisuals$freshOptions;

    @Inject(method = "load()V", at = @At("HEAD"))
    private void lunacyvisuals$noteFreshOptions(CallbackInfo ci) {
        this.lunacyvisuals$freshOptions = !Files.isRegularFile(
                FabricLoader.getInstance().getGameDir().resolve("options.txt"));
    }

    @Inject(method = "load()V", at = @At("RETURN"))
    private void lunacyvisuals$restoreGuiScale(CallbackInfo ci) {
        int saved = GuiScaleMemory.read(FabricLoader.getInstance().getConfigDir());
        if (saved != GuiScaleMemory.UNSET) {
            if (!this.guiScale.get().equals(saved)) {
                this.guiScale.set(saved);
            }
            return;
        }
        this.guiScale.set(GuiScaleMemory.FIRST_LAUNCH_DEFAULT);
        GuiScaleMemory.write(FabricLoader.getInstance().getConfigDir(), GuiScaleMemory.FIRST_LAUNCH_DEFAULT);
    }
}
