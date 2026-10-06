package net.lunacy.visuals.module;

import net.lunacy.visuals.event.EventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ModuleManager implements AutoCloseable {
    private final EventBus eventBus;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public <T extends Module> T register(T module) {
        if (modules.putIfAbsent(module.id(), module) != null) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        module.attach(eventBus);
        return module;
    }

    public Optional<Module> find(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }

    public List<Module> allVisible() {
        return modules.values().stream()
                .filter(module -> !module.isBlocked())
                .toList();
    }

    public List<Module> search(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return allVisible();
        }
        return modules.values().stream()
                .filter(module -> !module.isBlocked())
                .filter(module -> module.name().toLowerCase(Locale.ROOT).contains(normalized)
                        || module.description().toLowerCase(Locale.ROOT).contains(normalized)
                        || module.id().contains(normalized))
                .toList();
    }

    @Override
    public void close() {
        modules.values().forEach(module -> module.setEnabled(false));
    }
}
