package net.lunacy.visuals.gui;

import net.lunacy.visuals.mixin.SelectWorldScreenAccessor;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Ванильный безопасный список миров внутри фирменной стеклянной сцены. */
public final class LunacyWorldSelectScreen extends SelectWorldScreen {
    public LunacyWorldSelectScreen(Screen parent) { super(parent); }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        var list = ((SelectWorldScreenAccessor) (Object) this).lunacy$getLevelList();
        if (list == null) return;
        int panelWidth = Math.min(680, Math.max(320, width - 72));
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(48, Math.min(58, height - 260));
        int listBottom = Math.max(panelY + 80, height - 74);
        int panelHeight = Math.max(80, listBottom - panelY);
        list.setRectangle(panelWidth, panelHeight, panelX, panelY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        LunacyMenuChrome.background(context, context.guiWidth(), context.guiHeight(),
                mouseX, mouseY, false);
        LunacyMenuChrome.microBrand(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int panelWidth = Math.min(680, Math.max(320, width - 72));
        int panelX = (width - panelWidth) / 2;
        int panelY = Math.max(48, Math.min(58, height - 260));
        int winX = panelX - 8;
        int winY = panelY - 28;
        int winW = panelWidth + 16;
        int winH = (height - 6) - winY;

        UiRenderer.shadow(context, winX, winY, winW, winH, 20, ColorUtil.withAlpha(0xFF000000, 150));
        UiRenderer.roundedRect(context, winX, winY, winW, winH, 20, 0xEE0C1411);
        UiRenderer.roundedBorder(context, winX, winY, winW, winH, 20, 1,
                ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 65));
        UiRenderer.spacedFont(context, FontFace.UNBOUNDED_LIGHT,
                "LUNACY / " + getTitle().getString().toUpperCase(java.util.Locale.ROOT),
                panelX + 8, winY + 11, 6.5, 0.9, LunacyMenuChrome.GREEN);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
