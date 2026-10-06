package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class EnhancedTooltipsModule extends Module {
    private final BooleanSetting durability = add(new BooleanSetting("durability", "Прочность", "Точные единицы и проценты", true));
    private final BooleanSetting container = add(new BooleanSetting("container", "Содержимое контейнера", "Первые пять предметов шалкера", true));
    public EnhancedTooltipsModule() { super("enhanced_tooltips", "Подсказки предметов", "Прочность, проценты и содержимое контейнеров", ModuleCategory.HUD); }
    public void append(ItemStack stack, List<Component> lines) {
        if (durability.get() && stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            int percent = Math.round(left * 100F / Math.max(1, stack.getMaxDamage()));
            lines.add(Component.literal("Прочность: " + left + "/" + stack.getMaxDamage() + " (" + percent + "%)").withStyle(percent <= 20 ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        var contents = stack.get(DataComponents.CONTAINER);
        if (container.get() && contents != null) {
            var items = contents.nonEmptyItemCopyStream().limit(5).toList();
            long total = contents.nonEmptyItemCopyStream().count();
            if (!items.isEmpty()) lines.add(Component.literal("Содержимое:").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (var item : items) lines.add(Component.literal("  " + item.getCount() + "× ").append(item.getHoverName()).withStyle(ChatFormatting.GRAY));
            if (total > items.size()) lines.add(Component.literal("  …ещё " + (total - items.size())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
