package onix.gl;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import onix.core.render.Renderer;

/**
 * Glyphs rasterised on demand into one alpha texture.
 *
 * Each pixel size gets its own crisp glyphs. Drawing text is an array lookup
 * per character; the font is only touched the first time a glyph is needed.
 * Characters the UI font lacks (Hangul, for example) fall back to the system
 * sans-serif.
 */
final class FontAtlas {
	private static final int SIZE = 2048, PAD = 2;
	private static final String[] FILES = { "Poppins-Regular.ttf", "Poppins-Medium.ttf", "Poppins-SemiBold.ttf" };

	static final class Glyph {
		float u0, v0, u1, v1;
		int w, h, offX, offY;
		float advance;
	}

	static final class Face {
		int px, weight, lineHeight, ascent;
		Font font, fallback;
		FontMetrics metrics, fallbackMetrics;
		final Glyph[] ascii = new Glyph[256];
		Map<Character, Glyph> other;
	}

	private final List<Face> faces = new ArrayList<Face>();
	private final Font[] base = new Font[3];
	private boolean loaded;
	private int texture;
	private int penX = 1, penY = 1, rowHeight;
	private final BufferedImage scratch = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
	private final Graphics2D graphics = scratch.createGraphics();
	private final ByteBuffer upload = BufferUtils.createByteBuffer(256 * 256);

	int texture() {
		if (texture == 0) create();
		return texture;
	}

	Face face(int px, int weight) {
		// A handful of faces at most, so a scan beats a map and allocates nothing.
		for (int i = 0, n = faces.size(); i < n; i++) {
			Face f = faces.get(i);
			if (f.px == px && f.weight == weight) return f;
		}
		if (!loaded) load();
		Face f = new Face();
		f.px = px;
		f.weight = weight;
		f.font = base[weight].deriveFont((float) px);
		f.fallback = new Font(Font.SANS_SERIF, weight == Renderer.REGULAR ? Font.PLAIN : Font.BOLD, px);
		f.metrics = graphics.getFontMetrics(f.font);
		f.fallbackMetrics = graphics.getFontMetrics(f.fallback);
		f.ascent = f.metrics.getAscent();
		f.lineHeight = f.metrics.getAscent() + f.metrics.getDescent();
		faces.add(f);
		return f;
	}

	Glyph glyph(Face face, char c) {
		if (c < 256) {
			Glyph g = face.ascii[c];
			if (g == null) face.ascii[c] = g = rasterise(face, c);
			return g;
		}
		if (face.other == null) face.other = new HashMap<Character, Glyph>();
		Glyph g = face.other.get(c);
		if (g == null) face.other.put(c, g = rasterise(face, c));
		return g;
	}

	private void load() {
		loaded = true;
		for (int i = 0; i < 3; i++) {
			InputStream in = FontAtlas.class.getResourceAsStream("/assets/onix/fonts/" + FILES[i]);
			if (in != null) {
				try {
					base[i] = Font.createFont(Font.TRUETYPE_FONT, in);
				} catch (Exception e) {
					System.err.println("[Onix] Could not load " + FILES[i] + ": " + e);
				} finally {
					try {
						in.close();
					} catch (Exception ignored) {
					}
				}
			}
			if (base[i] == null) base[i] = new Font(Font.SANS_SERIF, i == 0 ? Font.PLAIN : Font.BOLD, 1);
		}
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private Glyph rasterise(Face face, char c) {
		boolean primary = face.font.canDisplay(c);
		Font font = primary ? face.font : face.fallback;
		FontMetrics metrics = primary ? face.metrics : face.fallbackMetrics;

		Glyph g = new Glyph();
		g.advance = (float) font.getStringBounds(String.valueOf(c), graphics.getFontRenderContext()).getWidth();
		if (c == ' ' || Character.isISOControl(c)) return g;

		int w = Math.min(256, (int) Math.ceil(g.advance) + PAD * 2 + 2);
		int h = Math.min(256, metrics.getAscent() + metrics.getDescent() + PAD * 2);
		graphics.setComposite(java.awt.AlphaComposite.Clear);
		graphics.fillRect(0, 0, w, h);
		graphics.setComposite(java.awt.AlphaComposite.SrcOver);
		graphics.setColor(java.awt.Color.WHITE);
		graphics.setFont(font);
		// Line the fallback font's baseline up with the main font's.
		graphics.drawString(String.valueOf(c), PAD, PAD + face.ascent);

		if (penX + w + 1 > SIZE) {
			penX = 1;
			penY += rowHeight + 1;
			rowHeight = 0;
		}
		if (penY + h + 1 > SIZE) {
			// Full: start over. Every cached glyph is stale, so the faces go too.
			System.err.println("[Onix] Font atlas full, resetting");
			faces.clear();
			penX = 1;
			penY = 1;
			rowHeight = 0;
			clear();
			return rasterise(face(face.px, face.weight), c);
		}

		upload.clear();
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) upload.put((byte) (scratch.getRGB(x, y) >>> 24));
		}
		upload.flip();

		int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture());
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, penX, penY, w, h, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, upload);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);

		g.w = w;
		g.h = h;
		g.offX = -PAD;
		g.offY = -PAD;
		g.u0 = penX / (float) SIZE;
		g.v0 = penY / (float) SIZE;
		g.u1 = (penX + w) / (float) SIZE;
		g.v1 = (penY + h) / (float) SIZE;
		penX += w + 1;
		rowHeight = Math.max(rowHeight, h);
		return g;
	}

	private void create() {
		int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		texture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
		clear();
	}

	private void clear() {
		int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture());
		ByteBuffer zero = BufferUtils.createByteBuffer(SIZE * SIZE);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA, SIZE, SIZE, 0, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, zero);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
	}
}
