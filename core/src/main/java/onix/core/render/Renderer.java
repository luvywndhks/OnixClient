package onix.core.render;

/**
 * Everything the client draws goes through this interface, so modules and the
 * settings screen are written once and run on every Minecraft version.
 *
 * Coordinates are real framebuffer pixels with the origin at the top left.
 * Colors are ARGB. Implementations batch: nothing reaches the GPU until the
 * platform flushes the frame.
 */
public interface Renderer {
	int REGULAR = 0, MEDIUM = 1, SEMIBOLD = 2;
	int FONT_CLIENT = 0, FONT_MINECRAFT = 1;

	/** Picks the font for text drawn from now on. Sizes and widths follow the font. */
	void font(int font);

	/**
	 * Rounded box. With blur, the game behind the box is frosted first and the
	 * fill is laid over it as a tint, which gives the glass look.
	 */
	void panel(float x, float y, float w, float h, float radius, int fill, float borderWidth, int borderColor, boolean blur);

	/** Soft halo around a rounded box. Draw it before the box itself. */
	void glow(float x, float y, float w, float h, float radius, float spread, int color);

	/**
	 * An item icon from the player: slots 0 to 3 are helmet to boots, 4 is the held
	 * item. Icons are drawn after everything else in the pass.
	 */
	void item(int slot, float x, float y, float size);

	/** The client logo, as a square of the given size. Drawn after everything else in the pass. */
	void logo(float x, float y, float size);

	/** While on, what is drawn goes above item icons instead of under them. */
	void topLayer(boolean on);

	/** y is the top of the line box. */
	void text(String text, float x, float y, float size, int color, int weight);

	float textWidth(String text, float size, int weight);

	/** Height of one line box at this size. */
	float lineHeight(float size);

	/** Clips everything drawn until the matching pop. Clips nest. */
	void pushClip(float x, float y, float w, float h);

	void popClip();

	/**
	 * Scales everything drawn until the matching pop about the given point, which
	 * stays where it is. Scales nest.
	 */
	void pushScale(float factor, float originX, float originY);

	void popScale();

	/** Multiplies the alpha of everything drawn until the matching pop. */
	void pushAlpha(float alpha);

	void popAlpha();
}
