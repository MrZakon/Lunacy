package net.lunacy.visuals.render;

import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.config.ClientSettings;

/** Central budget for particle-heavy visual modules. */
public final class VisualQuality {
    private VisualQuality() {}

    private static ClientSettings.ShaderQuality quality() {
        ClientRuntime runtime = ClientRuntime.getNullable();
        return runtime == null ? ClientSettings.ShaderQuality.BALANCED : runtime.settings().shaderQuality();
    }

    public static int cadence(int balanced) {
        return switch (quality()) {
            case PERFORMANCE -> balanced + 2;
            case BALANCED -> balanced;
            case QUALITY -> Math.max(1, balanced - 1);
        };
    }

    public static int particles(int requested) {
        double multiplier = switch (quality()) {
            case PERFORMANCE -> 0.5;
            case BALANCED -> 0.75;
            case QUALITY -> 1.0;
        };
        ClientRuntime runtime = ClientRuntime.getNullable();
        var adaptive = runtime == null ? null : runtime.module(net.lunacy.visuals.module.impl.visual.AdaptiveEffectsModule.class);
        if (adaptive != null) multiplier *= adaptive.factor();
        return Math.max(1, (int) Math.round(requested * multiplier));
    }
}
