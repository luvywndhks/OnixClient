package onix.modern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import onix.core.render.Renderer;

/**
 * The core's renderer on modern Minecraft, drawn through the game's own GUI
 * drawing calls so it works on every graphics backend the game has.
 *
 * This first version keeps to what those calls offer: flat fills and the
 * game font. Rounded corners are built from one-pixel strips. Blur, glow,
 * the client font, item icons and the logo are not drawn yet.
 *
 * Coordinates arrive in real pixels; the game draws in GUI units, so the
 * whole pass runs under a pose scaled by one over the GUI scale.
 */
public final class ModernRenderer implements Renderer {
	/** Sizes are given for the client font; the game font is 9 units a line. */
	private static final float FONT_UNITS = 9f;

	private GuiGraphicsExtractor g;
	private Font font;
	private final float[] alphas = new float[32];
	private int alphaDepth;
	private float alpha = 1f;

	public void begin(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		g = graphics;
		font = mc.font;
		alphaDepth = 0;
		alpha = 1f;
		g.pose().pushMatrix();
		float inverse = 1f / mc.getWindow().getGuiScale();
		g.pose().scale(inverse, inverse);
	}

	public void end() {
		g.pose().popMatrix();
		g = null;
	}

	private int faded(int argb) {
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	@Override
	public void panel(float x, float y, float w, float h, float radius, int fill, float borderWidth, int borderColor, boolean blur) {
		if (w <= 0f || h <= 0f) return;
		int x0 = Math.round(x), y0 = Math.round(y), x1 = Math.round(x + w), y1 = Math.round(y + h);
		int r = Math.min(Math.round(radius), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
		int color = faded(fill);
		if ((color >>> 24) != 0) rounded(x0, y0, x1, y1, r, color);
		int edge = faded(borderColor);
		int bw = Math.round(borderWidth);
		if (bw > 0 && (edge >>> 24) != 0) {
			// The straight parts of the outline; the corners are left to the fill underneath.
			g.fill(x0 + r, y0, x1 - r, y0 + bw, edge);
			g.fill(x0 + r, y1 - bw, x1 - r, y1, edge);
			g.fill(x0, y0 + r, x0 + bw, y1 - r, edge);
			g.fill(x1 - bw, y0 + r, x1, y1 - r, edge);
		}
	}

	/** A filled box whose corners are cut to a quarter circle, one row at a time. */
	private void rounded(int x0, int y0, int x1, int y1, int r, int color) {
		if (r <= 1) {
			g.fill(x0, y0, x1, y1, color);
			return;
		}
		g.fill(x0, y0 + r, x1, y1 - r, color);
		for (int i = 0; i < r; i++) {
			float dy = r - i - 0.5f;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			g.fill(x0 + inset, y0 + i, x1 - inset, y0 + i + 1, color);
			g.fill(x0 + inset, y1 - i - 1, x1 - inset, y1 - i, color);
		}
	}

	@Override
	public void glow(float x, float y, float w, float h, float radius, float spread, int color) {
		// Not drawn in this version.
	}

	@Override
	public void item(int slot, float x, float y, float size) {
		// Not drawn in this version.
	}

	@Override
	public void logo(float x, float y, float size) {
		// Not drawn in this version.
	}

	@Override
	public void topLayer(boolean on) {
		// Everything is drawn in call order here, so there is no separate layer.
	}

	@Override
	public void font(int font) {
		// Only the game font exists in this version.
	}

	@Override
	public void text(String text, float x, float y, float size, int color, int weight) {
		int argb = faded(color);
		// The game treats a nearly clear color as fully opaque, so very faint text is skipped.
		if (((argb >>> 24) & 0xFF) < 8 || text.isEmpty()) return;
		float scale = size / FONT_UNITS;
		g.pose().pushMatrix();
		g.pose().translate(Math.round(x), Math.round(y));
		g.pose().scale(scale, scale);
		g.text(font, text, 0, 0, argb, false);
		g.pose().popMatrix();
	}

	@Override
	public float textWidth(String text, float size, int weight) {
		return Minecraft.getInstance().font.width(text) * size / FONT_UNITS;
	}

	@Override
	public float lineHeight(float size) {
		return size;
	}

	@Override
	public void pushClip(float x, float y, float w, float h) {
		g.enableScissor(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h));
	}

	@Override
	public void popClip() {
		g.disableScissor();
	}

	@Override
	public void pushScale(float factor, float originX, float originY) {
		g.pose().pushMatrix();
		g.pose().translate(originX, originY);
		g.pose().scale(factor, factor);
		g.pose().translate(-originX, -originY);
	}

	@Override
	public void popScale() {
		g.pose().popMatrix();
	}

	@Override
	public void pushAlpha(float a) {
		if (alphaDepth >= alphas.length) throw new IllegalStateException("alpha stack overflow");
		alphas[alphaDepth++] = alpha;
		alpha *= Math.max(0f, Math.min(1f, a));
	}

	@Override
	public void popAlpha() {
		alpha = alphas[--alphaDepth];
	}
}
