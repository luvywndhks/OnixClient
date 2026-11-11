package onix.gl;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

/**
 * One picture from the client's own files, drawn as a quad in pixel
 * coordinates. Used for the logo. It is read from the client jar, never
 * through the game's resource packs, and all GL state it touches is put back.
 */
public final class ImageQuad {
	private final String resource;
	private int texture;
	private boolean failed;

	/** @param resource class path location, such as "/assets/onix/textures/logo.png" */
	public ImageQuad(String resource) {
		this.resource = resource;
	}

	public void draw(float x, float y, float w, float h, float alpha, float screenW, float screenH) {
		if (failed || alpha <= 0.003f) return;
		if (texture == 0 && !load()) {
			failed = true;
			return;
		}
		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glOrtho(0, screenW, screenH, 0, -1, 1);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL20.glUseProgram(0);
		GL11.glViewport(0, 0, (int) screenW, (int) screenH);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glColor4f(1f, 1f, 1f, Math.min(1f, alpha));
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(0f, 0f);
		GL11.glVertex2f(x, y);
		GL11.glTexCoord2f(0f, 1f);
		GL11.glVertex2f(x, y + h);
		GL11.glTexCoord2f(1f, 1f);
		GL11.glVertex2f(x + w, y + h);
		GL11.glTexCoord2f(1f, 0f);
		GL11.glVertex2f(x + w, y);
		GL11.glEnd();
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();
		GL11.glPopAttrib();
		GL20.glUseProgram(program);
	}

	private boolean load() {
		InputStream in = ImageQuad.class.getResourceAsStream(resource);
		if (in == null) return false;
		try {
			BufferedImage image = ImageIO.read(in);
			if (image == null) return false;
			int w = image.getWidth(), h = image.getHeight();
			ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int argb = image.getRGB(x, y);
					pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
				}
			}
			pixels.flip();
			int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			texture = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
			return true;
		} catch (Exception e) {
			System.err.println("[Onix] Could not load " + resource + ": " + e);
			return false;
		} finally {
			try {
				in.close();
			} catch (Exception ignored) {
			}
		}
	}
}
