package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.Render2DEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class AmbienceModule extends Module {
    private final ColorSetting tint = add(new ColorSetting("tint", "World tint", "Screen-space atmosphere color",
            ColorValue.solid(0xFF8060C0)));
    private final DoubleSetting strength = add(new DoubleSetting(
            "strength", "Tint strength", "Color-grading intensity", 0.08, 0, 0.35, 0.01));
    private final BooleanSetting forceTime = add(new BooleanSetting(
            "force_time", "Custom time", "Use a client-side visual time of day", true));
    private final DoubleSetting time = add(new DoubleSetting(
            "time", "Time", "Desired client-side time", 18000, 0, 24000, 100));
    private final BooleanSetting weather = add(new BooleanSetting(
            "hide_weather", "Clear weather", "Hide client-side rain and thunder", true));
    private ClientLevel trackedWorld;
    private long previousTime;
    private float previousRain;
    private float previousThunder;

    public AmbienceModule() {
        super("ambience", "Ambience / Custom World", "Restorable tint, time and weather presentation",
                ModuleCategory.WORLD_VISUALS);
    }

    @Override public String displayValue() { return forceTime.get() ? String.valueOf(time.get().intValue()) : "TINT"; }
    public long timeOfDay() { return time.get().longValue(); }
    public boolean hidesWeather() { return weather.get(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        ClientLevel world = event.client().level;
        if (world == null) {
            trackedWorld = null;
            return;
        }
        if (trackedWorld != world) {
            trackedWorld = world;
            previousTime = world.getGameTime();
            previousRain = world.getRainLevel(1.0F);
            previousThunder = world.getThunderLevel(1.0F);
        }
        if (forceTime.get()) world.setTimeFromServer(timeOfDay());
        if (weather.get()) {
            world.setRainLevel(0.0F);
            world.setThunderLevel(0.0F);
        }
    }

    @Subscribe
    private void onRender(Render2DEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.screen() != null || strength.get() <= 0) return;
        int color = tint.get().colorAt(0, System.currentTimeMillis());
        UiRenderer.rect(event.context(), 0, 0, client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight(),
                ColorUtil.withAlpha(color, (int) (strength.get() * 255)));
    }

    @Override
    protected void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (trackedWorld != null && client.level == trackedWorld) {
            if (forceTime.get()) trackedWorld.setTimeFromServer(previousTime);
            if (weather.get()) {
                trackedWorld.setRainLevel(previousRain);
                trackedWorld.setThunderLevel(previousThunder);
            }
        }
        trackedWorld = null;
    }
}
