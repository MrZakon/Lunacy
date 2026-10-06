package net.lunacy.visuals.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/** DrawContext-compatible renderer for every MSDF face shipped with the original menu assets. */
public final class MsdfFontRenderer implements AutoCloseable {
    private static final float SUBPIXEL_PRECISION = 4.0F;
    private final Map<FontFace, LoadedFace> faces = new EnumMap<>(FontFace.class);
    private boolean failed;

    public void initialize() {
        if (!faces.isEmpty() || failed) return;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            for (FontFace face : FontFace.values()) {
                Identifier metrics = source(face, "json");
                Identifier image = source(face, "png");
                Identifier raster = Identifier.of(LunacyVisuals.MOD_ID, "runtime/font_" + face.file());
                MsdfFont font = MsdfFont.load(client.getResourceManager(), metrics, image);
                NativeImageBackedTexture texture = new LinearFontTexture(
                        () -> "Lunacy " + face.file() + " alpha atlas", rasterizeAtlas(client, font, image));
                client.getTextureManager().registerTexture(raster, texture);
                texture.upload();
                faces.put(face, new LoadedFace(font, raster));
            }
        } catch (IOException | RuntimeException exception) {
            close();
            failed = true;
            LunacyVisuals.LOGGER.warn("Could not initialize the bundled menu fonts; vanilla fallback stays active",
                    exception);
        }
    }

    public float width(String text, float size) {
        return width(FontFace.SF_REGULAR, text, size);
    }

    public float width(FontFace face, String text, float size) {
        initialize();
        LoadedFace loaded = faces.get(face);
        if (loaded == null) return MinecraftClient.getInstance().textRenderer.getWidth(text) * (size / 9.0F);
        float value = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            MsdfFont.Glyph glyph = loaded.font.glyph(codePoint);
            value += glyph == null ? fallbackWidth(codePoint, size) : glyph.advance() * size;
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    public void draw(DrawContext context, String text, float x, float y, float size, int color) {
        draw(context, FontFace.SF_REGULAR, text, x, y, size, color);
    }

    public void draw(DrawContext context, FontFace face, String text,
                     float x, float y, float size, int color) {
        initialize();
        LoadedFace loaded = faces.get(face);
        if (loaded == null) {
            float scale = size / 9.0F;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            context.drawText(MinecraftClient.getInstance().textRenderer, text, 0, 0, color, false);
            context.getMatrices().popMatrix();
            return;
        }

        // DrawContext textures use integer destination rectangles. Rendering at 4x coordinates
        // under a reciprocal matrix scale preserves quarter-pixel glyph positions and fixes the
        // uneven Cyrillic spacing produced by rounding every letter independently.
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(1.0F / SUBPIXEL_PRECISION, 1.0F / SUBPIXEL_PRECISION);
        try {
            MsdfFont font = loaded.font;
            float preciseSize = size * SUBPIXEL_PRECISION;
            float cursor = x * SUBPIXEL_PRECISION;
            float preciseY = y * SUBPIXEL_PRECISION;
            float baseline = preciseY + font.ascender() * preciseSize;
            for (int offset = 0; offset < text.length();) {
                int codePoint = text.codePointAt(offset);
                MsdfFont.Glyph glyph = font.glyph(codePoint);
                if (glyph != null && glyph.plane() != null && glyph.atlas() != null) {
                    MsdfFont.Bounds plane = glyph.plane();
                    MsdfFont.Bounds atlas = glyph.atlas();
                    double drawX = cursor + plane.left() * preciseSize;
                    double drawY = baseline - plane.top() * preciseSize;
                    double drawWidth = (plane.right() - plane.left()) * preciseSize;
                    double drawHeight = (plane.top() - plane.bottom()) * preciseSize;
                    int sourceWidth = Math.max(1, Math.round(atlas.right() - atlas.left()));
                    int sourceHeight = Math.max(1, Math.round(atlas.top() - atlas.bottom()));
                    float sourceY = font.atlasHeight() - atlas.top();
                    UiRenderer.textureRegion(context, loaded.raster, drawX, drawY, drawWidth, drawHeight,
                            atlas.left(), sourceY, sourceWidth, sourceHeight,
                            Math.round(font.atlasWidth()), Math.round(font.atlasHeight()), color);
                } else if (glyph == null && !Character.isWhitespace(codePoint)) {
                    drawFallback(context, codePoint, cursor, preciseY, preciseSize, color);
                }
                cursor += glyph == null ? fallbackWidth(codePoint, preciseSize)
                        : glyph.advance() * preciseSize;
                offset += Character.charCount(codePoint);
            }
        } finally {
            context.getMatrices().popMatrix();
        }
    }

    private static float fallbackWidth(int codePoint, float size) {
        String value = new String(Character.toChars(codePoint));
        return MinecraftClient.getInstance().textRenderer.getWidth(value) * (size / 9.0F);
    }

    private static void drawFallback(DrawContext context, int codePoint, float x, float y,
                                     float size, int color) {
        String value = new String(Character.toChars(codePoint));
        float scale = size / 9.0F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, value, 0, 0, color, false);
        context.getMatrices().popMatrix();
    }

    private static Identifier source(FontFace face, String extension) {
        return Identifier.of(LunacyVisuals.MOD_ID, "fonts/" + face.file() + "." + extension);
    }

    private static NativeImage rasterizeAtlas(MinecraftClient client, MsdfFont font,
                                               Identifier source) throws IOException {
        var resource = client.getResourceManager().getResource(source)
                .orElseThrow(() -> new IOException("Missing font atlas " + source));
        try (InputStream stream = resource.getInputStream(); NativeImage image = NativeImage.read(stream)) {
            NativeImage output = new NativeImage(image.getWidth(), image.getHeight(), true);
            float range = Math.max(1.0F, font.distanceRange());
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getColorArgb(x, y);
                    float red = ((argb >>> 16) & 255) / 255.0F;
                    float green = ((argb >>> 8) & 255) / 255.0F;
                    float blue = (argb & 255) / 255.0F;
                    float median = Math.max(Math.min(red, green), Math.min(Math.max(red, green), blue));
                    // A slightly softer edge survives fractional GUI scaling without the
                    // stair-stepping produced by the former hard threshold.
                    float coverage = smoothstep(0.0F, 1.0F,
                            Math.clamp((median - 0.5F) * (range * 0.72F) + 0.5F, 0.0F, 1.0F));
                    output.setColorArgb(x, y, Math.round(coverage * 255.0F) << 24 | 0x00FFFFFF);
                }
            }
            return output;
        }
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static final class LinearFontTexture extends NativeImageBackedTexture {
        private LinearFontTexture(java.util.function.Supplier<String> label, NativeImage image) {
            super(label, image);
            // Dynamic textures default to NEAREST/REPEAT in 1.21.11. Font atlases need
            // linear sampling and clamped edges or small Cyrillic text becomes blocky.
            this.sampler = RenderSystem.getSamplerCache().get(
                    AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, false);
        }
    }

    @Override
    public void close() {
        MinecraftClient client = MinecraftClient.getInstance();
        for (LoadedFace face : faces.values()) client.getTextureManager().destroyTexture(face.raster);
        faces.clear();
        failed = false;
    }

    private record LoadedFace(MsdfFont font, Identifier raster) {}
}
