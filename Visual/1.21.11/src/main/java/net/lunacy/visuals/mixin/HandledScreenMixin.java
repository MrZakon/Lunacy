package net.lunacy.visuals.mixin;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.impl.visual.ItemScrollerModule;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow protected abstract void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType);

    @Unique private Slot lunacy$lastMovedSlot;
    @Unique private long lunacy$lastMoveTime;
    @Unique private boolean lunacy$isDragging;

    protected HandledScreenMixin() {
        super(null);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void lunacy$onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        ItemScrollerModule scroller = lunacy$scroller();
        if (scroller != null && scroller.isEnabled() && scroller.wheelEnabled() && verticalAmount != 0) {
            Slot slot = lunacy$findSlot(mouseX, mouseY);
            if (slot != null && slot.hasStack()) {
                this.onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lunacy$onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        ItemScrollerModule scroller = lunacy$scroller();
        if (scroller != null && scroller.isEnabled() && scroller.dragEnabled() && scroller.matchesDragButton(click.button(), click.modifiers())) {
            lunacy$isDragging = true;
            Slot slot = lunacy$findSlot(click.x(), click.y());
            if (slot != null && slot.hasStack()) {
                this.onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
                lunacy$lastMovedSlot = slot;
                lunacy$lastMoveTime = System.currentTimeMillis();
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void lunacy$onMouseDragged(Click click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        ItemScrollerModule scroller = lunacy$scroller();
        if (scroller != null && scroller.isEnabled() && scroller.dragEnabled() && lunacy$isDragging
                && scroller.matchesDragButton(click.button(), click.modifiers())) {
            Slot slot = lunacy$findSlot(click.x(), click.y());
            if (slot != null && slot.hasStack() && slot != lunacy$lastMovedSlot) {
                long now = System.currentTimeMillis();
                if (now - lunacy$lastMoveTime >= scroller.delayMs()) {
                    this.onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
                    lunacy$lastMovedSlot = slot;
                    lunacy$lastMoveTime = now;
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void lunacy$onMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        lunacy$isDragging = false;
        lunacy$lastMovedSlot = null;
    }

    @Unique
    private Slot lunacy$findSlot(double x, double y) {
        if (this.focusedSlot != null) return this.focusedSlot;
        if ((Object) this instanceof HandledScreenAccessor accessor) {
            return accessor.lunacy$getSlotAt(x, y);
        }
        return null;
    }

    @Unique
    private static ItemScrollerModule lunacy$scroller() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime != null ? runtime.module(ItemScrollerModule.class) : null;
    }
}