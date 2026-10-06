package net.lunacy.visuals.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.lunacy.visuals.config.GuiScaleMemory;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restore the last GUI Scale the player chose.
 * On first launch, initializes to 2 ("Интерфейс 2") and saves it.
 * When player chooses another scale, it is remembered across launches.
 */
@Mixin(GameOptions.class)
public class OptionsMixin {
    @Shadow
    @Final
    private SimpleOption<Integer> guiScale;

    @Inject(method = "load()V", at = @At("RETURN"))
    private void lunacyvisuals$restoreGuiScale(CallbackInfo ci) {
        int saved = GuiScaleMemory.read(FabricLoader.getInstance().getConfigDir());
        if (saved != GuiScaleMemory.UNSET) {
            if (!this.guiScale.getValue().equals(saved)) {
                this.guiScale.setValue(saved);
            }
            return;
        }
        this.guiScale.setValue(GuiScaleMemory.FIRST_LAUNCH_DEFAULT);
        GuiScaleMemory.write(FabricLoader.getInstance().getConfigDir(), GuiScaleMemory.FIRST_LAUNCH_DEFAULT);
    }
}
