package net.lunacy.visuals.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.lunacy.visuals.ClientRuntime;
import net.lunacy.visuals.LunacyVisuals;
import net.lunacy.visuals.animation.Easing;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Renders a smooth, clean neon welcome signature when the title screen opens. */
public final class StartupWelcomeAnimation {
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "mainmenu/welcome.json");
    private static final Identifier LV_LOGO = Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "textures/gui/lv_logo.png");
    private static final double DEFAULT_FIRST_FRAME = 0;
    private static final double DEFAULT_LAST_FRAME = 493;
    private static final double DEFAULT_FPS = 60;
    private static final double THICKNESS = 3.6;

    private final List<Point> path = new ArrayList<>();
    private final List<Double> cumulativeLength = new ArrayList<>();
    private static boolean alreadyPlayedSession = false;
    private boolean loaded;
    private boolean completed;
    private long startedAtNanos;
    private double firstFrame = DEFAULT_FIRST_FRAME;
    private double lastFrame = DEFAULT_LAST_FRAME;
    private double fps = DEFAULT_FPS;
    private double minX;
    private double maxX;
    private double minY;
    private double maxY;
    private double totalLength;

    public boolean render(GuiGraphicsExtractor context, int width, int height) {
        if (completed || alreadyPlayedSession) return false;
        if (!loaded) load();
        if (startedAtNanos == 0) startedAtNanos = System.nanoTime();

        boolean reducedMotion = ClientRuntime.get().settings().reducedMotion();
        double sourceDuration = Math.max(1.0, (lastFrame - firstFrame) / Math.max(1.0, fps));
        double duration = reducedMotion ? 7.0 : Math.clamp(sourceDuration * 1.25, 9.8, 10.5);
        double age = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0;
        if (age >= duration) {
            completed = true;
            alreadyPlayedSession = true;
            return false;
        }

        double progress = Math.clamp(age / duration, 0.0, 1.0);
        double fade = Easing.EASE_OUT_CUBIC.applyAsDouble(Math.clamp(age / 0.62, 0.0, 1.0));
        if (age > duration - 1.10) {
            fade *= 1.0 - Easing.EASE_IN_OUT_QUAD.applyAsDouble((age - duration + 1.10) / 1.10);
        }

        context.fillGradient(0, 0, width, height,
                ColorUtil.withAlpha(0xFF050907, (int) (215 * fade)),
                ColorUtil.withAlpha(0xFF0C0612, (int) (225 * fade)));
        UiRenderer.horizontalGradient(context, 0, 0, width, height,
                ColorUtil.withAlpha(LunacyMenuChrome.GREEN, (int) (30 * fade)),
                ColorUtil.withAlpha(LunacyMenuChrome.VIOLET, (int) (25 * fade)));

        double drawPhase = smootherStep(Math.clamp(progress / 0.84, 0.0, 1.0));
        drawPath(context, width, height, drawPhase,
                fade * (1.0 - Math.clamp((progress - 0.86) / 0.10, 0.0, 1.0)));
        drawBrand(context, width, height, fade * smootherStep((progress - 0.83) / 0.13));
        return true;
    }

    public void restart() {
        if (alreadyPlayedSession) return;
        completed = false;
        startedAtNanos = 0;
    }

    public boolean isActive() {
        return !completed && !alreadyPlayedSession;
    }

    public void skip() {
        completed = true;
        alreadyPlayedSession = true;
    }

    private void drawBrand(GuiGraphicsExtractor context, int width, int height, double fade) {
        if (fade <= 0.001) return;
        double size = Math.clamp(Math.min(width, height) * 0.20, 92, 156);
        double y = height * 0.5 - size * 0.62;
        int tint = (Math.clamp((int) Math.round(fade * 255), 0, 255) << 24) | 0x00FFFFFF;
        UiRenderer.texture(context, LV_LOGO, width * 0.5 - size * 0.5, y,
                size, size, 128, 128, tint);
    }

    private void drawPath(GuiGraphicsExtractor context, int width, int height,
                          double reveal, double alpha) {
        if (path.size() < 2 || totalLength <= 0 || reveal <= 0 || alpha <= 0.001) return;

        double sourceWidth = Math.max(1.0, maxX - minX);
        double sourceHeight = Math.max(1.0, maxY - minY);
        double scale = Math.min(width * 0.82 / sourceWidth, height * 0.34 / sourceHeight);
        double centerX = width * 0.5;
        double centerY = height * 0.5 + 8;
        double visibleLength = totalLength * reveal;
        int a = (int) Math.round(255 * alpha);
        if (a <= 0) return;

        double radius = THICKNESS * 0.5;
        double overlap = Math.max(0.6, THICKNESS * 0.28);

        Point first = screenPoint(path.get(0), scale, centerX, centerY);
        drawRoundCap(context, first.x, first.y, radius, gradientColor(0.0, a));

        Point lastPoint = null;
        for (int i = 0; i < path.size() - 1; i++) {
            double segmentStart = cumulativeLength.get(i);
            double segmentEnd = cumulativeLength.get(i + 1);
            if (segmentStart >= visibleLength) break;
            double sourceLength = segmentEnd - segmentStart;
            if (sourceLength <= 0.0001) continue;

            double part = Math.clamp((visibleLength - segmentStart) / sourceLength, 0.0, 1.0);
            Point pA = screenPoint(path.get(i), scale, centerX, centerY);
            Point pB = screenPoint(path.get(i + 1), scale, centerX, centerY);
            double dx = (pB.x - pA.x) * part;
            double dy = (pB.y - pA.y) * part;
            double length = Math.hypot(dx, dy);
            if (length <= 0.01) continue;

            double t = Math.clamp((segmentStart + sourceLength * part * 0.5) / totalLength, 0.0, 1.0);
            int color = gradientColor(t, a);

            context.pose().pushMatrix();
            context.pose().translate((float) pA.x, (float) pA.y);
            context.pose().rotate((float) Math.atan2(dy, dx));
            context.fill((int) Math.round(-overlap), (int) Math.round(-radius),
                    (int) Math.round(length + overlap), (int) Math.round(radius), color);
            context.pose().popMatrix();

            lastPoint = new Point(pA.x + dx, pA.y + dy);
        }

        if (lastPoint != null) {
            double tipT = Math.clamp(visibleLength / totalLength, 0.0, 1.0);
            drawRoundCap(context, lastPoint.x, lastPoint.y, radius, gradientColor(tipT, a));
        }
    }

    private static void drawRoundCap(GuiGraphicsExtractor context, double cx, double cy, double radius, int color) {
        int r = (int) Math.ceil(radius);
        double rSquared = radius * radius;
        for (int dy = -r; dy <= r; dy++) {
            double diff = rSquared - dy * dy;
            if (diff < 0) continue;
            double dx = Math.sqrt(diff);
            context.fill((int) Math.round(cx - dx), (int) Math.round(cy + dy),
                    (int) Math.round(cx + dx + 1), (int) Math.round(cy + dy + 1), color);
        }
    }

    private Point screenPoint(Point point, double scale, double centerX, double centerY) {
        return new Point(
                centerX + (point.x - (minX + maxX) * 0.5) * scale,
                centerY + (point.y - (minY + maxY) * 0.5) * scale);
    }

    private void load() {
        loaded = true;
        try (InputStreamReader reader = new InputStreamReader(Minecraft.getInstance().getResourceManager()
                .getResource(SOURCE).orElseThrow().open(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            fps = number(root, "fr", DEFAULT_FPS);
            firstFrame = number(root, "ip", DEFAULT_FIRST_FRAME);
            lastFrame = number(root, "op", DEFAULT_LAST_FRAME);
            JsonObject shape = findShape(root.get("layers"));
            if (shape == null) throw new IllegalStateException("welcome.json has no drawable path");
            flatten(shape);
        } catch (Exception exception) {
            LunacyVisuals.LOGGER.warn("Unable to decode startup welcome animation", exception);
        }
    }

    private static JsonObject findShape(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                JsonObject found = findShape(child);
                if (found != null) return found;
            }
            return null;
        }
        if (!element.isJsonObject()) return null;

        JsonObject object = element.getAsJsonObject();
        if (object.has("ty") && "sh".equals(object.get("ty").getAsString()) && object.has("ks")) {
            JsonElement keyframes = object.getAsJsonObject("ks").get("k");
            JsonObject shape = finalShape(keyframes);
            if (shape != null) return shape;
        }
        for (String key : new String[]{"it", "shapes", "layers"}) {
            if (!object.has(key)) continue;
            JsonObject found = findShape(object.get(key));
            if (found != null) return found;
        }
        return null;
    }

    private static JsonObject finalShape(JsonElement keyframes) {
        if (keyframes == null || keyframes.isJsonNull()) return null;
        if (keyframes.isJsonObject()) {
            JsonObject object = keyframes.getAsJsonObject();
            return object.has("v") ? object : null;
        }
        if (!keyframes.isJsonArray()) return null;
        JsonArray array = keyframes.getAsJsonArray();
        for (int index = array.size() - 1; index >= 0; index--) {
            JsonElement frameElement = array.get(index);
            if (!frameElement.isJsonObject()) continue;
            JsonObject frame = frameElement.getAsJsonObject();
            if (frame.has("v")) return frame;
            if (!frame.has("s") || !frame.get("s").isJsonArray()) continue;
            JsonArray shapes = frame.getAsJsonArray("s");
            if (!shapes.isEmpty() && shapes.get(0).isJsonObject()
                    && shapes.get(0).getAsJsonObject().has("v")) {
                return shapes.get(0).getAsJsonObject();
            }
        }
        return null;
    }

    private void flatten(JsonObject shape) {
        JsonArray vertices = shape.getAsJsonArray("v");
        JsonArray incoming = shape.getAsJsonArray("i");
        JsonArray outgoing = shape.getAsJsonArray("o");
        for (int index = 0; index < vertices.size() - 1; index++) {
            Point p0 = point(vertices.get(index));
            Point p3 = point(vertices.get(index + 1));
            Point p1 = p0.add(point(outgoing.get(index)));
            Point p2 = p3.add(point(incoming.get(index + 1)));
            for (int sample = 0; sample < 16; sample++) {
                double t = sample / 16.0;
                path.add(cubic(p0, p1, p2, p3, t));
            }
        }
        path.add(point(vertices.get(vertices.size() - 1)));
        rebuildLengths();
        minX = path.stream().mapToDouble(Point::x).min().orElse(0);
        maxX = path.stream().mapToDouble(Point::x).max().orElse(1);
        minY = path.stream().mapToDouble(Point::y).min().orElse(0);
        maxY = path.stream().mapToDouble(Point::y).max().orElse(1);
    }

    private void rebuildLengths() {
        cumulativeLength.clear();
        cumulativeLength.add(0.0);
        totalLength = 0;
        for (int index = 1; index < path.size(); index++) {
            Point previous = path.get(index - 1);
            Point current = path.get(index);
            totalLength += Math.hypot(current.x - previous.x, current.y - previous.y);
            cumulativeLength.add(totalLength);
        }
    }

    private static Point point(JsonElement element) {
        JsonArray values = element.getAsJsonArray();
        return new Point(values.get(0).getAsDouble(), values.get(1).getAsDouble());
    }

    private static Point cubic(Point a, Point b, Point c, Point d, double t) {
        double u = 1.0 - t;
        double aa = u * u * u;
        double bb = 3.0 * u * u * t;
        double cc = 3.0 * u * t * t;
        double dd = t * t * t;
        return new Point(a.x * aa + b.x * bb + c.x * cc + d.x * dd,
                a.y * aa + b.y * bb + c.y * cc + d.y * dd);
    }

    private static double number(JsonObject object, String key, double fallback) {
        return object.has(key) ? object.get(key).getAsDouble() : fallback;
    }

    private static double smootherStep(double value) {
        double t = Math.clamp(value, 0.0, 1.0);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static int gradientColor(double t, int alpha) {
        int a = LunacyMenuChrome.GREEN;
        int b = LunacyMenuChrome.VIOLET;
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (Math.clamp(alpha, 0, 255) << 24) | (r << 16) | (g << 8) | bl;
    }

    private record Point(double x, double y) {
        Point add(Point other) {
            return new Point(x + other.x, y + other.y);
        }
    }
}