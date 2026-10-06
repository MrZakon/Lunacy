package net.lunacy.visuals.module.impl.visual;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.lunacy.visuals.event.Subscribe;
import net.lunacy.visuals.event.impl.Render2DEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.module.Module;
import net.lunacy.visuals.module.ModuleCategory;
import net.lunacy.visuals.module.setting.BooleanSetting;
import net.lunacy.visuals.module.setting.ColorSetting;
import net.lunacy.visuals.module.setting.ColorValue;
import net.lunacy.visuals.module.setting.DoubleSetting;
import net.lunacy.visuals.module.setting.EnumSetting;
import net.lunacy.visuals.render.ColorUtil;
import net.lunacy.visuals.render.LineRenderer;
import net.lunacy.visuals.render.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class AmbienceModule extends Module {
    public enum WeatherFx {
        OFF,
        SAKURA,
        NEON_SPARKS
    }

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
    private final BooleanSetting galaxySky = add(new BooleanSetting(
            "galaxy_sky", "Galaxy Sky", "Render cosmic stars and deep space nebulae", true));
    private final EnumSetting<WeatherFx> weatherFx = add(new EnumSetting<>(
            "weather_fx", "Weather FX", "Custom atmospheric falling particle effects", WeatherFx.SAKURA, WeatherFx.class));

    private ClientWorld trackedWorld;
    private long previousTime;
    private float previousRain;
    private float previousThunder;
    private final List<WeatherParticle> weatherParticles = new ArrayList<>();

    public AmbienceModule() {
        super("ambience", "Ambience / Custom World", "Restorable tint, time, cosmic sky, and custom weather FX",
                ModuleCategory.WORLD_VISUALS);
    }

    @Override public String displayValue() { return forceTime.get() ? String.valueOf(time.get().intValue()) : "TINT"; }
    public long timeOfDay() { return time.get().longValue(); }
    public boolean hidesWeather() { return weather.get(); }

    @Subscribe
    private void onTick(TickEvent.Client event) {
        ClientWorld world = event.client().world;
        if (world == null) {
            trackedWorld = null;
            weatherParticles.clear();
            return;
        }
        if (trackedWorld != world) {
            trackedWorld = world;
            previousTime = world.getTimeOfDay();
            previousRain = world.getRainGradient(1.0F);
            previousThunder = world.getThunderGradient(1.0F);
        }
        if (forceTime.get()) world.getLevelProperties().setTimeOfDay(timeOfDay());
        if (weather.get()) {
            world.setRainGradient(0.0F);
            world.setThunderGradient(0.0F);
        }

        if (weatherFx.get() != WeatherFx.OFF && event.client().player != null) {
            double px0 = event.client().player.getX();
            double py0 = event.client().player.getY();
            double pz0 = event.client().player.getZ();

            while (weatherParticles.size() < 120) {
                double px = px0 + (Math.random() - 0.5) * 24.0;
                double py = py0 + Math.random() * 12.0 + 2.0;
                double pz = pz0 + (Math.random() - 0.5) * 24.0;
                weatherParticles.add(new WeatherParticle(px, py, pz, Math.random() * 0.05 + 0.02, Math.random() * 360));
            }

            for (int i = weatherParticles.size() - 1; i >= 0; i--) {
                WeatherParticle p = weatherParticles.get(i);
                p.y -= p.fallSpeed;
                p.angle += 1.5;
                if (p.y < py0 - 4.0 || Math.hypot(p.x - px0, p.z - pz0) > 28.0) {
                    weatherParticles.remove(i);
                }
            }
        } else {
            weatherParticles.clear();
        }
    }

    @Subscribe
    private void onRender(Render2DEvent event) {
        if (strength.get() <= 0.001) return;
        int width = event.context().getScaledWindowWidth();
        int height = event.context().getScaledWindowHeight();
        int color = ColorUtil.withAlpha(tint.get().colorAt(0.5, System.currentTimeMillis()),
                (int) Math.round(strength.get() * 255));
        UiRenderer.rect(event.context(), 0, 0, width, height, color);
    }

    public void renderWorld(WorldRenderContext context) {
        if (!isEnabled() || context.matrices() == null || context.consumers() == null) return;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
            MatrixStack matrices = context.matrices();
            MatrixStack.Entry entry = matrices.peek();

            if (galaxySky.get()) {
                VertexConsumer stars = context.consumers().getBuffer(RenderLayers.linesTranslucent());
                long now = System.currentTimeMillis();
                double rot = (now % 120000) / 120000.0 * Math.PI * 2.0;

                for (int i = 0; i < 48; i++) {
                    double theta = (i * 0.2617) + rot;
                    double phi = ((i * 37) % 180) * Math.PI / 180.0;
                    double dist = 85.0;

                    float sx = (float) (Math.sin(phi) * Math.cos(theta) * dist);
                    float sy = (float) (Math.cos(phi) * dist + 25.0);
                    float sz = (float) (Math.sin(phi) * Math.sin(theta) * dist);

                    float r = (i % 2 == 0) ? 0.35f : 0.75f;
                    float g = (i % 3 == 0) ? 0.85f : 0.45f;
                    float b = 1.0f;

                    LineRenderer.line(entry, stars,
                            sx, sy, sz,
                            sx + 0.4f, sy + 0.4f, sz,
                            r, g, b, 0.75f, 2.0f);
                }
            }

            if (weatherFx.get() != WeatherFx.OFF && !weatherParticles.isEmpty()) {
                VertexConsumer lines = context.consumers().getBuffer(RenderLayers.linesTranslucent());
                boolean isSakura = weatherFx.get() == WeatherFx.SAKURA;
                float r = isSakura ? 1.0f : 0.35f;
                float g = isSakura ? 0.65f : 1.0f;
                float b = isSakura ? 0.82f : 0.85f;

                for (WeatherParticle p : weatherParticles) {
                    float px = (float) (p.x - camera.x);
                    float py = (float) (p.y - camera.y);
                    float pz = (float) (p.z - camera.z);

                    double rad = Math.toRadians(p.angle);
                    float dx = (float) Math.cos(rad) * 0.12f;
                    float dz = (float) Math.sin(rad) * 0.12f;

                    LineRenderer.line(entry, lines,
                            px - dx, py, pz - dz,
                            px + dx, py - 0.08f, pz + dz,
                            r, g, b, 0.85f, 1.5f);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void resetState() {
        if (trackedWorld != null) {
            trackedWorld.getLevelProperties().setTimeOfDay(previousTime);
            trackedWorld.setRainGradient(previousRain);
            trackedWorld.setThunderGradient(previousThunder);
            trackedWorld = null;
        }
        weatherParticles.clear();
    }

    private static class WeatherParticle {
        double x, y, z, fallSpeed, angle;
        WeatherParticle(double x, double y, double z, double fallSpeed, double angle) {
            this.x = x; this.y = y; this.z = z; this.fallSpeed = fallSpeed; this.angle = angle;
        }
    }
}