package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public final class CosmeticPreviewScreen extends Screen {
    private final Screen parent;
    private List<Module> cosmetics = List.of();
    private int selected;
    public CosmeticPreviewScreen(Screen parent) { super(Text.literal("Примерочная Lunacy")); this.parent = parent; }
    @Override protected void init() {
        cosmetics = ClientRuntime.get().modules().all().stream()
                .filter(m -> m.category() == ModuleCategory.COSMETICS && !m.id().equals("visual_studio")).toList();
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        LunacyTheme.canvas(context, width, height);
        double left = width / 2.0 - 150;
        LunacyTheme.card(context, left, 22, 300, height - 44, 16);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, "ПРИМЕРОЧНАЯ", left + 18, 36, 13, LunacyTheme.text());
        UiRenderer.font(context, FontFace.GOLOS, "←/→ косметика  •  Enter вкл/выкл  •  Space анимация", left + 18, 55, 7.5, LunacyTheme.muted());
        if (client.player != null) InventoryScreen.drawEntity(context, (int) left + 120, 72, (int) left + 280, height - 42, 72, .05F, mouseX, mouseY, client.player);
        if (!cosmetics.isEmpty()) {
            Module module = cosmetics.get(selected);
            UiRenderer.font(context, FontFace.GOLOS_SEMI, module.name(), left + 18, height - 82, 10, module.isEnabled() ? LunacyTheme.success() : LunacyTheme.text());
            UiRenderer.font(context, FontFace.GOLOS, module.description(), left + 18, height - 66, 7, LunacyTheme.muted());
        }
        super.render(context, mouseX, mouseY, delta);
    }
    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (cosmetics.isEmpty()) return super.keyPressed(input);
        if (input.key() == GLFW.GLFW_KEY_LEFT) selected = Math.floorMod(selected - 1, cosmetics.size());
        else if (input.key() == GLFW.GLFW_KEY_RIGHT) selected = (selected + 1) % cosmetics.size();
        else if (input.key() == GLFW.GLFW_KEY_ENTER) cosmetics.get(selected).toggle();
        else if (input.key() == GLFW.GLFW_KEY_UP || input.key() == GLFW.GLFW_KEY_DOWN) cycleStyle(cosmetics.get(selected), input.key() == GLFW.GLFW_KEY_UP ? 1 : -1);
        else if (input.key() == GLFW.GLFW_KEY_SPACE && client.player != null) client.player.swingHand(Hand.MAIN_HAND);
        else return super.keyPressed(input);
        return true;
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static void cycleStyle(Module module, int direction) {
        for (var setting : module.settings()) if (setting instanceof EnumSetting value) {
            Enum[] values = (Enum[]) value.values();
            value.set(values[Math.floorMod(((Enum) value.get()).ordinal() + direction, values.length)]); return;
        }
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
