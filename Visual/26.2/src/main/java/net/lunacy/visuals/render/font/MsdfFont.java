package net.lunacy.visuals.render.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Метрики атласа, созданного msdf-atlas-gen. */
public final class MsdfFont {
    private final Map<Integer, Glyph> glyphs;
    private final float lineHeight;
    private final float ascender;
    private final float distanceRange;
    private final float atlasWidth;
    private final float atlasHeight;
    private final Identifier texture;

    private MsdfFont(Map<Integer, Glyph> glyphs, float lineHeight, float ascender, float distanceRange,
                     float atlasWidth, float atlasHeight, Identifier texture) {
        this.glyphs = glyphs;
        this.lineHeight = lineHeight;
        this.ascender = ascender;
        this.distanceRange = distanceRange;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.texture = texture;
    }

    public static MsdfFont load(ResourceManager manager, Identifier metrics, Identifier texture) throws IOException {
        JsonObject root;
        try (InputStreamReader reader = new InputStreamReader(
                manager.getResource(metrics).orElseThrow(() -> new IOException("Missing font " + metrics))
                        .open(), StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonObject atlas = root.getAsJsonObject("atlas");
        JsonObject metricValues = root.getAsJsonObject("metrics");
        Map<Integer, Glyph> glyphs = new HashMap<>();
        JsonArray sourceGlyphs = root.getAsJsonArray("glyphs");
        sourceGlyphs.forEach(element -> {
            JsonObject glyph = element.getAsJsonObject();
            JsonObject plane = glyph.has("planeBounds") ? glyph.getAsJsonObject("planeBounds") : null;
            JsonObject bounds = glyph.has("atlasBounds") ? glyph.getAsJsonObject("atlasBounds") : null;
            glyphs.put(glyph.get("unicode").getAsInt(), new Glyph(
                    glyph.get("advance").getAsFloat(), readBounds(plane), readBounds(bounds)));
        });
        return new MsdfFont(glyphs, metricValues.get("lineHeight").getAsFloat(),
                metricValues.get("ascender").getAsFloat(), atlas.get("distanceRange").getAsFloat(),
                atlas.get("width").getAsFloat(), atlas.get("height").getAsFloat(), texture);
    }

    private static Bounds readBounds(JsonObject object) {
        if (object == null) return null;
        return new Bounds(object.get("left").getAsFloat(), object.get("bottom").getAsFloat(),
                object.get("right").getAsFloat(), object.get("top").getAsFloat());
    }

    public Glyph glyph(int codePoint) { return glyphs.get(codePoint); }
    public float lineHeight() { return lineHeight; }
    public float ascender() { return ascender; }
    public float distanceRange() { return distanceRange; }
    public float atlasWidth() { return atlasWidth; }
    public float atlasHeight() { return atlasHeight; }
    public Identifier texture() { return texture; }

    public record Glyph(float advance, Bounds plane, Bounds atlas) {}
    public record Bounds(float left, float bottom, float right, float top) {}
}
