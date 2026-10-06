package net.lunacy.visuals.render.shader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Загружает GLSL и раскрывает Mojang-import до передачи исходника OpenGL. */
public final class ShaderSourceLoader {
    private static final Pattern IMPORT = Pattern.compile("^\\s*#moj_import\\s+[<\"]([^>\"]+)[>\"]\\s*$");

    private ShaderSourceLoader() {}

    public static String load(ResourceManager manager, Identifier identifier) throws IOException {
        return load(manager, identifier, new HashSet<>());
    }

    private static String load(ResourceManager manager, Identifier identifier, Set<Identifier> stack) throws IOException {
        if (!stack.add(identifier)) {
            throw new IOException("Recursive GLSL import: " + identifier);
        }

        StringBuilder result = new StringBuilder(4096);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                manager.getResource(identifier).orElseThrow(() -> new IOException("Missing shader " + identifier))
                        .open(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = IMPORT.matcher(line);
                if (!matcher.matches()) {
                    result.append(line).append('\n');
                    continue;
                }
                Identifier imported = importId(identifier, matcher.group(1));
                result.append("// begin ").append(imported).append('\n');
                result.append(load(manager, imported, stack));
                result.append("// end ").append(imported).append('\n');
            }
        } finally {
            stack.remove(identifier);
        }
        return result.toString();
    }

    private static Identifier importId(Identifier parent, String value) {
        Identifier requested = Identifier.parse(value);
        String path = requested.getPath();
        if (!path.startsWith("shaders/")) {
            path = "shaders/include/" + path;
        }
        String namespace = value.indexOf(':') >= 0 ? requested.getNamespace() : parent.getNamespace();
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
