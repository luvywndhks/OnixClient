package onix.core.modules;

import onix.core.module.HudModule;
import onix.core.render.Renderer;

/**
 * A HUD box holding one line of text. The string is rebuilt only when the
 * number behind it changes, so a steady counter allocates nothing per frame.
 */
public abstract class TextHudModule extends HudModule {
	private static final float SIZE = 20f, PAD_X = 12f, PAD_Y = 7f;

	private String cached = "";
	private long cachedKey = Long.MIN_VALUE;
	private float cachedWidth = -1f;
	private int cachedFont = -1;

	protected TextHudModule(String name, String description, float posX, float posY) {
		super(name, description, posX, posY);
	}

	/** A number that changes exactly when the text would change. */
	protected abstract long valueKey();

	protected abstract String format();

	private String text() {
		long key = valueKey();
		int font = onix.core.Onix.get().global.hudFont.index;
		if (key != cachedKey || font != cachedFont) {
			cachedFont = font;
			cachedKey = key;
			cached = format();
			cachedWidth = -1f;
		}
		return cached;
	}

	@Override
	protected float contentWidth(Renderer r) {
		String text = text();
		if (cachedWidth < 0f) cachedWidth = r.textWidth(text, SIZE, Renderer.REGULAR);
		return cachedWidth + PAD_X * 2f;
	}

	@Override
	protected float contentHeight(Renderer r) {
		return r.lineHeight(SIZE) + PAD_Y * 2f;
	}

	@Override
	protected void draw(Renderer r, float x, float y, float s) {
		r.text(text(), x + PAD_X * s, y + PAD_Y * s, SIZE * s, textColor.argb, Renderer.REGULAR);
	}
}
