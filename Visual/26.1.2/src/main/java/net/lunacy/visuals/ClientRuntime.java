package net.lunacy.visuals;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.lunacy.visuals.config.ConfigManager;
import net.lunacy.visuals.config.ClientSettings;
import net.lunacy.visuals.config.GuiScaleMemory;
import net.lunacy.visuals.config.StudioProfiles;
import net.lunacy.visuals.event.EventBus;
import net.lunacy.visuals.event.impl.KeyEvent;
import net.lunacy.visuals.event.impl.MouseEvent;
import net.lunacy.visuals.event.impl.Render2DEvent;
import net.lunacy.visuals.event.impl.TickEvent;
import net.lunacy.visuals.gui.ClickGuiScreen;
import net.lunacy.visuals.gui.LunacyPauseScreen;
import net.lunacy.visuals.gui.LunacyBranding;
import net.lunacy.visuals.gui.LunacyTitleScreen;
import net.lunacy.visuals.gui.StartupWelcomeAnimation;
import net.lunacy.visuals.hud.HudLayout;
import net.lunacy.visuals.module.ModuleRegistry;
import net.lunacy.visuals.module.ModuleManager;
import net.lunacy.visuals.module.impl.hud.ClientRuntimeFrame;
import net.lunacy.visuals.module.impl.visual.ItemGlintOverlayModule;
import net.lunacy.visuals.render.RenderEngine;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** Владелец клиентских сервисов и всех Fabric callback-регистраций. */
public final class ClientRuntime implements AutoCloseable {
    private static ClientRuntime instance;

    private final EventBus events = new EventBus();
    private final ModuleManager modules = new ModuleManager(events);
    private final RenderEngine renderEngine = new RenderEngine();
    private final ClientSettings settings = new ClientSettings();
    private final HudLayout hudLayout = new HudLayout();
    private final Render2DEvent render2DEvent = new Render2DEvent();
    private final TickEvent.Client tickEvent = new TickEvent.Client();
    private final StartupWelcomeAnimation startupWelcome = new StartupWelcomeAnimation();
    private ConfigManager config;
    private StudioProfiles profiles;
    private KeyMapping clickGuiKey;
    private Object lastWorld;
    private boolean windowBrandingApplied;
    private int lastPersistedGuiScale = Integer.MIN_VALUE;

    public static void initialize() {
        if (instance != null) return;
        instance = new ClientRuntime();
        instance.start();
    }

    private void start() {
        ModuleRegistry.registerAll(modules);
        enableDefaults();
        config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("lunacy_visuals.json"), modules, settings);
        config.load();
        profiles = new StudioProfiles(config, FabricLoader.getInstance().getConfigDir().resolve("lunacy_visuals_profiles/26.1.2"));
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            var tooltips = module(net.lunacy.visuals.module.impl.visual.EnhancedTooltipsModule.class);
            if (tooltips != null && tooltips.isEnabled()) tooltips.append(stack, lines);
        });
        ensureWatermark();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "main"));
        clickGuiKey = new KeyMapping(
                "key.lunacyvisuals.clickgui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(LunacyVisuals.MOD_ID, "visual_overlay"), (context, counter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (client.screen != null && !(client.screen instanceof net.minecraft.client.gui.screens.ChatScreen) && !(client.screen instanceof ClickGuiScreen)) {
                return;
            }
            renderEngine.beginFrame(settings.customShaders());
            ClientRuntimeFrame.update();
            hudLayout.beginFrame();
            events.post(render2DEvent.reset(context, counter.getGameTimeDeltaPartialTick(true)));
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        LunacyVisuals.LOGGER.info("LunacyVisual initialized with {} modules", modules.all().size());
    }

    private void enableDefaults() {
        for (String id : new String[]{"watermark", "custom_crosshair"}) {
            modules.find(id).ifPresent(module -> module.setEnabled(true));
        }
    }

    private void onTick(Minecraft client) {
        net.lunacy.visuals.user.PresenceSync.tick(client);
        ensureWatermark();
        persistGuiScale(client);
        if (!windowBrandingApplied) {
            windowBrandingApplied = LunacyBranding.applyWindowIcon(client);
        }
        config.tick();
        events.post(tickEvent.reset(client));
        try {
            var server = client.getCurrentServer();
            profiles.switchServer(server == null ? "" : server.ip);
        } catch (java.io.IOException exception) {
            LunacyVisuals.LOGGER.warn("Could not switch Lunacy profile", exception);
        }

        while (clickGuiKey.consumeClick()) {
            client.setScreen(new ClickGuiScreen());
        }

        if (client.level != lastWorld) lastWorld = client.level;

        if (client.screen instanceof TitleScreen && !(client.screen instanceof LunacyTitleScreen)) {
            client.setScreen(new LunacyTitleScreen());
        } else if (client.screen instanceof PauseScreen && !(client.screen instanceof LunacyPauseScreen)) {
            client.setScreen(new LunacyPauseScreen());
        }
    }

    private void persistGuiScale(Minecraft client) {
        if (client.options == null) return;
        int current = client.options.guiScale().get();
        if (current == lastPersistedGuiScale) return;
        lastPersistedGuiScale = current;
        GuiScaleMemory.write(FabricLoader.getInstance().getConfigDir(), current);
    }

    public boolean onKey(int key, int scanCode, int action, int modifiers) {
        KeyEvent event = new KeyEvent(key, scanCode, action, modifiers);
        events.post(event);
        return event.isCancelled();
    }

    public boolean onMouseButton(int button, int action, int modifiers) {
        if (action == 1) {
            if (button == 0) {
                net.lunacy.visuals.module.impl.hud.KeystrokesModule.onLeftClick();
            } else if (button == 1) {
                net.lunacy.visuals.module.impl.hud.KeystrokesModule.onRightClick();
            }
        }
        MouseEvent event = MouseEvent.button(button, action, modifiers);
        events.post(event);
        return event.isCancelled();
    }

    public boolean onMouseScroll(double horizontal, double vertical) {
        MouseEvent event = MouseEvent.scroll(horizontal, vertical);
        events.post(event);
        return event.isCancelled();
    }

    private void ensureWatermark() {
        modules.find("watermark").ifPresent(module -> module.setEnabled(true));
    }

    public <T extends net.lunacy.visuals.module.Module> T module(Class<T> type) {
        return modules.all().stream().filter(type::isInstance).map(type::cast).findFirst().orElse(null);
    }

    public static ClientRuntime get() {
        if (instance == null) throw new IllegalStateException("LunacyVisual is not initialized");
        return instance;
    }

    public static ClientRuntime getNullable() { return instance; }
    public EventBus events() { return events; }
    public ModuleManager modules() { return modules; }
    public ConfigManager config() { return config; }
    public StudioProfiles profiles() { return profiles; }
    public ClientSettings settings() { return settings; }
    public RenderEngine renderEngine() { return renderEngine; }
    public HudLayout hudLayout() { return hudLayout; }
    public StartupWelcomeAnimation startupWelcome() { return startupWelcome; }

    @Override
    public void close() {
        net.lunacy.visuals.user.PresenceSync.leave();
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            persistGuiScale(client);
        }
        if (profiles != null) profiles.close();
        if (config != null) config.flush();
        modules.close();
        renderEngine.close();
    }
}

