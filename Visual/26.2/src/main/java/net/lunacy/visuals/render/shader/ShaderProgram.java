package net.lunacy.visuals.render.shader;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/** Владелец OpenGL program object с кэшем uniform-location. */
public final class ShaderProgram implements AutoCloseable {
    private final Map<String, Integer> uniforms = new HashMap<>();
    private int handle;

    public ShaderProgram(String vertexSource, String fragmentSource, String debugName) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource, debugName + ".vsh");
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource, debugName + ".fsh");
        int program = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
                throw new IllegalStateException("Could not link " + debugName + ": " + GL20.glGetProgramInfoLog(program));
            }
            handle = program;
        } catch (RuntimeException exception) {
            GL20.glDeleteProgram(program);
            throw exception;
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    public void bind() { GL20.glUseProgram(handle); }
    public static void unbind() { GL20.glUseProgram(0); }

    public void uniform(String name, float value) { GL20.glUniform1f(location(name), value); }
    public void uniform(String name, int value) { GL20.glUniform1i(location(name), value); }
    public void uniform(String name, float x, float y) { GL20.glUniform2f(location(name), x, y); }
    public void uniform(String name, float x, float y, float z, float w) { GL20.glUniform4f(location(name), x, y, z, w); }

    public void uniform(String name, Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(16);
            matrix.get(values);
            GL20.glUniformMatrix4fv(location(name), false, values);
        }
    }

    public int handle() { return handle; }

    private int location(String name) {
        return uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(handle, key));
    }

    @Override
    public void close() {
        if (handle != 0) {
            GL20.glDeleteProgram(handle);
            handle = 0;
            uniforms.clear();
        }
    }

    private static int compile(int type, String source, String debugName) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Could not compile " + debugName + ": " + log);
        }
        return shader;
    }
}
