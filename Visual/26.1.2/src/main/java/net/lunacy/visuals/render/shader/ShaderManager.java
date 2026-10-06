package net.lunacy.visuals.render.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.lunacy.visuals.LunacyVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Централизованно компилирует и освобождает direct-GLSL программы. */
public final class ShaderManager implements AutoCloseable {
    private final Map<String, ShaderProgram> programs = new LinkedHashMap<>();
    private boolean initialized;

    public void initializeOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        if (initialized) return;
        initialized = true;
        // Ресурсы проверены Gradle-задачей; ошибка драйвера не должна ломать запуск клиента.
        load("liquid_glass", "core/liquidglassnew");
        load("rounded_outline", "core/dw_rounded_outline");
        load("border", "core/border");
        load("msdf_font", "core/msdf_font");
    }

    public Optional<ShaderProgram> find(String name) {
        return Optional.ofNullable(programs.get(name));
    }

    public boolean isInitialized() { return initialized; }

    private void load(String name, String basePath) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        try {
            String vertex = ShaderSourceLoader.load(resources,
                    Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "shaders/" + basePath + ".vsh"));
            String fragment = ShaderSourceLoader.load(resources,
                    Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "shaders/" + basePath + ".fsh"));
            programs.put(name, new ShaderProgram(vertex, fragment, name));
        } catch (IOException | RuntimeException exception) {
            LunacyVisuals.LOGGER.warn("Custom shader '{}' is unavailable; UI fallback stays active", name, exception);
        }
    }

    public void reload() {
        close();
        initialized = false;
        initializeOnRenderThread();
    }

    @Override
    public void close() {
        programs.values().forEach(ShaderProgram::close);
        programs.clear();
        initialized = false;
    }
}
