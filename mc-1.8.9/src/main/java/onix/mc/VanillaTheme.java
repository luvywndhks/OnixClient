package onix.mc;

import net.minecraft.client.Minecraft;

import onix.core.render.Renderer;
import onix.core.theme.Theme;
import onix.gl.GlRenderer;

/**
 * Draws the game's own screens in the client style: buttons, sliders, lists,
 * screen backgrounds, the wordmark and the main menu. The vanilla widgets keep
 * doing the work (clicks, option values); only their look and, on the menus,
 * their placement are replaced.
 *
 * Buttons are flat, low and translucent over a blurred background. Nothing
 * here reads a texture or a font from the game, so a resource pack cannot
 * change any of it.
 *
 * Vanilla screens use GUI-scaled coordinates, so everything is converted to
 * real pixels with {@link #factor()} before it reaches the renderer.
 */
public final class VanillaTheme {
	/** The light blue of the client wordmark. */
	private static final int WORDMARK = 0xFF6CC5F5;
	private static final String NAME = "ONIX CLIENT";
	private static final onix.gl.ImageQuad LOGO = new onix.gl.ImageQuad("/assets/onix/textures/logo.png");

	private VanillaTheme() {
	}

	/** Real pixels per GUI unit of the open screen. */
	private static float factor() {
		Minecraft mc = Minecraft.getInstance();
		return mc.screen == null || mc.screen.width <= 0 ? 1f : mc.width / (float) mc.screen.width;
	}

	/** The blurred picture every menu sits on: the world when in one, the client backdrop otherwise. */
	private static void frosted(GlRenderer r, float w, float h) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.world == null) Backdrop.draw(r, w, h);
		r.begin(w, h);
		r.panel(0f, 0f, w, h, 0f, mc.world != null ? 0x33101822 : 0x2E081428, 0f, 0, true);
		r.end();
	}

	/** Replaces the dirt and gradient backgrounds of vanilla screens, and puts the wordmark in the corner. */
	public static void background() {
		Minecraft mc = Minecraft.getInstance();
		GlRenderer r = Bridge.get().renderer;
		float w = mc.width, h = mc.height, f = factor();
		frosted(r, w, h);
		r.begin(w, h);
		// The pause menu shows the wordmark large above its buttons instead.
		if (!(mc.screen instanceof net.minecraft.client.gui.screen.menu.GameMenuScreen)) r.text(NAME, 8f * f, 6f * f, 11f * f, WORDMARK, Renderer.SEMIBOLD);
		r.end();
	}

	/**
	 * @param hover  0 to 1, how far the hover animation has come
	 * @param slider the slider position 0 to 1, or a negative number for a plain button
	 */
	public static void button(int x, int y, int w, int h, String label, boolean active, float hover, float slider) {
		Minecraft mc = Minecraft.getInstance();
		Bridge bridge = Bridge.get();
		GlRenderer r = bridge.renderer;
		Theme t = bridge.onix.theme;
		float f = factor();
		float px = x * f, py = y * f, pw = w * f, ph = h * f;
		float radius = 2f * f;

		r.begin(mc.width, mc.height);
		// A flat, see-through slab with a hairline edge; it lightens under the cursor.
		int fill = active ? Theme.mix(0x6612304E, 0x8C1E5C96, hover) : 0x4D0E1A28;
		r.panel(px, py, pw, ph, radius, fill, Math.max(1f, f * 0.5f), Theme.mix(0x5952B4F0, 0xE66CC5F5, hover), false);
		if (slider >= 0f) {
			// The filled part shows the value, like the sliders of the client menu.
			r.pushClip(px, py, pw * Math.min(1f, slider), ph);
			r.panel(px, py, pw, ph, radius, t.withAlpha(t.accent, 0.85f), 0f, 0, false);
			r.popClip();
		}
		float size = Math.max(9f, ph * 0.43f);
		float tw = r.textWidth(label, size, Renderer.REGULAR);
		// A label too long for its button is shrunk to fit rather than cut off.
		if (tw > pw - 8f * f) {
			size *= (pw - 8f * f) / tw;
			tw = r.textWidth(label, size, Renderer.REGULAR);
		}
		r.text(label, px + (pw - tw) / 2f, py + (ph - r.lineHeight(size)) / 2f, size, active ? t.text : t.textDim, Renderer.REGULAR);
		r.end();
	}

	/** The translucent sheet a vanilla list (worlds, servers, resource packs, key binds) sits on, replacing the dirt. */
	public static void listPanel(int minX, int minY, int maxX, int maxY) {
		Minecraft mc = Minecraft.getInstance();
		GlRenderer r = Bridge.get().renderer;
		float f = factor();
		r.begin(mc.width, mc.height);
		float inset = 6f * f;
		r.panel(minX * f + inset, minY * f, (maxX - minX) * f - inset * 2f, (maxY - minY) * f, 3f * f, 0x800C1826,
				Math.max(1f, f * 0.5f), 0x4052B4F0, false);
		r.end();
	}

	/**
	 * Covers list entries that have scrolled past the top or bottom edge, a job the
	 * game gives to two strips of dirt. The strip is repainted with the screen background.
	 */
	public static void listEdge(int y0, int y1) {
		Minecraft mc = Minecraft.getInstance();
		GlRenderer r = Bridge.get().renderer;
		float f = factor();
		float w = mc.width, h = mc.height;
		r.begin(w, h);
		// Reuse the blur the background pass made; the strip must match it exactly.
		r.setBlurCapture(false);
		r.pushClip(0f, y0 * f, w, (y1 - y0) * f);
		r.panel(0f, 0f, w, h, 0f, mc.world != null ? 0x33101822 : 0x2E081428, 0f, 0, true);
		r.popClip();
		r.end();
		r.setBlurCapture(true);
	}

	/** The wordmark, centred, ending at the given height. Used above the main and pause menu buttons. */
	public static void wordmark(int bottom, float guiSize) {
		Minecraft mc = Minecraft.getInstance();
		GlRenderer r = Bridge.get().renderer;
		float f = factor(), w = mc.width;
		r.begin(w, mc.height);
		float size = guiSize * f;
		r.text(NAME, (w - r.textWidth(NAME, size, Renderer.SEMIBOLD)) / 2f, bottom * f - r.lineHeight(size), size, WORDMARK, Renderer.SEMIBOLD);
		r.end();
		float logo = guiSize * 1.7f * f;
		LOGO.draw((w - logo) / 2f, bottom * f - r.lineHeight(size) - logo - 3f * f, logo, logo, 1f, w, mc.height);
		r.begin(w, mc.height);
		r.end();
	}

	/**
	 * The main menu: the blurred backdrop, the wordmark with a line under it, and
	 * the version in the corner.
	 *
	 * @param nameBottom where the wordmark block should end, in GUI units
	 */
	public static void title(int guiWidth, int guiHeight, int nameBottom) {
		Minecraft mc = Minecraft.getInstance();
		Bridge bridge = Bridge.get();
		GlRenderer r = bridge.renderer;
		Theme t = bridge.onix.theme;
		float f = factor();
		float w = mc.width, h = mc.height;

		frosted(r, w, h);
		r.begin(w, h);
		float sub = 7.5f * f;
		String line = "The legitimate client for Minecraft: Java Edition";
		float subTop = nameBottom * f - r.lineHeight(sub);
		r.text(line, (w - r.textWidth(line, sub, Renderer.REGULAR)) / 2f, subTop, sub, t.textDim, Renderer.REGULAR);
		float size = 26f * f;
		r.text(NAME, (w - r.textWidth(NAME, size, Renderer.SEMIBOLD)) / 2f, subTop - r.lineHeight(size) + 2f * f, size, WORDMARK, Renderer.SEMIBOLD);
		r.end();
		// The logo sits above the wordmark.
		float logo = 42f * f;
		LOGO.draw((w - logo) / 2f, subTop - r.lineHeight(size) + 2f * f - logo - 3f * f, logo, logo, 1f, w, h);
		r.begin(w, h);
		float small = 7f * f;
		r.text("Onix Client 1.8.9", 6f * f, h - r.lineHeight(small) - 5f * f, small, t.textDim, Renderer.REGULAR);
		r.end();
	}
}
