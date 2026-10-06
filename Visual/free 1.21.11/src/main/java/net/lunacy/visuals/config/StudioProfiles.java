package net.lunacy.visuals.config;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
/** Portable profiles, isolated by game version. Server addresses stay in the local bindings file. */
public final class StudioProfiles {
    private final ConfigManager config;
    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Properties bindings = new Properties();
    private String server = "";
    private JsonObject beforeServer;
    public StudioProfiles(ConfigManager config, Path directory) {
        this.config = config; this.directory = directory;
        try {
            Files.createDirectories(directory.resolve("imports"));
            Files.createDirectories(directory.resolve("exports"));
            Path file = directory.resolve("servers.properties");
            if (Files.exists(file)) try (var reader = Files.newBufferedReader(file)) { bindings.load(reader); }
        } catch (IOException ex) { throw new IllegalStateException("Cannot open profiles", ex); }
    }
    public static String safeName(String name) {
        if (name == null || !name.matches("[\\p{L}\\p{N}_ -]{1,48}") || name.isBlank())
            throw new IllegalArgumentException("Use 1–48 letters, numbers, spaces, - or _");
        return name.strip();
    }
    public List<String> list() throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replaceFirst("\\.json$", "")).sorted().toList();
        }
    }
    private void write(Path path, JsonObject root) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
        try { Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private JsonObject read(Path path) throws IOException {
        if (Files.size(path) > 2_000_000) throw new IOException("Profile exceeds 2 MB");
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("modules") || !root.get("modules").isJsonObject()) throw new IOException("Invalid profile");
            return root;
        } catch (RuntimeException ex) { throw new IOException("Invalid profile JSON", ex); }
    }
    public void save(String name) throws IOException { write(directory.resolve(safeName(name)+".json"), config.snapshot()); }
    public void load(String name) throws IOException { config.restore(read(directory.resolve(safeName(name)+".json"))); }
    public Path exportProfile(String name) throws IOException {
        Path output = directory.resolve("exports").resolve(safeName(name)+".json");
        write(output, config.snapshot()); return output;
    }
    public void importProfile(String name) throws IOException {
        String safe = safeName(name);
        JsonObject root = read(directory.resolve("imports").resolve(safe+".json"));
        write(directory.resolve(safe+".json"), root);
    }
    public void bind(String name) throws IOException {
        if (server.isEmpty()) throw new IOException("Join a server first");
        save(name); bindings.setProperty(server, safeName(name)); persistBindings();
    }
    public void unbind() throws IOException { bindings.remove(server); persistBindings(); }
    private void persistBindings() throws IOException {
        Path tmp = directory.resolve("servers.properties.tmp");
        try (var writer = Files.newBufferedWriter(tmp)) { bindings.store(writer, "Local server/profile bindings"); }
        Files.move(tmp, directory.resolve("servers.properties"), StandardCopyOption.REPLACE_EXISTING);
    }
    public void switchServer(String next) throws IOException {
        if (Objects.equals(server, next)) return;
        if (beforeServer != null) { config.restore(beforeServer); beforeServer = null; }
        server = next;
        String profile = bindings.getProperty(next);
        if (profile != null) {
            JsonObject saved = config.snapshot();
            load(profile); beforeServer = saved;
        }
    }
    public void close() { if (beforeServer != null) { config.restore(beforeServer); beforeServer = null; } }
    public Path directory() { return directory; }
}
