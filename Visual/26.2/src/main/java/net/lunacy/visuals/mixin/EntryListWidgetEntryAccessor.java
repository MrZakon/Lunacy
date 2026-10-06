package net.lunacy.visuals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Stable access to option-row geometry inherited from Minecraft's list entry. */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList$Entry")
public interface EntryListWidgetEntryAccessor {
    @Accessor("x") int lunacy$getX();
    @Accessor("y") int lunacy$getY();
    @Accessor("width") int lunacy$getWidth();
    @Accessor("height") int lunacy$getHeight();
}
