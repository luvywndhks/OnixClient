package onix.mc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.Minecraft;

import onix.core.Onix;
import onix.gl.GlRenderer;

/**
 * The picture behind the main menu and the other screens shown outside a world.
 *
 * It is the wallpaper that ships inside the client. A player can replace it
 * by putting an image at {@code onix/background.png} (or .jpg) in the game
 * folder. Either way it is scaled to cover the window, the caller frosts it
 * afterwards, and no resource pack can touch it.
 */
public final class Backdrop {
	private static final String BUNDLED = "/assets/onix/textures/background.jpg";

	private static boolean looked;
	private static int texture, imageW, imageH;

	private Backdrop() {
	}

	public static void draw(GlRenderer r, float w, float h) {
		if (!looked) load();
		if (texture != 0) {
			image(w, h);
		} else {
			// No picture could be read: a plain dark blue rather than nothing.
			r.begin(w, h);
			r.panel(0f, 0f, w, h, 0f, 0xFF0A1220, 0f, 0, false);
			r.end();
		}
	}

	private static void image(float w, float h) {
		// Cover the window: fill it completely and crop whatever sticks out.
		float scale = Math.max(w / imageW, h / imageH);
		float cropU = (1f - w / (imageW * scale)) / 2f, cropV = (1f - h / (imageH * scale)) / 2f;

		int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL20.glUseProgram(0);
		GL11.glViewport(0, 0, (int) w, (int) h);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_FOG);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDepthMask(false);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glColor4f(1f, 1f, 1f, 1f);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glTexCoord2f(cropU, 1f - cropV);
		GL11.glVertex2f(-1f, -1f);
		GL11.glTexCoord2f(1f - cropU, 1f - cropV);
		GL11.glVertex2f(1f, -1f);
		GL11.glTexCoord2f(1f - cropU, cropV);
		GL11.glVertex2f(1f, 1f);
		GL11.glTexCoord2f(cropU, cropV);
		GL11.glVertex2f(-1f, 1f);
		GL11.glEnd();
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();
		GL11.glPopAttrib();
		GL20.glUseProgram(program);
	}

	/** The player's own picture if there is one, otherwise the wallpaper inside the client. */
	private static void load() {
		looked = true;
		File dir = new File(Minecraft.getInstance().gameDir, Onix.ID);
		for (String name : new String[] { "background.png", "background.jpg", "background.jpeg" }) {
			File file = new File(dir, name);
			if (!file.isFile()) continue;
			try {
				InputStream in = new FileInputStream(file);
				try {
					if (upload(ImageIO.read(in))) return;
				} finally {
					in.close();
				}
			} catch (Exception e) {
				System.err.println("[Onix] Could not load " + file + ": " + e);
			}
		}
		try {
			InputStream in = Backdrop.class.getResourceAsStream(BUNDLED);
			if (in == null) return;
			try {
				upload(ImageIO.read(in));
			} finally {
				in.close();
			}
		} catch (Exception e) {
			System.err.println("[Onix] Could not load the wallpaper: " + e);
		}
	}

	private static boolean upload(BufferedImage image) {
		if (image == null) return false;
		int w = image.getWidth(), h = image.getHeight();
		ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 3);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int rgb = image.getRGB(x, y);
				pixels.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb);
			}
		}
		pixels.flip();
		int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		int id = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, pixels);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
		texture = id;
		imageW = w;
		imageH = h;
		return true;
	}
}
