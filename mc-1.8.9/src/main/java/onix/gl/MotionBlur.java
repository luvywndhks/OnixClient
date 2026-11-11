package onix.gl;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Motion blur on whatever framebuffer is bound, in three flavours.
 *
 * TRAIL (V1) is the classic accumulation blur: each finished frame is blended
 * with the previous finished frame. Because the previous frame already holds
 * the one before it, a fast object leaves a long fading tail.
 *
 * SINGLE and AVERAGE (V2) follow Latite Client's MotionBlur. They only ever
 * mix in clean frames, the picture before any blur was applied, so nothing
 * feeds back and the tail has a hard end: one frame for SINGLE, a short
 * window for AVERAGE, where older frames count for much less (age cubed).
 * That is what makes it look clean instead of smeared.
 *
 * Plain OpenGL only, and every piece of state touched is put back.
 */
public final class MotionBlur {
	public static final int TRAIL = 0, SINGLE = 1, AVERAGE = 2;
	private static final int MAX_HISTORY = 8;

	private final int[] textures = new int[MAX_HISTORY + 1];
	private final int[] fbos = new int[MAX_HISTORY + 1];
	private int width, height, lastMode = -1;
	// Ring of clean frames for AVERAGE: 'count' frames ending just before 'next'.
	private int count, next;
	private boolean hasPrevious;
	private Boolean supported;

	public boolean isSupported() {
		if (supported == null) supported = GLContext.getCapabilities().OpenGL30;
		return supported;
	}

	/** Forget every stored frame, for example on a world change. */
	public void reset() {
		count = 0;
		next = 0;
		hasPrevious = false;
	}

	/**
	 * @param mode     TRAIL, SINGLE or AVERAGE
	 * @param strength how much of the old picture is mixed in, 0 to 1
	 * @param frames   AVERAGE only: how many past frames to keep, 2 to 8
	 * @param delta    seconds since the previous frame
	 */
	public void apply(int mode, float strength, int frames, float delta, int w, int h) {
		if (!isSupported() || w < 8 || h < 8 || strength <= 0f) return;
		if (w != width || h != height) resize(w, h);
		if (mode != lastMode) {
			reset();
			lastMode = mode;
		}

		int target = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
		int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		try {
			GL20.glUseProgram(0);
			GL11.glViewport(0, 0, w, h);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDisable(GL11.GL_FOG);
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			GL11.glDisable(GL11.GL_STENCIL_TEST);
			GL11.glDepthMask(false);
			GL11.glColorMask(true, true, true, true);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			if (mode == TRAIL) {
				// The slider is tuned at 60 FPS. Raising it to the frame time keeps the tail
				// the same length in seconds whether the game runs at 60 or 600 FPS.
				float mix = (float) Math.pow(strength, Math.max(0.01f, delta * 60f));
				if (hasPrevious) draw(textures[0], mix);
				// Store the blended result: this is the feedback that makes the tail.
				copy(target, 0, w, h);
				hasPrevious = true;
			} else if (mode == SINGLE) {
				// Keep the clean frame aside first, then lay the previous clean frame over it.
				copy(target, 1, w, h);
				if (hasPrevious) draw(textures[0], strength);
				swap(0, 1);
				hasPrevious = true;
			} else {
				int keep = Math.max(2, Math.min(MAX_HISTORY, frames));
				if (count > keep) count = keep;
				// The clean frame goes into the spare slot; history is drawn oldest first.
				copy(target, MAX_HISTORY, w, h);
				for (int i = 0; i < count; i++) {
					int slot = ((next - count + i) % MAX_HISTORY + MAX_HISTORY) % MAX_HISTORY;
					float age = count > 1 ? i / (float) (count - 1) : 1f;
					draw(textures[slot], strength * age * age * age);
				}
				swap(next, MAX_HISTORY);
				next = (next + 1) % MAX_HISTORY;
				if (count < keep) count++;
			}
		} finally {
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPopMatrix();
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
			GL20.glUseProgram(program);
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
		}
	}

	/** Lays a stored frame over the bound framebuffer at the given opacity. */
	private void draw(int texture, float opacity) {
		if (opacity <= 0.003f) return;
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glColor4f(1f, 1f, 1f, Math.min(1f, opacity));
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0f, 0f);
		GL11.glVertex2f(-1f, -1f);
		GL11.glTexCoord2f(1f, 0f);
		GL11.glVertex2f(1f, -1f);
		GL11.glTexCoord2f(1f, 1f);
		GL11.glVertex2f(1f, 1f);
		GL11.glTexCoord2f(0f, 1f);
		GL11.glVertex2f(-1f, 1f);
		GL11.glEnd();
	}

	/** Copies the picture in {@code from} into one of our textures, then binds {@code from} for drawing again. */
	private void copy(int from, int slot, int w, int h) {
		GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbos[slot]);
		GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
		GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, from);
	}

	private void swap(int a, int b) {
		int t = textures[a];
		textures[a] = textures[b];
		textures[b] = t;
		int f = fbos[a];
		fbos[a] = fbos[b];
		fbos[b] = f;
	}

	private void resize(int w, int h) {
		int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int boundFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		for (int i = 0; i < textures.length; i++) {
			if (textures[i] == 0) {
				textures[i] = GL11.glGenTextures();
				fbos[i] = GL30.glGenFramebuffers();
			}
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbos[i]);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, textures[i], 0);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, boundTexture);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, boundFbo);
		width = w;
		height = h;
		reset();
	}
}
