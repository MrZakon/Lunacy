package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.ItemScrollerModule;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput clickType);

    @Unique private Slot lastScrolledSlot;
    @Unique private long lastScrollTime;

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        ItemScrollerModule module = runtime.module(ItemScrollerModule.class);
        if (module == null || !module.isEnabled() || !module.wheelEnabled()) return;

        Slot slot = this.hoveredSlot;
        if (slot != null && slot.hasItem()) {
            this.slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime == null) return;
        ItemScrollerModule module = runtime.module(ItemScrollerModule.class);
        if (module == null || !module.isEnabled() || !module.dragEnabled()) return;

        if (!module.matchesDragButton(event.button(), event.modifiers())) return;

        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        Slot slot = accessor.lunacy$getHoveredSlot(event.x(), event.y());
        if (slot != null && slot.hasItem()) {
            long now = System.currentTimeMillis();
            if (slot != lastScrolledSlot || now - lastScrollTime >= module.delayMs()) {
                lastScrolledSlot = slot;
                lastScrollTime = now;
                this.slotClicked(slot, slot.index, event.button(), ContainerInput.QUICK_MOVE);
            }
        }
    }
}
