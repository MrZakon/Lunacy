package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.KeyEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.minecraft.client.MinecraftClient;

/** Reversible local camera setup. F2 remains the screenshot trigger. */
public final class PhotoModeModule extends Module {
    public enum Filter { NATURAL, WARM, MOON, CINEMA }
    private final DoubleSetting fov = add(new DoubleSetting("fov", "FOV", "Угол обзора фоторежима", 50, 20, 110, 1));
    private final DoubleSetting roll = add(new DoubleSetting("roll", "Наклон", "Наклон камеры", 0, -30, 30, 1));
    private final DoubleSetting time = add(new DoubleSetting("time", "Время", "Визуальное время мира", 12500, 0, 24000, 250));
    private final EnumSetting<Filter> filter = add(new EnumSetting<>("filter", "Фильтр", "Освещение кадра", Filter.NATURAL, Filter.class));
    private boolean active;
    private int oldFov;
    private double oldGamma;
    private boolean oldHud;
    private long oldTime;
    private Object world;
    public PhotoModeModule() { super("photo_mode", "Фоторежим", "F8: камера, время, фильтр и чистый кадр; F2: снимок", ModuleCategory.RENDER_FX); }
    @Subscribe private void key(KeyEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (event.action() == 1 && event.key() == 297 && client.player != null && client.currentScreen == null) setActive(!active, client);
    }
    @Subscribe private void tick(TickEvent.Client event) {
        if (!active) return;
        var client = event.client();
        if (client.player == null || client.world == null) { setActive(false, client); return; }
        client.options.getFov().setValue(fov.get().intValue());
        client.options.getGamma().setValue(gamma());
        client.options.hudHidden = true;
        client.world.getLevelProperties().setTimeOfDay(time.get().longValue());
    }
    private double gamma() { return switch (filter.get()) { case NATURAL -> oldGamma; case WARM -> .65; case MOON -> 1.0; case CINEMA -> .35; }; }
    private void setActive(boolean value, MinecraftClient client) {
        if (active == value) return;
        active = value;
        if (value) {
            oldFov = client.options.getFov().getValue(); oldGamma = client.options.getGamma().getValue(); oldHud = client.options.hudHidden;
            world = client.world; oldTime = world == null ? 0 : client.world.getTimeOfDay();
        } else {
            client.options.getFov().setValue(oldFov); client.options.getGamma().setValue(oldGamma); client.options.hudHidden = oldHud;
            if (world != null && client.world == world) client.world.getLevelProperties().setTimeOfDay(oldTime);
            world = null;
        }
    }
    public boolean active() { return active; }
    public float roll() { return roll.get().floatValue(); }
    @Override public String displayValue() { return active ? "ACTIVE" : filter.get().name(); }
    @Override protected void onDisable() { if (active) setActive(false, MinecraftClient.getInstance()); }
}
