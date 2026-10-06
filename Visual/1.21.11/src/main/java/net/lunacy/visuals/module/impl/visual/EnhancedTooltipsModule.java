package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.List;

public final class EnhancedTooltipsModule extends Module {
    private final BooleanSetting durability = add(new BooleanSetting("durability", "Прочность", "Точные единицы и проценты", true));
    private final BooleanSetting container = add(new BooleanSetting("container", "Содержимое контейнера", "Первые пять предметов шалкера", true));
    public EnhancedTooltipsModule() { super("enhanced_tooltips", "Подсказки предметов", "Прочность, проценты и содержимое контейнеров", ModuleCategory.HUD); }
    public void append(ItemStack stack, List<Text> lines) {
        if (durability.get() && stack.isDamageable()) {
            int left = stack.getMaxDamage() - stack.getDamage();
            int percent = Math.round(left * 100F / Math.max(1, stack.getMaxDamage()));
            lines.add(Text.literal("Прочность: " + left + "/" + stack.getMaxDamage() + " (" + percent + "%)").formatted(percent <= 20 ? Formatting.RED : Formatting.GRAY));
        }
        var contents = stack.get(DataComponentTypes.CONTAINER);
        if (container.get() && contents != null) {
            var items = contents.streamNonEmpty().limit(5).toList();
            long total = contents.streamNonEmpty().count();
            if (!items.isEmpty()) lines.add(Text.literal("Содержимое:").formatted(Formatting.LIGHT_PURPLE));
            for (var item : items) lines.add(Text.literal("  " + item.getCount() + "× ").append(item.getName()).formatted(Formatting.GRAY));
            if (total > items.size()) lines.add(Text.literal("  …ещё " + (total - items.size())).formatted(Formatting.DARK_GRAY));
        }
    }
}
