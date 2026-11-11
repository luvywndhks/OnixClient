package onix.gl;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Frosts the game image for glass panels.
 *
 * The screen is copied at half size, then shrunk twice and grown twice with
 * the dual Kawase filter. That is five cheap passes over small textures, and
 * it happens once per pass however many panels sample the result.
 */
final class Blur {
	private static final int LEVELS = 4;

	private final int[] fbo = new int[LEVELS], tex = new int[LEVELS];
	private final int[] w = new int[LEVELS], h = new int[LEVELS];
	private int sizeW, sizeH;
	private int down, up, downHalf, upHalf;
	private Boolean supported;

	boolean isSupported() {
		if (supported == null) supported = GLContext.getCapabilities().OpenGL30;
		return supported;
	}

	/** The frosted image, or 0 before the first capture. */
	int texture() {
		return tex[0];
	}

	void capture(int width, int height) {
		if (!isSupported() || width < 16 || height < 16) return;
		if (down == 0 && !compile()) {
			supported = false;
			return;
		}
		if (width != sizeW || height != sizeH) resize(width, height);

		int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		try {
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_BLEND);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			GL11.glDisable(GL11.GL_STENCIL_TEST);
			GL11.glDepthMask(false);
			GL11.glColorMask(true, true, true, true);

			// Whatever the game is drawing into right now is the picture to frost.
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, drawTarget);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo[0]);
			GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, w[0], h[0], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL20.glUseProgram(down);
			for (int i = 1; i < LEVELS; i++) pass(i - 1, i, downHalf);
			GL20.glUseProgram(up);
			for (int i = LEVELS - 1; i > 0; i--) pass(i, i - 1, upHalf);
		} finally {
			GL20.glUseProgram(program);
			GL11.glPopAttrib();
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
		}
	}

	private void pass(int from, int to, int halfUniform) {
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[to]);
		GL11.glViewport(0, 0, w[to], h[to]);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[from]);
		GL20.glUniform2f(halfUniform, 0.5f / w[from], 0.5f / h[from]);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex2f(-1f, -1f);
		GL11.glVertex2f(1f, -1f);
		GL11.glVertex2f(1f, 1f);
		GL11.glVertex2f(-1f, 1f);
		GL11.glEnd();
	}

	private void resize(int width, int height) {
		int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int boundFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		for (int i = 0; i < LEVELS; i++) {
			if (tex[i] == 0) {
				tex[i] = GL11.glGenTextures();
				fbo[i] = GL30.glGenFramebuffers();
			}
			w[i] = Math.max(1, width >> (i + 1));
			h[i] = Math.max(1, height >> (i + 1));
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w[i], h[i], 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[i]);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex[i], 0);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, boundTexture);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, boundFbo);
		sizeW = width;
		sizeH = height;
	}

	private boolean compile() {
		String vertex = "#version 120\nvarying vec2 vUv;\n"
				+ "void main() { vUv = gl_Vertex.xy * 0.5 + 0.5; gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0); }\n";
		String head = "#version 120\nuniform sampler2D uTex; uniform vec2 uHalf; varying vec2 vUv;\nvoid main() {\n";
		down = Shaders.link(vertex, head
				+ "  vec3 c = texture2D(uTex, vUv).rgb * 4.0;\n"
				+ "  c += texture2D(uTex, vUv - uHalf).rgb;\n"
				+ "  c += texture2D(uTex, vUv + uHalf).rgb;\n"
				+ "  c += texture2D(uTex, vUv + vec2(uHalf.x, -uHalf.y)).rgb;\n"
				+ "  c += texture2D(uTex, vUv - vec2(uHalf.x, -uHalf.y)).rgb;\n"
				+ "  gl_FragColor = vec4(c / 8.0, 1.0);\n}\n");
		up = Shaders.link(vertex, head
				+ "  vec3 c = texture2D(uTex, vUv + vec2(-uHalf.x * 2.0, 0.0)).rgb;\n"
				+ "  c += texture2D(uTex, vUv + vec2(-uHalf.x, uHalf.y)).rgb * 2.0;\n"
				+ "  c += texture2D(uTex, vUv + vec2(0.0, uHalf.y * 2.0)).rgb;\n"
				+ "  c += texture2D(uTex, vUv + vec2(uHalf.x, uHalf.y)).rgb * 2.0;\n"
				+ "  c += texture2D(uTex, vUv + vec2(uHalf.x * 2.0, 0.0)).rgb;\n"
				+ "  c += texture2D(uTex, vUv + vec2(uHalf.x, -uHalf.y)).rgb * 2.0;\n"
				+ "  c += texture2D(uTex, vUv + vec2(0.0, -uHalf.y * 2.0)).rgb;\n"
				+ "  c += texture2D(uTex, vUv + vec2(-uHalf.x, -uHalf.y)).rgb * 2.0;\n"
				+ "  gl_FragColor = vec4(c / 12.0, 1.0);\n}\n");
		if (down == 0 || up == 0) return false;
		downHalf = GL20.glGetUniformLocation(down, "uHalf");
		upHalf = GL20.glGetUniformLocation(up, "uHalf");
		return true;
	}
}
