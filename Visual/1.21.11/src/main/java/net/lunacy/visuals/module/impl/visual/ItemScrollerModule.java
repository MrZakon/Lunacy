package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import org.lwjgl.glfw.GLFW;

public final class ItemScrollerModule extends Module {
    public enum DragButton {
        RIGHT("Right Click"),
        LEFT("Left Click"),
        SHIFT_LEFT("Shift + Left Click");

        private final String label;
        DragButton(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final DoubleSetting delay = add(new DoubleSetting(
            "delay", "Задержка (мс)", "Задержка между перемещениями предметов в миллисекундах", 20.0, 0.0, 100.0, 5.0));
    private final BooleanSetting wheel = add(new BooleanSetting(
            "wheel", "Колесико мыши", "Быстрое перемещение предметов при прокрутке колесика", true));
    private final BooleanSetting drag = add(new BooleanSetting(
            "drag", "Перетаскивание", "Быстрое перемещение предметов при зажатой кнопке мыши", true));
    private final EnumSetting<DragButton> dragButton = add(new EnumSetting<>(
            "drag_button", "Кнопка", "Кнопка мыши для быстрого перетаскивания предметов", DragButton.LEFT, DragButton.class));

    public ItemScrollerModule() {
        super("item_scroller", "Item Scroller", "Быстрое перемещение предметов колесиком мыши или перетаскиванием",
                ModuleCategory.RENDER_FX);
    }

    public boolean wheelEnabled() {
        return wheel.get();
    }

    public boolean dragEnabled() {
        return drag.get();
    }

    public long delayMs() {
        return delay.get().longValue();
    }

    public boolean matchesDragButton(int button, int modifiers) {
        return switch (dragButton.get()) {
            case RIGHT -> button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case LEFT -> button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case SHIFT_LEFT -> button == GLFW.GLFW_MOUSE_BUTTON_LEFT && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        };
    }
}
