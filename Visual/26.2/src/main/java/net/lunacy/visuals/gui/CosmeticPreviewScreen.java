package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public final class CosmeticPreviewScreen extends Screen {
    private final Screen parent;
    private List<Module> cosmetics = List.of();
    private int selected;
    public CosmeticPreviewScreen(Screen parent) { super(Component.literal("Примерочная Lunacy")); this.parent = parent; }
    @Override protected void init() { cosmetics = ClientRuntime.get().modules().all().stream().filter(m -> m.category() == ModuleCategory.COSMETICS && !m.id().equals("visual_studio")).toList(); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        LunacyTheme.canvas(context, width, height);
        double left = width / 2.0 - 150;
        LunacyTheme.card(context, left, 22, 300, height - 44, 16);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, "ПРИМЕРОЧНАЯ", left + 18, 36, 13, LunacyTheme.text());
        UiRenderer.font(context, FontFace.GOLOS, "←/→ косметика  •  Enter вкл/выкл  •  Space анимация", left + 18, 55, 7.5, LunacyTheme.muted());
        if (minecraft.player != null) InventoryScreen.extractEntityInInventoryFollowsMouse(context, (int) left + 120, 72, (int) left + 280, height - 42, 72, .05F, mouseX, mouseY, minecraft.player);
        if (!cosmetics.isEmpty()) {
            Module module = cosmetics.get(selected);
            UiRenderer.font(context, FontFace.GOLOS_SEMI, module.name(), left + 18, height - 82, 10, module.isEnabled() ? LunacyTheme.success() : LunacyTheme.text());
            UiRenderer.font(context, FontFace.GOLOS, module.description(), left + 18, height - 66, 7, LunacyTheme.muted());
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    @Override public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (cosmetics.isEmpty()) return super.keyPressed(input);
        if (input.key() == GLFW.GLFW_KEY_LEFT) selected = Math.floorMod(selected - 1, cosmetics.size());
        else if (input.key() == GLFW.GLFW_KEY_RIGHT) selected = (selected + 1) % cosmetics.size();
        else if (input.key() == GLFW.GLFW_KEY_ENTER) cosmetics.get(selected).toggle();
        else if (input.key() == GLFW.GLFW_KEY_UP || input.key() == GLFW.GLFW_KEY_DOWN) cycleStyle(cosmetics.get(selected), input.key() == GLFW.GLFW_KEY_UP ? 1 : -1);
        else if (input.key() == GLFW.GLFW_KEY_SPACE && minecraft.player != null) minecraft.player.swing(InteractionHand.MAIN_HAND);
        else return super.keyPressed(input);
        return true;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void cycleStyle(Module module, int direction) { for (var setting : module.settings()) if (setting instanceof EnumSetting value) { Enum[] values = (Enum[]) value.values(); value.set(values[Math.floorMod(((Enum) value.get()).ordinal() + direction, values.length)]); return; } }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
