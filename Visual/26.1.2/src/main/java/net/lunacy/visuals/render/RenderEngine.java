package net.lunacy.visuals.render;

import net.lunacy.visuals.render.font.MsdfFontRenderer;
import net.lunacy.visuals.render.shader.ShaderManager;

/** Единый владелец GPU-ресурсов и шрифтов клиента. */
public final class RenderEngine implements AutoCloseable {
    private final ShaderManager shaders = new ShaderManager();
    private final MsdfFontRenderer fonts = new MsdfFontRenderer();

    public void beginFrame(boolean enableCustomShaders) {
        if (enableCustomShaders) shaders.initializeOnRenderThread();
        else if (shaders.isInitialized()) shaders.close();
        fonts.initialize();
    }

    public ShaderManager shaders() { return shaders; }
    public MsdfFontRenderer fonts() { return fonts; }

    @Override
    public void close() {
        fonts.close();
        shaders.close();
    }
}
