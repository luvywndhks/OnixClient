package onix.gl;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

import onix.core.render.Renderer;

/**
 * Draws the whole client UI in a single draw call per pass.
 *
 * Boxes, glows and glyphs all go through one shader and one vertex buffer.
 * Rounded corners, outlines and soft edges are computed in the fragment shader
 * from a signed distance, so a box is always one quad whatever its radius.
 * Clipping is done on the CPU by trimming quads, which keeps the batch whole
 * instead of breaking it with scissor changes.
 *
 * It uses plain OpenGL 2.1 and no Minecraft classes. Before returning it puts
 * back every piece of GL state it touched, so the game's own state cache
 * stays correct.
 */
public final class GlRenderer implements Renderer {
	private static final int FLOATS = 19, STRIDE = FLOATS * 4;
	private static final int MAX_QUADS = 8192;
	private static final float MODE_BOX = 0f, MODE_GLYPH = 1f, MODE_GLASS = 2f, MODE_GLOW = 3f;

	private final float[] data = new float[MAX_QUADS * 4 * FLOATS];
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(MAX_QUADS * 4 * FLOATS);
	private int used;

	private final float[] clips = new float[4 * 32];
	private int clipDepth;
	private float clipX0, clipY0, clipX1, clipY1;
	private final float[] alphas = new float[32];
	private int alphaDepth;
	private float alpha = 1f;

	/** Draws a player item icon; supplied by the platform, since that needs game classes. */
	public interface ItemDrawer {
		void draw(int slot, float x, float y, float size, float alpha);
	}

	/** Draws text in the font of the game itself; supplied by the platform. */
	public interface TextDrawer {
		int width(String text);

		void draw(String text, float x, float y, float scale, int argb);
	}

	/** Sizes are given for the client font; the game font looks the same size at this many units per em. */
	private static final float GAME_FONT_EM = 9.2f;
	private static final int GAME_TEXTS = 256;
	private TextDrawer textDrawer;
	private int font;
	private final String[] gameText = new String[GAME_TEXTS];
	private final float[] gameTextAt = new float[GAME_TEXTS * 3];
	private final int[] gameTextColor = new int[GAME_TEXTS];
	private int gameTextCount;

	private ItemDrawer itemDrawer;
	private final float[] items = new float[5 * 32];
	private int itemCount;
	private final ImageQuad logoImage = new ImageQuad("/assets/onix/textures/logo.png");
	private final float[] logos = new float[4 * 32];
	private int logoCount;
	/** Quads drawn above item icons, flushed as a second batch. */
	private final float[] top = new float[1024 * 4 * FLOATS];
	private int topUsed;
	private final float[] scales = new float[3 * 32];
	private int scaleDepth;
	private float scale = 1f, shiftX, shiftY;
	private boolean topLayer;

	private final FontAtlas atlas = new FontAtlas();
	private final Blur blur = new Blur();
	private boolean needsBlur;
	private boolean blurAllowed = true;
	private boolean blurCapture = true;

	private int program, uScreen;
	private boolean failed;
	private float screenW, screenH;

	public void begin(float width, float height) {
		screenW = width;
		screenH = height;
		used = 0;
		clipDepth = 0;
		alphaDepth = 0;
		alpha = 1f;
		clipX0 = 0f;
		clipY0 = 0f;
		clipX1 = width;
		clipY1 = height;
		needsBlur = false;
		topUsed = 0;
		font = FONT_CLIENT;
		gameTextCount = 0;
		topLayer = false;
		scaleDepth = 0;
		scale = 1f;
		shiftX = 0f;
		shiftY = 0f;
		itemCount = 0;
		logoCount = 0;
	}

	/**
	 * With capture off, glass drawn in the coming passes reuses the frost of an earlier
	 * pass instead of blurring the screen again. Used when many small glass widgets are
	 * drawn one pass each over the same background.
	 */
	public void setBlurCapture(boolean capture) {
		blurCapture = capture;
	}

	/** Turns the frosted backdrop off, for players who want every last frame. */
	public void setBlurAllowed(boolean allowed) {
		blurAllowed = allowed;
	}

	public void setTextDrawer(TextDrawer drawer) {
		textDrawer = drawer;
	}

	@Override
	public void font(int font) {
		this.font = font;
	}

	public void setItemDrawer(ItemDrawer drawer) {
		itemDrawer = drawer;
	}

	// ------------------------------------------------------------- drawing

	@Override
	public void topLayer(boolean on) {
		topLayer = on;
	}

	@Override
	public void logo(float x, float y, float size) {
		if (logoCount >= 32 || alpha <= 0.02f) return;
		x = x * scale + shiftX;
		y = y * scale + shiftY;
		size *= scale;
		// A picture cannot be trimmed like a quad, so one that crosses the clip edge is skipped.
		if (x < clipX0 || y < clipY0 || x + size > clipX1 || y + size > clipY1) return;
		int i = logoCount++ * 4;
		logos[i] = x;
		logos[i + 1] = y;
		logos[i + 2] = size;
		logos[i + 3] = alpha;
	}

	@Override
	public void item(int slot, float x, float y, float size) {
		if (itemCount >= 32 || alpha <= 0.02f) return;
		x = x * scale + shiftX;
		y = y * scale + shiftY;
		size *= scale;
		// Icons cannot be clipped like quads, so one that crosses the clip edge is skipped.
		if (x < clipX0 || y < clipY0 || x + size > clipX1 || y + size > clipY1) return;
		int i = itemCount++ * 5;
		items[i] = slot;
		items[i + 1] = x;
		items[i + 2] = y;
		items[i + 3] = size;
		items[i + 4] = alpha;
	}

	@Override
	public void panel(float x, float y, float w, float h, float radius, int fill, float borderWidth, int borderColor, boolean glass) {
		if (w <= 0f || h <= 0f) return;
		float hx = w / 2f, hy = h / 2f;
		float r = Math.min(radius, Math.min(hx, hy));
		boolean frosted = glass && blurAllowed && blur.isSupported();
		if (frosted) needsBlur = true;
		// One extra pixel on each side leaves room for the anti-aliased edge.
		quad(x - 1f, y - 1f, x + w + 1f, y + h + 1f, x + hx, y + hy, hx, hy, r, borderWidth, fill, borderColor,
				frosted ? MODE_GLASS : MODE_BOX, 0f, 0f, 0f, 0f);
	}

	@Override
	public void glow(float x, float y, float w, float h, float radius, float spread, int color) {
		if (w <= 0f || h <= 0f || spread <= 0f) return;
		float hx = w / 2f, hy = h / 2f;
		quad(x - spread, y - spread, x + w + spread, y + h + spread, x + hx, y + hy, hx, hy,
				Math.min(radius, Math.min(hx, hy)), spread, color, 0, MODE_GLOW, 0f, 0f, 0f, 0f);
	}

	@Override
	public void text(String text, float x, float y, float size, int color, int weight) {
		if (font == FONT_MINECRAFT && textDrawer != null) {
			if (gameTextCount >= GAME_TEXTS || alpha <= 0.02f) return;
			int i = gameTextCount++;
			gameText[i] = text;
			gameTextAt[i * 3] = Math.round(x * scale + shiftX);
			gameTextAt[i * 3 + 1] = Math.round(y * scale + shiftY);
			gameTextAt[i * 3 + 2] = size / GAME_FONT_EM * scale;
			int a = Math.round(((color >>> 24) & 0xFF) * alpha);
			gameTextColor[i] = (a << 24) | (color & 0xFFFFFF);
			return;
		}
		FontAtlas.Face face = atlas.face(Math.max(6, Math.round(size)), weight);
		float pen = Math.round(x);
		float top = Math.round(y);
		for (int i = 0, n = text.length(); i < n; i++) {
			FontAtlas.Glyph g = atlas.glyph(face, text.charAt(i));
			if (g.w > 0) {
				float gx = pen + g.offX, gy = top + g.offY;
				quad(gx, gy, gx + g.w, gy + g.h, 0f, 0f, 0f, 0f, 0f, 0f, color, 0, MODE_GLYPH, g.u0, g.v0, g.u1, g.v1);
			}
			pen += g.advance;
		}
	}

	@Override
	public float textWidth(String text, float size, int weight) {
		if (font == FONT_MINECRAFT && textDrawer != null) return textDrawer.width(text) * size / GAME_FONT_EM;
		FontAtlas.Face face = atlas.face(Math.max(6, Math.round(size)), weight);
		float width = 0f;
		for (int i = 0, n = text.length(); i < n; i++) width += atlas.glyph(face, text.charAt(i)).advance;
		return width;
	}

	@Override
	public float lineHeight(float size) {
		// The game font is 8 pixels tall with the baseline one pixel up.
		if (font == FONT_MINECRAFT && textDrawer != null) return 8f * size / GAME_FONT_EM;
		return atlas.face(Math.max(6, Math.round(size)), REGULAR).lineHeight;
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		if (clipDepth >= 32) throw new IllegalStateException("clip stack overflow");
		int i = clipDepth++ * 4;
		clips[i] = clipX0;
		clips[i + 1] = clipY0;
		clips[i + 2] = clipX1;
		clips[i + 3] = clipY1;
		// Clip rectangles are kept in screen space, so an active scale applies to them too.
		x = x * scale + shiftX;
		y = y * scale + shiftY;
		w *= scale;
		h *= scale;
		clipX0 = Math.max(clipX0, x);
		clipY0 = Math.max(clipY0, y);
		clipX1 = Math.min(clipX1, x + w);
		clipY1 = Math.min(clipY1, y + h);
	}

	@Override
	public void popClip() {
		int i = --clipDepth * 4;
		clipX0 = clips[i];
		clipY0 = clips[i + 1];
		clipX1 = clips[i + 2];
		clipY1 = clips[i + 3];
	}

	@Override
	public void pushScale(float factor, float originX, float originY) {
		if (scaleDepth >= 32) throw new IllegalStateException("scale stack overflow");
		int i = scaleDepth++ * 3;
		scales[i] = scale;
		scales[i + 1] = shiftX;
		scales[i + 2] = shiftY;
		// Compose with whatever scale is already active: scale about the origin, then the outer transform.
		shiftX += originX * (1f - factor) * scale;
		shiftY += originY * (1f - factor) * scale;
		scale *= factor;
	}

	@Override
	public void popScale() {
		int i = --scaleDepth * 3;
		scale = scales[i];
		shiftX = scales[i + 1];
		shiftY = scales[i + 2];
	}

	@Override
	public void pushAlpha(float a) {
		if (alphaDepth >= 32) throw new IllegalStateException("alpha stack overflow");
		alphas[alphaDepth++] = alpha;
		alpha *= Math.max(0f, Math.min(1f, a));
	}

	@Override
	public void popAlpha() {
		alpha = alphas[--alphaDepth];
	}

	private void quad(float x0, float y0, float x1, float y1, float cx, float cy, float hx, float hy, float radius,
			float border, int fill, int borderColor, float mode, float u0, float v0, float u1, float v1) {
		if (scale != 1f || shiftX != 0f || shiftY != 0f) {
			// A scaled box keeps its shape: corner radius, border and glow shrink with it.
			x0 = x0 * scale + shiftX;
			x1 = x1 * scale + shiftX;
			y0 = y0 * scale + shiftY;
			y1 = y1 * scale + shiftY;
			cx = cx * scale + shiftX;
			cy = cy * scale + shiftY;
			hx *= scale;
			hy *= scale;
			radius *= scale;
			border *= scale;
		}
		float qx0 = Math.max(x0, clipX0), qy0 = Math.max(y0, clipY0);
		float qx1 = Math.min(x1, clipX1), qy1 = Math.min(y1, clipY1);
		if (qx0 >= qx1 || qy0 >= qy1) return;
		if (mode == MODE_GLYPH && (qx0 != x0 || qy0 != y0 || qx1 != x1 || qy1 != y1)) {
			// A trimmed glyph must sample the matching part of its texture cell.
			float du = (u1 - u0) / (x1 - x0), dv = (v1 - v0) / (y1 - y0);
			float nu0 = u0 + (qx0 - x0) * du, nu1 = u0 + (qx1 - x0) * du;
			float nv0 = v0 + (qy0 - y0) * dv, nv1 = v0 + (qy1 - y0) * dv;
			u0 = nu0;
			u1 = nu1;
			v0 = nv0;
			v1 = nv1;
		}
		float[] d = topLayer ? top : data;
		int base = topLayer ? topUsed : used;
		if (base + 4 * FLOATS > d.length) return;

		float fa = ((fill >>> 24) & 0xFF) / 255f, fr = ((fill >> 16) & 0xFF) / 255f;
		float fg = ((fill >> 8) & 0xFF) / 255f, fb = (fill & 0xFF) / 255f;
		if (mode == MODE_GLASS) {
			// Glass keeps its tint strength while fading, so the fade travels separately.
			u0 = alpha;
			u1 = alpha;
		} else {
			fa *= alpha;
		}
		float ba = ((borderColor >>> 24) & 0xFF) / 255f * alpha, br = ((borderColor >> 16) & 0xFF) / 255f;
		float bg = ((borderColor >> 8) & 0xFF) / 255f, bb = (borderColor & 0xFF) / 255f;

		for (int corner = 0; corner < 4; corner++) {
			boolean right = corner == 1 || corner == 2, bottom = corner >= 2;
			float px = right ? qx1 : qx0, py = bottom ? qy1 : qy0;
			int i = base;
			d[i] = px;
			d[i + 1] = py;
			d[i + 2] = px - cx;
			d[i + 3] = py - cy;
			d[i + 4] = hx;
			d[i + 5] = hy;
			d[i + 6] = radius;
			d[i + 7] = border;
			d[i + 8] = fr;
			d[i + 9] = fg;
			d[i + 10] = fb;
			d[i + 11] = fa;
			d[i + 12] = br;
			d[i + 13] = bg;
			d[i + 14] = bb;
			d[i + 15] = ba;
			d[i + 16] = mode;
			d[i + 17] = right ? u1 : u0;
			d[i + 18] = bottom ? v1 : v0;
			base += FLOATS;
		}
		if (topLayer) topUsed = base;
		else used = base;
	}

	// --------------------------------------------------------------- flush

	/** Sends everything queued since {@link #begin} to the GPU. */
	public void end() {
		flush();
		// Icons go through the item renderer of the game, after the batch, so they sit on top.
		if (itemDrawer != null) {
			for (int i = 0; i < itemCount; i++) {
				int o = i * 5;
				itemDrawer.draw((int) items[o], items[o + 1], items[o + 2], items[o + 3], items[o + 4]);
			}
		}
		itemCount = 0;
		for (int i = 0; i < logoCount; i++) {
			int o = i * 4;
			logoImage.draw(logos[o], logos[o + 1], logos[o + 2], logos[o + 2], logos[o + 3], screenW, screenH);
		}
		logoCount = 0;
		// Text in the game font is drawn by the game, after the batch and the icons.
		for (int i = 0; i < gameTextCount; i++) {
			textDrawer.draw(gameText[i], gameTextAt[i * 3], gameTextAt[i * 3 + 1], gameTextAt[i * 3 + 2], gameTextColor[i]);
			gameText[i] = null;
		}
		gameTextCount = 0;
		if (topUsed > 0) {
			System.arraycopy(top, 0, data, 0, topUsed);
			used = topUsed;
			topUsed = 0;
			needsBlur = false;
			flush();
		}
	}

	private void flush() {
		if (used == 0 || failed) {
			used = 0;
			return;
		}
		if (program == 0 && !compile()) {
			used = 0;
			return;
		}
		if (needsBlur && blurCapture) blur.capture((int) screenW, (int) screenH);

		int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushClientAttrib(GL11.GL_ALL_CLIENT_ATTRIB_BITS);
		try {
			GL11.glViewport(0, 0, (int) screenW, (int) screenH);
			GL11.glDisable(GL11.GL_DEPTH_TEST);
			GL11.glDisable(GL11.GL_CULL_FACE);
			GL11.glDisable(GL11.GL_ALPHA_TEST);
			GL11.glDisable(GL11.GL_SCISSOR_TEST);
			GL11.glDisable(GL11.GL_STENCIL_TEST);
			GL11.glDepthMask(false);
			GL11.glColorMask(true, true, true, true);
			GL11.glEnable(GL11.GL_BLEND);
			GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

			GL20.glUseProgram(program);
			GL20.glUniform2f(uScreen, screenW, screenH);
			GL13.glActiveTexture(GL13.GL_TEXTURE1);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, blur.texture());
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.texture());

			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
			buffer.clear();
			buffer.put(data, 0, used);
			pointer(0, 2, 0);
			pointer(1, 2, 2);
			pointer(2, 2, 4);
			pointer(3, 2, 6);
			pointer(4, 4, 8);
			pointer(5, 4, 12);
			pointer(6, 1, 16);
			pointer(7, 2, 17);
			GL11.glDrawArrays(GL11.GL_QUADS, 0, used / FLOATS);
			for (int i = 0; i < 8; i++) GL20.glDisableVertexAttribArray(i);
		} finally {
			GL20.glUseProgram(previousProgram);
			GL11.glPopClientAttrib();
			GL11.glPopAttrib();
			used = 0;
		}
	}

	private void pointer(int index, int size, int offset) {
		buffer.limit(used);
		buffer.position(offset);
		GL20.glEnableVertexAttribArray(index);
		GL20.glVertexAttribPointer(index, size, false, STRIDE, buffer);
	}

	private boolean compile() {
		program = Shaders.link(VERTEX, FRAGMENT, "aPos", "aLocal", "aHalf", "aShape", "aFill", "aBorder", "aMode", "aUv");
		if (program == 0) {
			failed = true;
			return false;
		}
		int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		GL20.glUseProgram(program);
		uScreen = GL20.glGetUniformLocation(program, "uScreen");
		GL20.glUniform1i(GL20.glGetUniformLocation(program, "uFont"), 0);
		GL20.glUniform1i(GL20.glGetUniformLocation(program, "uBlur"), 1);
		GL20.glUseProgram(previous);
		return true;
	}

	private static final String VERTEX = "#version 120\n"
			+ "attribute vec2 aPos; attribute vec2 aLocal; attribute vec2 aHalf; attribute vec2 aShape;\n"
			+ "attribute vec4 aFill; attribute vec4 aBorder; attribute float aMode; attribute vec2 aUv;\n"
			+ "uniform vec2 uScreen;\n"
			+ "varying vec2 vLocal; varying vec2 vHalf; varying vec2 vShape; varying vec4 vFill; varying vec4 vBorder;\n"
			+ "varying float vMode; varying vec2 vUv;\n"
			+ "void main() {\n"
			+ "  vLocal = aLocal; vHalf = aHalf; vShape = aShape; vFill = aFill; vBorder = aBorder; vMode = aMode; vUv = aUv;\n"
			+ "  gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);\n"
			+ "}\n";

	private static final String FRAGMENT = "#version 120\n"
			+ "uniform sampler2D uFont; uniform sampler2D uBlur; uniform vec2 uScreen;\n"
			+ "varying vec2 vLocal; varying vec2 vHalf; varying vec2 vShape; varying vec4 vFill; varying vec4 vBorder;\n"
			+ "varying float vMode; varying vec2 vUv;\n"
			+ "void main() {\n"
			+ "  if (vMode > 0.5 && vMode < 1.5) {\n"
			+ "    gl_FragColor = vec4(vFill.rgb, vFill.a * texture2D(uFont, vUv).a);\n"
			+ "    return;\n"
			+ "  }\n"
			// Signed distance to the rounded box: negative inside, positive outside.
			+ "  float radius = vShape.x;\n"
			+ "  vec2 q = abs(vLocal) - vHalf + radius;\n"
			+ "  float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n"
			+ "  if (vMode > 2.5) {\n"
			+ "    float g = clamp(1.0 - d / vShape.y, 0.0, 1.0);\n"
			+ "    gl_FragColor = vec4(vFill.rgb, vFill.a * g * g);\n"
			+ "    return;\n"
			+ "  }\n"
			+ "  float coverage = clamp(0.5 - d, 0.0, 1.0);\n"
			+ "  if (coverage <= 0.0) discard;\n"
			+ "  vec4 fill = vFill;\n"
			+ "  if (vMode > 1.5) {\n"
			// Glass: the frosted game image with the fill laid over it as a tint.
			+ "    vec3 back = texture2D(uBlur, gl_FragCoord.xy / uScreen).rgb;\n"
			// For glass, vFill.a is the tint strength and vUv.x carries the fade.
			+ "    fill = vec4(mix(back, vFill.rgb, vFill.a), vUv.x);\n"
			// A faint light band along the top edge sells the glass.
			+ "    fill.rgb += 0.035 * step(0.5, radius) * clamp(1.0 + (vLocal.y + vHalf.y - 26.0) / -26.0, 0.0, 1.0);\n"
			+ "  }\n"
			+ "  float band = vShape.y > 0.0 ? clamp(d + vShape.y + 0.5, 0.0, 1.0) * vBorder.a : 0.0;\n"
			+ "  vec4 color = vec4(mix(fill.rgb, vBorder.rgb, band), mix(fill.a, 1.0, band));\n"
			+ "  gl_FragColor = vec4(color.rgb, color.a * coverage);\n"
			+ "}\n";
}
