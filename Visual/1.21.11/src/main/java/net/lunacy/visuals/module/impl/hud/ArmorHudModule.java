package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.hud.HudAnchor;
import net.lunacy.visuals.hud.HudPosition;
import net.lunacy.visuals.gui.LunacyTheme;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;

public final class ArmorHudModule extends HudModule {
    private static final Identifier SLOT = Identifier.of("visual-test", "hud/slot");
    public ArmorHudModule() {
        super("armor_hud", "ArmorHUD", "Броня и прочность предметов",
                new HudPosition(HudAnchor.BOTTOM_CENTER, 0, 56, 1));
    }

    @Override protected double width() { return 118; }
    @Override protected double height() { return 34; }

    @Override
    protected void render(DrawContext context, double x, double y, double scale, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        panel(context, x, y, width(), height());
        int index = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = client.player.getEquippedStack(slot);
            int slotX = (int) x + 8 + index * 26;
            UiRenderer.guiSprite(context, SLOT, slotX, y + 5, 23, 23);
            int itemX = slotX + 3;
            if (!stack.isEmpty()) context.drawItem(stack, itemX, (int) y + 8);
            if (stack.isDamageable()) {
                float durability = 1.0F - stack.getDamage() / (float) Math.max(1, stack.getMaxDamage());
                int color = ColorUtil.mix(0xFFFF557A, 0xFF63E6A5, durability);
                UiRenderer.rect(context, slotX + 3, y + 28, 17 * durability, 2, color);
            }
            index++;
        }
    }
}
