package onix.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

final class Shaders {
	private Shaders() {
	}

	/**
	 * Compiles and links a program. Attribute names are bound to locations 0, 1, 2...
	 * in the order given. Returns 0 and logs the reason if the driver rejects it.
	 */
	static int link(String vertex, String fragment, String... attributes) {
		int vs = compile(GL20.GL_VERTEX_SHADER, vertex);
		int fs = compile(GL20.GL_FRAGMENT_SHADER, fragment);
		if (vs == 0 || fs == 0) return 0;
		int program = GL20.glCreateProgram();
		GL20.glAttachShader(program, vs);
		GL20.glAttachShader(program, fs);
		for (int i = 0; i < attributes.length; i++) GL20.glBindAttribLocation(program, i, attributes[i]);
		GL20.glLinkProgram(program);
		GL20.glDeleteShader(vs);
		GL20.glDeleteShader(fs);
		if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			System.err.println("[Onix] Shader link failed: " + GL20.glGetProgramInfoLog(program, 4096));
			GL20.glDeleteProgram(program);
			return 0;
		}
		return program;
	}

	private static int compile(int type, String source) {
		int shader = GL20.glCreateShader(type);
		GL20.glShaderSource(shader, source);
		GL20.glCompileShader(shader);
		if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			System.err.println("[Onix] Shader compile failed: " + GL20.glGetShaderInfoLog(shader, 4096));
			GL20.glDeleteShader(shader);
			return 0;
		}
		return shader;
	}
}
