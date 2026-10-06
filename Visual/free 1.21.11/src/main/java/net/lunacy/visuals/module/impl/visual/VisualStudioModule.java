package net.lunacy.visuals.module.impl.visual;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.config.AppearancePresets;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.EnumSetting;

/** ClickGUI controls for presets and portable/server-bound profiles. */
public final class VisualStudioModule extends Module {
    public enum Slot { SLOT_1, SLOT_2, SLOT_3, PVP, SCREENSHOTS }
    public enum Action { NONE, SAVE, LOAD, EXPORT, IMPORT, BIND_SERVER, UNBIND_SERVER }
    private final EnumSetting<AppearancePresets.Preset> preset = add(new EnumSetting<>(
            "preset", "Пресет", "Общий стиль интерфейса, мира и косметики",
            AppearancePresets.Preset.ECLIPSE, AppearancePresets.Preset.class));
    private final EnumSetting<Slot> slot = add(new EnumSetting<>(
            "profile", "Профиль", "Слот локального профиля", Slot.SLOT_1, Slot.class));
    private final EnumSetting<Action> action = add(new EnumSetting<>(
            "action", "Действие", "SAVE, LOAD, EXPORT, IMPORT или привязка к серверу", Action.NONE, Action.class));
    private boolean handling;

    public VisualStudioModule() {
        super("visual_studio", "Visual Studio", "Пресеты, переносимые профили и настройки для серверов", ModuleCategory.COSMETICS);
        preset.onChanged(value -> {
            var runtime = ClientRuntime.getNullable();
            if (runtime != null) AppearancePresets.apply(value, runtime.modules(), runtime.settings());
        });
        action.onChanged(this::run);
    }

    private void run(Action requested) {
        if (handling || requested == Action.NONE) return;
        handling = true;
        try {
            var profiles = ClientRuntime.get().profiles();
            String name = slot.get().name().toLowerCase(java.util.Locale.ROOT);
            switch (requested) {
                case SAVE -> profiles.save(name);
                case LOAD -> profiles.load(name);
                case EXPORT -> profiles.exportProfile(name);
                case IMPORT -> profiles.importProfile(name);
                case BIND_SERVER -> profiles.bind(name);
                case UNBIND_SERVER -> profiles.unbind();
                default -> { }
            }
        } catch (Exception exception) {
            LunacyVisuals.LOGGER.warn("Visual Studio action {} failed", requested, exception);
        } finally {
            action.set(Action.NONE);
            handling = false;
        }
    }

    @Override public String displayValue() { return preset.get().name(); }
}
