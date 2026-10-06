package net.lunacy.visuals.gui;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.animation.Easing;
import net.lunacy.visuals.hud.HudBounds;
import net.lunacy.visuals.localization.LunacyI18n;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.impl.hud.HudModule;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.module.setting.Setting;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.lunacy.visuals.render.font.FontFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Faithful modern port of the supplied reference panel ClickGUI. Its fixed 120x250
 * liquid-glass columns, center scaling, lower search field and inline settings pages are kept;
 * only the module/settings adapters and branding are Lunacy-native.
 */
public final class ClickGuiScreen extends Screen {
    private static final ModuleCategory[] PANELS = {
            ModuleCategory.COSMETICS,
            ModuleCategory.HUD,
            ModuleCategory.RENDER_FX,
            ModuleCategory.WORLD_VISUALS
    };
    private static final double PANEL_W = 126;
    private static final double PANEL_GAP = 5;
    private static final double HEADER_H = 20;
    private static final double OPEN_H = 250;
    private static final double CONTENT_H = OPEN_H - HEADER_H;
    private static final double PANELS_Y_SHIFT = 125;
    private static final double SEARCH_W = 150;
    private static final double SEARCH_H = 18;
    private static final double SEARCH_Y_SHIFT = 140;
    private static final double DESCRIPTION_Y_OFFSET = 166;
    private static final long OPEN_MS = 450;
    private static final long CLOSE_MS = 220;
    private static final long PANEL_OPEN_MS = 70;
    private static final long PAGE_MS = 240;

    private final Screen parent;
    private final EnumMap<ModuleCategory, Double> scroll = new EnumMap<>(ModuleCategory.class);
    private final EnumMap<ModuleCategory, Boolean> collapsed = new EnumMap<>(ModuleCategory.class);
    private final EnumMap<ModuleCategory, List<Module>> moduleCache = new EnumMap<>(ModuleCategory.class);
    private List<Setting<?>> globalSettingsCache;
    private String lastSearch = null;
    private static final java.util.Map<String, String> FONT_FIT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private Module selected;
    private Module leavingSettings;
    private boolean pageOpening;
    private long pageTransitionAt;
    private String search = "";
    private boolean searchFocused;
    private boolean hudEditor;
    private HudModule draggedHud;
    private double dragOffsetX;
    private double dragOffsetY;
    private final java.util.Set<String> hudSelection = new java.util.LinkedHashSet<>();
    private final java.util.Deque<java.util.Map<String, net.lunacy.visuals.hud.HudPosition>> hudHistory = new java.util.ArrayDeque<>();
    private java.util.Map<String, net.lunacy.visuals.hud.HudPosition> dragStart = java.util.Map.of();
    private DoubleSetting draggedSlider;
    private double draggedSliderX;
    private double draggedSliderWidth;
    private long openedAt;
    private long closingAt;
    private String hoveredDescription = "";

    public ClickGuiScreen() {
        this(ModuleCategory.HUD);
    }

    public ClickGuiScreen(ModuleCategory initialCategory) {
        super(Component.translatable("lunacyvisuals.screen.clickgui"));
        Screen current = Minecraft.getInstance().gui.screen();
        this.parent = current instanceof ClickGuiScreen ? null : current;
        for (ModuleCategory category : PANELS) {
            scroll.put(category, 0.0);
            collapsed.put(category, false);
        }
        if (initialCategory != null) {
            scroll.put(initialCategory, 0.0);
        }
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        closingAt = 0;
        moduleCache.clear();
        globalSettingsCache = null;
        lastSearch = null;
        GuiSound.play("clickgui_open");
    }

    @Override public boolean isPauseScreen() { return false; }
    public boolean isEditingHud() { return hudEditor; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (hudEditor) {
            renderHudEditor(context, mouseX, mouseY);
            return;
        }
        // The supplied ClickGui is a floating panel set over the live world. It does not own a
        // full-screen plate or blur pass. Keep an artwork fallback only when no world exists.
        if (minecraft.level == null) LunacyMenuChrome.background(context, width, height, mouseX, mouseY, false);

        if (closingAt != 0 && System.currentTimeMillis() - closingAt >= CLOSE_MS) {
            finishClose();
            return;
        }

        Transform transform = transform();
        double mx = transform.mouseX(mouseX);
        double my = transform.mouseY(mouseY);
        hoveredDescription = "";

        transform.begin(context);
        renderFloatingHeader(context);
        int index = 0;
        for (ModuleCategory category : PANELS) {
            renderPanel(context, category, index++, mx, my);
        }
        renderSearch(context, mx, my);
        renderDescription(context);
        transform.end(context);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderFloatingHeader(GuiGraphicsExtractor context) {
        double x = panelLeft();
        double y = panelY() - 29;
        double w = 112;
        LunacyMenuChrome.glass(context, x, y, w, 23, 9,
                ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 74));
        LunacyMenuChrome.logo(context, x + 3, y + 2, 19);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, "LUNACY", x + 27, y + 5.5,
                8.6, LunacyMenuChrome.TEXT);
        UiRenderer.roundedRect(context, x + 27, y + 17, 36, 1, 0.5,
                ColorUtil.withAlpha(LunacyMenuChrome.VIOLET, 105));

        String hint = LunacyI18n.tr("clickgui.close_hint");
        double hintW = UiRenderer.fontWidth(FontFace.GOLOS, hint, 7.5);
        double hintX = panelLeft() + PANELS.length * PANEL_W
                + (PANELS.length - 1) * PANEL_GAP - hintW;
        UiRenderer.font(context, FontFace.GOLOS, hint, hintX, y + 7.0,
                7.5, LunacyMenuChrome.MUTED);
    }

    private void renderPanel(GuiGraphicsExtractor context, ModuleCategory category, int index,
                             double mouseX, double mouseY) {
        double x = panelLeft() + index * (PANEL_W + PANEL_GAP);
        double y = panelY();
        boolean isCollapsed = collapsed.get(category);
        double reveal = panelReveal();
        double panelHeight = HEADER_H + (isCollapsed ? 0 : CONTENT_H * reveal);

        UiRenderer.shadow(context, x, y + 2, PANEL_W, panelHeight, 13,
                ColorUtil.withAlpha(0xFF000000, 80));
        UiRenderer.verticalGradientRoundedRect(context, x + 1, y + 1, PANEL_W - 2,
                Math.max(1, panelHeight - 2), 12, 0xE91B2520, 0xEE0E1512);
        UiRenderer.roundedBorder(context, x, y, PANEL_W, panelHeight, 13, 1,
                ColorUtil.withAlpha(0xFFFFFFFF, 32));

        String title = fitFont(LunacyI18n.category(category), PANEL_W - 20, FontFace.GOLOS_SEMI, 8.5);
        double titleWidth = UiRenderer.fontWidth(FontFace.GOLOS_SEMI, title, 8.5);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, title,
                x + (PANEL_W - titleWidth) / 2.0 - 4,
                y + (HEADER_H - 8.5 * 1.2) / 2.0, 8.5, LunacyMenuChrome.TEXT);
        UiRenderer.roundedRect(context, x + PANEL_W - 14, y + 7, 6, 6, 3,
                ColorUtil.withAlpha(index % 2 == 0 ? LunacyMenuChrome.GREEN : LunacyMenuChrome.VIOLET, 220));

        if (isCollapsed || reveal <= 0.02) return;
        context.enableScissor((int) x, (int) (y + HEADER_H),
                (int) (x + PANEL_W), (int) (y + panelHeight));
        boolean selectedHere = selected != null && selected.category() == category;
        boolean leavingHere = leavingSettings != null && leavingSettings.category() == category;
        if (!selectedHere && !leavingHere) {
            if (category == ModuleCategory.SETTINGS) {
                renderGlobalSettings(context, category, x, y + HEADER_H, mouseX, mouseY);
            } else {
                renderModules(context, category, x, y + HEADER_H, mouseX, mouseY);
            }
        } else {
            double page = pageProgress();
            Module settingsModule = selectedHere ? selected : leavingSettings;
            double modulesX = pageOpening ? -PANEL_W * page : -PANEL_W * (1.0 - page);
            double settingsX = pageOpening ? PANEL_W * (1.0 - page) : PANEL_W * page;

            context.pose().pushMatrix();
            context.pose().translate((float) modulesX, 0);
            renderModules(context, category, x, y + HEADER_H, mouseX - modulesX, mouseY);
            context.pose().popMatrix();

            context.pose().pushMatrix();
            context.pose().translate((float) settingsX, 0);
            renderModuleSettings(context, category, settingsModule, x, y + HEADER_H,
                    mouseX - settingsX, mouseY);
            context.pose().popMatrix();

            if (!pageOpening && page >= 1.0) leavingSettings = null;
        }
        context.disableScissor();
    }

    private void renderModules(GuiGraphicsExtractor context, ModuleCategory category, double x, double top,
                               double mouseX, double mouseY) {
        List<Module> modules = modules(category);
        double offset = scroll.get(category);
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            double y = top + i * HEADER_H - offset;
            if (y + HEADER_H < top || y > top + CONTENT_H) continue;
            boolean hovered = UiRenderer.inside(mouseX, mouseY, x, y, PANEL_W, HEADER_H);
            if (hovered) {
                UiRenderer.roundedRect(context, x + 3, y + 2, PANEL_W - 6, HEADER_H - 4, 8,
                        module.isEnabled() ? 0x3377E7A2 : 0x26A66BFF);
                hoveredDescription = LunacyI18n.moduleDescription(module);
            }
            String name = fitFont(LunacyI18n.moduleName(module), PANEL_W - 45, FontFace.GOLOS, 8.6);
            UiRenderer.font(context, FontFace.GOLOS, name, x + 8, y + 4.7,
                    8.6, module.isEnabled() ? LunacyMenuChrome.TEXT : 0xFFD0D8D3);
            double toggleX = x + PANEL_W - (module.settings().isEmpty() ? 24 : 35);
            UiRenderer.roundedRect(context, toggleX, y + 6, 17, 9, 4.5,
                    module.isEnabled() ? LunacyMenuChrome.GREEN : 0xFF2B3731);
            UiRenderer.roundedRect(context, toggleX + (module.isEnabled() ? 10 : 2), y + 8, 5, 5, 2.5,
                    module.isEnabled() ? LunacyMenuChrome.INK : LunacyMenuChrome.MUTED);
            if (!module.settings().isEmpty()) {
                UiRenderer.font(context, FontFace.GOLOS_SEMI, ">", x + PANEL_W - 11, y + 5.4,
                        7.8, hovered ? LunacyMenuChrome.GREEN : LunacyMenuChrome.FAINT);
            }
        }
    }

    private void renderModuleSettings(GuiGraphicsExtractor context, ModuleCategory category, Module module,
                                      double x, double top,
                                      double mouseX, double mouseY) {
        double headerY = top;
        boolean backHovered = UiRenderer.inside(mouseX, mouseY, x, headerY, PANEL_W, 14);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, "<", x + 6, headerY + 3.3,
                7.8, backHovered ? LunacyMenuChrome.GREEN : LunacyMenuChrome.MUTED);
        String moduleTitle = fitFont(LunacyI18n.moduleName(module), PANEL_W - 24, FontFace.GOLOS_SEMI, 7.8);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, moduleTitle,
                x + 16, headerY + 3.3, 7.8, LunacyMenuChrome.TEXT);

        List<Setting<?>> settings = module.settings().stream().filter(Setting::isVisible).toList();
        double y = top + 14 - scroll.get(category);
        for (Setting<?> setting : settings) {
            double rowHeight = settingHeight(setting);
            if (y + rowHeight >= top + 14 && y <= top + CONTENT_H) {
                boolean hovered = UiRenderer.inside(mouseX, mouseY, x + 2, y, PANEL_W - 4, rowHeight);
                if (hovered) {
                    UiRenderer.roundedRect(context, x + 2, y + 1, PANEL_W - 4, rowHeight - 2, 5,
                            0x2DA66BFF);
                    hoveredDescription = LunacyI18n.settingDescription(setting);
                }
                renderCompactSetting(context, setting, x + 7, y, PANEL_W - 14, rowHeight);
            }
            y += rowHeight;
        }
    }

    private void renderGlobalSettings(GuiGraphicsExtractor context, ModuleCategory category, double x, double top,
                                      double mouseX, double mouseY) {
        double offset = scroll.get(category);
        double minecraftY = top - offset;
        boolean minecraftHover = UiRenderer.inside(mouseX, mouseY, x + 2, minecraftY, PANEL_W - 4, 24);
        if (minecraftHover) UiRenderer.roundedRect(context, x + 2, minecraftY + 1,
                PANEL_W - 4, 22, 5, 0x3977E7A2);
        String minecraftSettings = fitFont(LunacyI18n.tr("clickgui.minecraft_settings"),
                PANEL_W - 27, FontFace.GOLOS_SEMI, 7.6);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, minecraftSettings,
                x + 8, minecraftY + 7.5, 7.6, LunacyMenuChrome.TEXT);
        UiRenderer.font(context, FontFace.GOLOS_SEMI, ">", x + PANEL_W - 12, minecraftY + 7.5,
                7.8, minecraftHover ? LunacyMenuChrome.GREEN : LunacyMenuChrome.FAINT);

        double y = top + 24 - offset;
        for (Setting<?> setting : globalSettings()) {
            double rowHeight = settingHeight(setting);
            if (y + rowHeight >= top && y <= top + CONTENT_H) {
                boolean hovered = UiRenderer.inside(mouseX, mouseY, x + 2, y, PANEL_W - 4, rowHeight);
                if (hovered) {
                    UiRenderer.roundedRect(context, x + 2, y + 1, PANEL_W - 4, rowHeight - 2, 5,
                            0x2D77E7A2);
                    hoveredDescription = LunacyI18n.settingDescription(setting);
                }
                renderCompactSetting(context, setting, x + 7, y, PANEL_W - 14, rowHeight);
            }
            y += rowHeight;
        }
    }

    private void renderCompactSetting(GuiGraphicsExtractor context, Setting<?> setting,
                                      double x, double y, double width, double rowHeight) {
        String label = fitFont(LunacyI18n.settingName(setting), width - 43, FontFace.GOLOS, 7.2);
        UiRenderer.font(context, FontFace.GOLOS, label, x, y + 5.8, 7.2, LunacyMenuChrome.TEXT);
        double controlX = x + width - 38;
        if (setting instanceof BooleanSetting value) {
            UiRenderer.roundedRect(context, controlX + 18, y + 6, 20, 9, 4.5,
                    value.get() ? LunacyMenuChrome.GREEN : 0xFF344139);
            UiRenderer.roundedRect(context, controlX + (value.get() ? 30 : 20), y + 8, 5, 5, 2.5,
                    value.get() ? 0xFF07100B : LunacyMenuChrome.MUTED);
        } else if (setting instanceof DoubleSetting value) {
            double fraction = Math.clamp((value.get() - value.minimum()) / (value.maximum() - value.minimum()), 0.0, 1.0);
            String number = String.format(Locale.ROOT, "%.2f", value.get());
            UiRenderer.font(context, FontFace.GOLOS_SEMI, number,
                    x + width - UiRenderer.fontWidth(FontFace.GOLOS_SEMI, number, 6.7), y + 4.2,
                    6.7, LunacyMenuChrome.GREEN);
            double barY = y + rowHeight - 7;
            double barH = 4;
            UiRenderer.roundedRect(context, x, barY, width, barH, 2, 0x90222B27);
            double fillW = Math.max(3, width * fraction);
            UiRenderer.gradientRoundedRect(context, x, barY, fillW, barH, 2,
                    0xFF50E4FF, 0xFFB28DFF);
        } else if (setting instanceof ColorSetting value) {
            int color = value.get().colorAt(0, System.currentTimeMillis());
            UiRenderer.roundedRect(context, x + width - 31, y + 5, 31, 10, 4, color);
            UiRenderer.roundedBorder(context, x + width - 31, y + 5, 31, 10, 4, 1, 0x60FFFFFF);
        } else {
            String value = LunacyI18n.value(setting.get());
            value = fitFont(value.toUpperCase(Locale.ROOT), 38, FontFace.GOLOS_SEMI, 6.5);
            UiRenderer.font(context, FontFace.GOLOS_SEMI, value,
                    x + width - UiRenderer.fontWidth(FontFace.GOLOS_SEMI, value, 6.5), y + 6.4,
                    6.5, LunacyMenuChrome.GREEN);
        }
    }

    private void renderSearch(GuiGraphicsExtractor context, double mouseX, double mouseY) {
        double x = width / 2.0 - SEARCH_W / 2.0;
        double y = height / 2.0 + SEARCH_Y_SHIFT;
        UiRenderer.shadow(context, x, y + 1, SEARCH_W, SEARCH_H, 9, 0x75000000);
        UiRenderer.roundedRect(context, x, y, SEARCH_W, SEARCH_H, 9, 0xDC111815);
        if (!searchFocused) {
            UiRenderer.roundedBorder(context, x, y, SEARCH_W, SEARCH_H, 9, 1, 0x2EFFFFFF);
        }
        if (searchFocused) {
            UiRenderer.roundedBorder(context, x, y, SEARCH_W, SEARCH_H, 9, 1.2,
                    ColorUtil.withAlpha(LunacyMenuChrome.GREEN, 180));
        }
        String shown = search.isEmpty() ? LunacyI18n.tr("clickgui.search") : search;
        shown = fitFont(shown, SEARCH_W - 10, FontFace.GOLOS, 8.2);
        double shownWidth = UiRenderer.fontWidth(FontFace.GOLOS, shown, 8.2);
        double shownX = x + (SEARCH_W - shownWidth) / 2.0;
        UiRenderer.font(context, FontFace.GOLOS, shown, shownX, y + 4.0,
                8.2, search.isEmpty() ? 0xFF9696A0 : LunacyMenuChrome.TEXT);
        if (searchFocused && ((System.currentTimeMillis() / 500) & 1) == 0) {
            double caret = search.isEmpty() ? shownX : shownX + shownWidth + 1;
            UiRenderer.roundedRect(context, Math.min(caret, x + SEARCH_W - 8), y + 5, 1.2, 9, 0.6,
                    LunacyMenuChrome.TEXT);
        }
    }

    private void renderDescription(GuiGraphicsExtractor context) {
        if (hoveredDescription == null || hoveredDescription.isBlank()) return;
        String description = fitFont(hoveredDescription, 360, FontFace.ROUND, 9);
        double textWidth = UiRenderer.fontWidth(FontFace.ROUND, description, 9);
        double x = width / 2.0 - textWidth / 2.0;
        double y = height / 2.0 + DESCRIPTION_Y_OFFSET;
        UiRenderer.font(context, FontFace.ROUND, description, x, y,
                9, LunacyMenuChrome.TEXT);
    }

    private void renderHudEditor(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        LunacyMenuChrome.worldBackdrop(context, width, height);
        LunacyTheme.card(context, 12, 12, 236, 34, 14);
        LunacyMenuChrome.logo(context, 16, 15, 28);
        LunacyTheme.eyebrow(context, "ЛКМ двигать • ПКМ группа • колесо масштаб • Ctrl+Z отмена", 52, 25);
        ClientRuntime.get().hudLayout().bounds().forEach((id, bounds) -> {
            UiRenderer.roundedBorder(context, bounds.x() - 3, bounds.y() - 3,
                    bounds.width() + 6, bounds.height() + 6, 8, 1,
                    hudSelection.contains(id) || bounds.contains(mouseX, mouseY) ? LunacyTheme.accent() : LunacyTheme.softLine());
            LunacyTheme.eyebrow(context, id, bounds.x(), bounds.y() - 11);
        });
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (closingAt != 0) return true;
        if (pageAnimating()) return true;
        if (hudEditor) return clickHud(click);

        Transform transform = transform();
        double mx = transform.mouseX(click.x());
        double my = transform.mouseY(click.y());
        double searchX = width / 2.0 - SEARCH_W / 2.0;
        double searchY = height / 2.0 + SEARCH_Y_SHIFT;
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && UiRenderer.inside(mx, my, searchX - 4, searchY - 4, SEARCH_W + 8, SEARCH_H + 8)) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;
        int index = 0;
        for (ModuleCategory category : PANELS) {
            double x = panelLeft() + index++ * (PANEL_W + PANEL_GAP);
            double y = panelY();
            if (UiRenderer.inside(mx, my, x, y, PANEL_W, HEADER_H)) {
                if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    collapsed.put(category, !collapsed.get(category));
                    if (selected != null && selected.category() == category) {
                        selected = null;
                        leavingSettings = null;
                    }
                    return true;
                }
            }
            if (collapsed.get(category) || !UiRenderer.inside(mx, my, x, y + HEADER_H, PANEL_W, CONTENT_H)) {
                continue;
            }
            if (category == ModuleCategory.SETTINGS) {
                return clickGlobalSetting(click, category, x, y + HEADER_H, mx, my);
            }
            if (selected != null && selected.category() == category) {
                return clickModuleSetting(click, category, x, y + HEADER_H, mx, my);
            }
            List<Module> modules = modules(category);
            double rowY = y + HEADER_H - scroll.get(category);
            for (Module module : modules) {
                if (UiRenderer.inside(mx, my, x, rowY, PANEL_W, HEADER_H)) {
                    if ((click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                            || mx >= x + PANEL_W - 14) && !module.settings().isEmpty()) {
                        selected = module;
                        leavingSettings = null;
                        pageOpening = true;
                        pageTransitionAt = System.currentTimeMillis();
                        scroll.put(category, 0.0);
                    } else if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !module.isLocked()) {
                        module.toggle();
                        dirty();
                        GuiSound.play(module.isEnabled() ? "enable" : "disable");
                    }
                    return true;
                }
                rowY += HEADER_H;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean clickModuleSetting(MouseButtonEvent click, ModuleCategory category, double x, double top,
                                       double mx, double my) {
        if (UiRenderer.inside(mx, my, x, top, PANEL_W, 14)) {
            leavingSettings = selected;
            selected = null;
            pageOpening = false;
            pageTransitionAt = System.currentTimeMillis();
            scroll.put(category, 0.0);
            return true;
        }
        double y = top + 14 - scroll.get(category);
        for (Setting<?> setting : selected.settings()) {
            if (!setting.isVisible()) continue;
            double height = settingHeight(setting);
            if (UiRenderer.inside(mx, my, x + 2, y, PANEL_W - 4, height)) {
                applySettingClick(setting, click, x + 7, PANEL_W - 14, mx);
                dirty();
                return true;
            }
            y += height;
        }
        return true;
    }

    private boolean clickGlobalSetting(MouseButtonEvent click, ModuleCategory category, double x, double top,
                                       double mx, double my) {
        double offset = scroll.get(category);
        if (UiRenderer.inside(mx, my, x + 2, top - offset, PANEL_W - 4, 24)) {
                minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false));
            return true;
        }
        double y = top + 24 - offset;
        for (Setting<?> setting : globalSettings()) {
            double height = settingHeight(setting);
            if (UiRenderer.inside(mx, my, x + 2, y, PANEL_W - 4, height)) {
                applySettingClick(setting, click, x + 7, PANEL_W - 14, mx);
                dirty();
                return true;
            }
            y += height;
        }
        return true;
    }

    private void applySettingClick(Setting<?> setting, MouseButtonEvent click,
                                   double controlX, double controlWidth, double mouseX) {
        if (setting instanceof BooleanSetting value) value.set(!value.get());
        else if (setting instanceof DoubleSetting value) {
            draggedSlider = value;
            draggedSliderX = controlX;
            draggedSliderWidth = controlWidth;
            double fraction = Math.clamp((mouseX - controlX) / Math.max(1, controlWidth), 0.0, 1.0);
            double newVal = value.minimum() + (value.maximum() - value.minimum()) * fraction;
            if (value.step() > 0.0) newVal = Math.round(newVal / value.step()) * value.step();
            value.set(newVal);
        } else if (setting instanceof EnumSetting<?> value) cycleEnum(value);
        else if (setting instanceof ColorSetting value) {
            ColorValue old = value.get();
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                value.set(new ColorValue(old.primaryArgb(), old.secondaryArgb(),
                        !old.rainbow(), old.rainbowSpeed()));
            } else {
                float hue = (float) Math.clamp((mouseX - controlX) / Math.max(1, controlWidth), 0.0, 1.0);
                int primary = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.46F, 0.78F) & 0xFFFFFF);
                int secondary = 0xFF000000 | (java.awt.Color.HSBtoRGB((hue + 0.12F) % 1.0F, 0.44F, 0.88F) & 0xFFFFFF);
                value.set(new ColorValue(primary, secondary, false, old.rainbowSpeed()));
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void cycleEnum(EnumSetting setting) {
        Enum[] values = (Enum[]) setting.values();
        setting.set(values[(((Enum) setting.get()).ordinal() + 1) % values.length]);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (hudEditor) {
            for (var entry : ClientRuntime.get().hudLayout().bounds().entrySet()) {
                if (!entry.getValue().contains(mouseX, mouseY)) continue;
                Module module = ClientRuntime.get().modules().find(entry.getKey()).orElse(null);
                if (module instanceof HudModule hud) {
                    hud.setScale(hud.position().scale() + Math.copySign(0.05, verticalAmount));
                    return true;
                }
            }
            return true;
        }
        Transform transform = transform();
        double mx = transform.mouseX(mouseX);
        double my = transform.mouseY(mouseY);
        int index = 0;
        for (ModuleCategory category : PANELS) {
            double x = panelLeft() + index++ * (PANEL_W + PANEL_GAP);
            double y = panelY();
            if (!collapsed.get(category) && UiRenderer.inside(mx, my, x, y + HEADER_H, PANEL_W, CONTENT_H)) {
                double maximum = maxScroll(category);
                scroll.put(category, Math.clamp(scroll.get(category) - verticalAmount * 22, 0, maximum));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!searchFocused || !input.isAllowedChatCharacter() || search.length() >= 42) {
            return super.charTyped(input);
        }
        search += input.codepointAsString();
        resetScrolls();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (hudEditor && key == GLFW.GLFW_KEY_Z && (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            undoHud();
            return true;
        }
        if (searchFocused) {
            if ((key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) && !search.isEmpty()) {
                search = search.substring(0, search.offsetByCodePoints(search.length(), -1));
                resetScrolls();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                if (!search.isEmpty()) {
                    search = "";
                    resetScrolls();
                }
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (hudEditor) hudEditor = false;
            else onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    private boolean clickHud(MouseButtonEvent click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (var entry : ClientRuntime.get().hudLayout().bounds().entrySet()) {
                if (entry.getValue().contains(click.x(), click.y())) {
                    if (!hudSelection.add(entry.getKey())) hudSelection.remove(entry.getKey());
                    return true;
                }
            }
            hudEditor = false;
            return true;
        }
        for (var entry : ClientRuntime.get().hudLayout().bounds().entrySet()) {
            HudBounds bounds = entry.getValue();
            if (!bounds.contains(click.x(), click.y())) continue;
            Module module = ClientRuntime.get().modules().find(entry.getKey()).orElse(null);
            if (module instanceof HudModule hud) {
                if (!hudSelection.contains(entry.getKey())) { hudSelection.clear(); hudSelection.add(entry.getKey()); }
                dragStart = captureHudPositions();
                hudHistory.push(new java.util.LinkedHashMap<>(dragStart));
                while (hudHistory.size() > 30) hudHistory.removeLast();
                draggedHud = hud;
                dragOffsetX = click.x() - bounds.x();
                dragOffsetY = click.y() - bounds.y();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggedSlider != null) {
            Transform transform = transform();
            double mx = transform.mouseX(click.x());
            double fraction = Math.clamp((mx - draggedSliderX) / Math.max(1, draggedSliderWidth), 0.0, 1.0);
            double newVal = draggedSlider.minimum() + (draggedSlider.maximum() - draggedSlider.minimum()) * fraction;
            if (draggedSlider.step() > 0.0) newVal = Math.round(newVal / draggedSlider.step()) * draggedSlider.step();
            draggedSlider.set(newVal);
            dirty();
            return true;
        }
        if (draggedHud != null) {
            HudBounds bounds = ClientRuntime.get().hudLayout().bounds().get(draggedHud.id());
            if (bounds == null) return true;
            double snapX = Math.round((click.x() - dragOffsetX) / 4.0) * 4.0;
            double snapY = Math.round((click.y() - dragOffsetY) / 4.0) * 4.0;
            if (Math.abs(snapX + bounds.width() / 2 - width / 2.0) < 5) {
                snapX = width / 2.0 - bounds.width() / 2;
            }
            double[] snapped = snapToHud(snapX, snapY, bounds);
            var primary = dragStart.get(draggedHud.id());
            if (primary == null) { draggedHud.moveTo(snapped[0], snapped[1]); return true; }
            double dx = snapped[0] - primary.x();
            double dy = snapped[1] - primary.y();
            for (String id : hudSelection) {
                var start = dragStart.get(id);
                Module selectedModule = ClientRuntime.get().modules().find(id).orElse(null);
                if (start != null && selectedModule instanceof HudModule hud) hud.moveTo(start.x() + dx, start.y() + dy);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggedHud = null;
        dragStart = java.util.Map.of();
        draggedSlider = null;
        return super.mouseReleased(click);
    }

    private java.util.Map<String, net.lunacy.visuals.hud.HudPosition> captureHudPositions() {
        java.util.Map<String, net.lunacy.visuals.hud.HudPosition> result = new java.util.LinkedHashMap<>();
        for (String id : hudSelection) {
            Module module = ClientRuntime.get().modules().find(id).orElse(null);
            if (module instanceof HudModule hud) result.put(id, hud.position());
        }
        return result;
    }

    private void undoHud() {
        if (hudHistory.isEmpty()) return;
        hudHistory.pop().forEach((id, position) -> {
            Module module = ClientRuntime.get().modules().find(id).orElse(null);
            if (module instanceof HudModule hud) { hud.moveTo(position.x(), position.y()); hud.setScale(position.scale()); }
        });
    }

    private double[] snapToHud(double x, double y, HudBounds dragged) {
        double bestX = x, bestY = y, threshold = 5;
        for (var entry : ClientRuntime.get().hudLayout().bounds().entrySet()) {
            if (hudSelection.contains(entry.getKey())) continue;
            HudBounds other = entry.getValue();
            double[] xs = {other.x(), other.x() + other.width(), other.x() + other.width()/2 - dragged.width()/2};
            double[] ys = {other.y(), other.y() + other.height(), other.y() + other.height()/2 - dragged.height()/2};
            for (double candidate : xs) if (Math.abs(bestX - candidate) < threshold) bestX = candidate;
            for (double candidate : ys) if (Math.abs(bestY - candidate) < threshold) bestY = candidate;
        }
        return new double[]{bestX, bestY};
    }

    @Override
    public void onClose() {
        if (closingAt == 0) closingAt = System.currentTimeMillis();
    }

    private void finishClose() {
        ClientRuntime.get().config().flush();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private List<Module> modules(ModuleCategory category) {
        if (!java.util.Objects.equals(search, lastSearch)) {
            moduleCache.clear();
            globalSettingsCache = null;
            lastSearch = search;
        }
        return moduleCache.computeIfAbsent(category, cat -> {
            String query = search.toLowerCase(Locale.ROOT).strip();
            return ClientRuntime.get().modules().all().stream()
                    .filter(module -> module.category() == cat)
                    .filter(module -> !module.isBlocked())
                    .filter(module -> query.isEmpty()
                            || LunacyI18n.moduleName(module).toLowerCase(Locale.ROOT).contains(query)
                            || LunacyI18n.moduleDescription(module).toLowerCase(Locale.ROOT).contains(query)
                            || module.id().contains(query))
                    .toList();
        });
    }

    private List<Setting<?>> globalSettings() {
        if (!java.util.Objects.equals(search, lastSearch)) {
            moduleCache.clear();
            globalSettingsCache = null;
            lastSearch = search;
        }
        if (globalSettingsCache == null) {
            String query = search.toLowerCase(Locale.ROOT).strip();
            globalSettingsCache = ClientRuntime.get().settings().all().stream()
                    .filter(Setting::isVisible)
                    .filter(setting -> query.isEmpty()
                            || LunacyI18n.settingName(setting).toLowerCase(Locale.ROOT).contains(query)
                            || LunacyI18n.settingDescription(setting).toLowerCase(Locale.ROOT).contains(query)
                            || setting.key().contains(query))
                    .toList();
        }
        return globalSettingsCache;
    }

    private double maxScroll(ModuleCategory category) {
        double total;
        if (category == ModuleCategory.SETTINGS) {
            total = 24 + globalSettings().stream().mapToDouble(ClickGuiScreen::settingHeight).sum();
        } else if (selected != null && selected.category() == category) {
            total = 14 + selected.settings().stream().filter(Setting::isVisible)
                    .mapToDouble(ClickGuiScreen::settingHeight).sum();
        } else {
            total = modules(category).size() * HEADER_H;
        }
        return Math.max(0, total - CONTENT_H);
    }

    private double panelLeft() {
        int count = PANELS.length;
        double total = count * PANEL_W + (count - 1) * PANEL_GAP;
        return width / 2.0 - total / 2.0;
    }

    private double panelY() { return height / 2.0 - PANELS_Y_SHIFT; }

    private double openReveal() {
        double value = Easing.EASE_OUT_CUBIC.applyAsDouble(
                Math.clamp((System.currentTimeMillis() - openedAt) / (double) OPEN_MS, 0.0, 1.0));
        if (ClientRuntime.get().settings().reducedMotion()) return 1.0;
        return value;
    }

    private double panelReveal() {
        if (ClientRuntime.get().settings().reducedMotion()) return 1.0;
        return Easing.EASE_OUT_CUBIC.applyAsDouble(
                Math.clamp((System.currentTimeMillis() - openedAt) / (double) PANEL_OPEN_MS, 0.0, 1.0));
    }

    private double pageProgress() {
        if (ClientRuntime.get().settings().reducedMotion() || pageTransitionAt == 0) return 1.0;
        return Easing.EASE_OUT_CUBIC.applyAsDouble(
                Math.clamp((System.currentTimeMillis() - pageTransitionAt) / (double) PAGE_MS, 0.0, 1.0));
    }

    private boolean pageAnimating() {
        return !ClientRuntime.get().settings().reducedMotion()
                && pageTransitionAt != 0
                && System.currentTimeMillis() - pageTransitionAt < PAGE_MS;
    }

    private Transform transform() {
        int count = PANELS.length;
        double designWidth = count * PANEL_W + (count - 1) * PANEL_GAP;
        double desired = ClientRuntime.get().settings().uiScale();
        double fit = Math.min(1.0, Math.min(
                Math.max(1, width - 8) / (designWidth * desired),
                Math.max(1, height - 8) / (360.0 * desired)));
        double base = desired * Math.max(0.60, fit);
        double progress = openReveal();
        if (closingAt != 0) {
            progress = 1.0 - Math.clamp((System.currentTimeMillis() - closingAt) / (double) CLOSE_MS, 0.0, 1.0);
        }
        double scale = base * (0.90 + 0.10 * progress);
        return new Transform(Math.max(0.01, scale), width / 2.0, height / 2.0);
    }

    private void resetScrolls() {
        for (ModuleCategory category : PANELS) scroll.put(category, 0.0);
    }

    private void dirty() { ClientRuntime.get().config().markDirty(); }

    private static double settingHeight(Setting<?> setting) {
        return setting instanceof DoubleSetting ? 29 : setting instanceof ColorSetting ? 25 : 22;
    }

    private static String fit(String value, double maxWidth, double scale) {
        if (value == null) return "";
        if (UiRenderer.textWidth(value) * scale <= maxWidth) return value;
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + "...";
            if (UiRenderer.textWidth(candidate) * scale <= maxWidth) return candidate;
        }
        return "...";
    }

    private static String fitFont(String value, double maxWidth, FontFace face, double size) {
        if (value == null) return "";
        String cacheKey = face.name() + ":" + (int) size + ":" + (int) maxWidth + ":" + value;
        String cached = FONT_FIT_CACHE.get(cacheKey);
        if (cached != null) return cached;
        if (UiRenderer.fontWidth(face, value, size) <= maxWidth) {
            FONT_FIT_CACHE.put(cacheKey, value);
            return value;
        }
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String candidate = value.substring(0, end).stripTrailing() + "...";
            if (UiRenderer.fontWidth(face, candidate, size) <= maxWidth) {
                FONT_FIT_CACHE.put(cacheKey, candidate);
                return candidate;
            }
        }
        FONT_FIT_CACHE.put(cacheKey, "...");
        return "...";
    }

    private record Transform(double scale, double centerX, double centerY) {
        void begin(GuiGraphicsExtractor context) {
            context.pose().pushMatrix();
            context.pose().translate((float) centerX, (float) centerY);
            context.pose().scale((float) scale, (float) scale);
            context.pose().translate((float) -centerX, (float) -centerY);
        }

        void end(GuiGraphicsExtractor context) { context.pose().popMatrix(); }
        double mouseX(double screenX) { return centerX + (screenX - centerX) / scale; }
        double mouseY(double screenY) { return centerY + (screenY - centerY) / scale; }
    }
}
