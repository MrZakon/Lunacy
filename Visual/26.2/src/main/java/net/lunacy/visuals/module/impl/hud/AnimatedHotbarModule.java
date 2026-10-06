package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.Render2DEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;

public final class AnimatedHotbarModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Цвет", "Цвет рамки выбранного слота",
            new ColorValue(0xFF77E7A2, 0xFFA66BFF, false, .7)));
    private final BooleanSetting itemPop = add(new BooleanSetting("item_pop", "Увеличение предмета",
            "Показывать выбранный предмет при смене слота", true));
    private double position;
    private int selected = -1;
    private long changedAt;
    public AnimatedHotbarModule() { super("animated_hotbar", "Анимированный хотбар", "Плавная рамка слота и анимация выбранного предмета", ModuleCategory.HUD); }
    @Subscribe private void render(Render2DEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        int slot = client.player.getInventory().getSelectedSlot();
        if (selected < 0) position = slot;
        if (slot != selected) { selected = slot; changedAt = System.nanoTime(); }
        position += (slot - position) * Math.min(1, ClientRuntimeFrame.delta() * 18);
        double x = event.context().guiWidth() / 2.0 - 91 + position * 20;
        double y = event.context().guiHeight() - 23;
        int accent = color.get().colorAt(position / 9.0, System.currentTimeMillis());
        UiRenderer.roundedBorder(event.context(), x, y, 22, 22, 6, 1.5, ColorUtil.withAlpha(accent, 230));
        double age = (System.nanoTime() - changedAt) / 1_000_000_000.0;
        if (itemPop.get() && age < .28) {
            double pulse = Math.sin(age / .28 * Math.PI);
            event.context().pose().pushMatrix();
            event.context().pose().translate((float) (x + 11), (float) (y - 9 - pulse * 4));
            event.context().pose().scale((float) (1 + pulse * .28), (float) (1 + pulse * .28));
            event.context().item(client.player.getInventory().getItem(slot), -8, -8);
            event.context().pose().popMatrix();
        }
    }
}
