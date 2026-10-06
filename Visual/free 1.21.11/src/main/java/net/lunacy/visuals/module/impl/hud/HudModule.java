package net.lunacy.visuals.module.impl.hud;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.config.ClientSettings;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.Render2DEvent;
import net.lunacy.visuals.gui.LunacyTheme;
import net.lunacy.visuals.hud.HudAnchor;
import net.lunacy.visuals.hud.HudBounds;
import net.lunacy.visuals.hud.HudPosition;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public abstract class HudModule extends Module {
    private static final Identifier GLASS_SURFACE = Identifier.of("visual-test", "hud/surface");
    private final HudPosition fallback;

    protected HudModule(String id, String name, String description, HudPosition fallback) {
        super(id, name, description, ModuleCategory.HUD);
        this.fallback = fallback;
    }

    @Subscribe
    private void onRender2D(Render2DEvent event) {
        if (event.context() == null || isBlocked()) return;
        beforeRender();
        boolean editor = MinecraftClient.getInstance().currentScreen instanceof net.lunacy.visuals.gui.ClickGuiScreen screen
                && screen.isEditingHud();
        SmartHudModule smart = ClientRuntime.get().module(SmartHudModule.class);
        if (smart != null && !smart.visible(id()) && !editor) return;
        if (!shouldRender() && !editor) return;
        HudPosition position = position();
        double width = width();
        double height = height();
        var window = MinecraftClient.getInstance().getWindow();
        double[] point = resolve(position, width, height, window.getScaledWidth(), window.getScaledHeight());
        event.context().getMatrices().pushMatrix();
        event.context().getMatrices().translate((float) point[0], (float) point[1]);
        event.context().getMatrices().scale((float) position.scale(), (float) position.scale());
        render(event.context(), 0, 0, 1, event.tickDelta());
        if (smart != null && smart.isEnabled() && smart.urgent(id())) {
            double pulse = .5 + .5 * Math.sin(System.currentTimeMillis() / 140.0);
            UiRenderer.roundedBorder(event.context(), 0, 0, width, height, 8, 1.5,
                    net.lunacy.visuals.render.ColorUtil.withAlpha(0xFFFF557A, (int) (120 + pulse * 120)));
        }
        event.context().getMatrices().popMatrix();
        ClientRuntime.get().hudLayout().put(id(), new HudBounds(point[0], point[1],
                width * position.scale(), height * position.scale()));
    }

    public HudPosition position() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null || runtime.config() == null ? fallback
                : runtime.config().hudPosition(id(), fallback);
    }

    public void moveTo(double x, double y) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.config() != null) {
            runtime.config().setHudPosition(id(), new HudPosition(HudAnchor.TOP_LEFT, x, y, position().scale()));
        }
    }

    public void setScale(double scale) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        if (runtime != null && runtime.config() != null) {
            HudPosition old = position();
            runtime.config().setHudPosition(id(), new HudPosition(old.anchor(), old.x(), old.y(), scale));
        }
    }

    protected void beforeRender() {}
    protected boolean shouldRender() { return true; }

    protected abstract double width();
    protected abstract double height();
    protected abstract void render(DrawContext context, double x, double y, double scale, float tickDelta);

    protected static void panel(DrawContext context, double x, double y, double width, double height) {
        ClientRuntime runtime = ClientRuntime.getNullable();
        boolean glass = runtime != null && runtime.settings().hudStyle() == ClientSettings.HudStyle.GLASS;
        if (glass) {
            UiRenderer.guiSprite(context, GLASS_SURFACE, x, y, width, height);
            return;
        }
        int surface = LunacyTheme.surface();
        UiRenderer.roundedRect(context, x, y, width, height, 7, surface);
        UiRenderer.roundedBorder(context, x, y, width, height, 7, 1, LunacyTheme.softLine());
    }

    protected static int hudText() {
        return glassHud() ? 0xFFF1F4F6 : LunacyTheme.text();
    }

    protected static int hudMuted() {
        return glassHud() ? 0xFF9CA8AE : LunacyTheme.muted();
    }

    private static boolean glassHud() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime != null && runtime.settings().hudStyle() == ClientSettings.HudStyle.GLASS;
    }

    private static double[] resolve(HudPosition position, double width, double height,
                                    double screenWidth, double screenHeight) {
        double scaledWidth = width * position.scale();
        double scaledHeight = height * position.scale();
        double x = switch (position.anchor()) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> position.x();
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> screenWidth * 0.5 - scaledWidth * 0.5 + position.x();
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - scaledWidth - position.x();
        };
        double y = switch (position.anchor()) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> position.y();
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> screenHeight * 0.5 - scaledHeight * 0.5 + position.y();
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> screenHeight - scaledHeight - position.y();
        };
        return new double[]{x, y};
    }
}
