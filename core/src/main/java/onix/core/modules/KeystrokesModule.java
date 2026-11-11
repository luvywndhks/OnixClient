package onix.core.modules;

import onix.core.Onix;
import onix.core.anim.Anim;
import onix.core.input.ClickTracker;
import onix.core.module.HudModule;
import onix.core.platform.Platform;
import onix.core.render.Renderer;
import onix.core.setting.Setting;

/** WASD, both mouse buttons with CPS, and the space bar. Keys light up while held. */
public final class KeystrokesModule extends HudModule {
	private static final float KEY = 52f, GAP = 5f;

	private final Setting.Bool mouse = add(new Setting.Bool("Mouse Buttons", true));
	private final Setting.Bool cps = add(new Setting.Bool("Show CPS", true));
	private final Setting.Bool space = add(new Setting.Bool("Space Bar", true));
	private final Setting.Color pressed = add(new Setting.Color("Pressed Color", 0xCC146B8C));

	// forward, left, back, right, attack, use, jump
	private final Anim[] press = new Anim[7];
	private final String[] labels = new String[4];
	private final int[] labelCodes = new int[4];
	private final String[] cpsText = { "0 CPS", "0 CPS" };
	private final int[] cpsValue = { 0, 0 };
	private long last = System.nanoTime();

	public KeystrokesModule() {
		super("Keystrokes", "Shows your movement keys and mouse buttons.", 0.955f, 0.09f);
		for (int i = 0; i < press.length; i++) press[i] = new Anim(0f, 22f);
	}

	@Override
	protected boolean drawsBox() {
		return false;
	}

	@Override
	protected float contentWidth(Renderer r) {
		return KEY * 3f + GAP * 2f;
	}

	@Override
	protected float contentHeight(Renderer r) {
		float h = KEY * 2f + GAP;
		if (mouse.on) h += GAP + KEY;
		if (space.on) h += GAP + KEY * 0.45f;
		return h;
	}

	@Override
	protected void draw(Renderer r, float x, float y, float s) {
		Platform p = Onix.get().platform;
		ClickTracker clicks = Onix.get().clicks;
		int[] keys = p.controlKeys();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;

		for (int i = 0; i < 4; i++) {
			// Rebinding a movement key changes its label, so names are cached per code.
			if (labels[i] == null || labelCodes[i] != keys[i]) {
				labelCodes[i] = keys[i];
				labels[i] = p.keyName(keys[i]);
			}
		}

		float k = KEY * s, g = GAP * s;
		key(r, 0, p.isDown(keys[0]), x + k + g, y, k, k, labels[0], null, s, dt);
		float row = y + k + g;
		key(r, 1, p.isDown(keys[1]), x, row, k, k, labels[1], null, s, dt);
		key(r, 2, p.isDown(keys[2]), x + k + g, row, k, k, labels[2], null, s, dt);
		key(r, 3, p.isDown(keys[3]), x + (k + g) * 2f, row, k, k, labels[3], null, s, dt);
		row += k + g;

		if (mouse.on) {
			float half = (k * 3f + g) / 2f;
			for (int b = 0; b < 2; b++) {
				int n = clicks.cps(b);
				if (n != cpsValue[b]) {
					cpsValue[b] = n;
					cpsText[b] = n + " CPS";
				}
			}
			key(r, 4, clicks.isDown(0), x, row, half, k, "LMB", cps.on ? cpsText[0] : null, s, dt);
			key(r, 5, clicks.isDown(1), x + half + g, row, half, k, "RMB", cps.on ? cpsText[1] : null, s, dt);
			row += k + g;
		}
		if (space.on) {
			float h = k * 0.45f, w = k * 3f + g * 2f;
			float a = press[6].update(p.isDown(keys[4]) ? 1f : 0f, dt);
			box(r, x, row, w, h, s);
			if (a > 0.01f) r.panel(x, row, w, h, radius.v * s, Onix.get().theme.withAlpha(pressed.argb, a), 0f, 0, false);
			float bar = w * 0.4f, bh = Math.max(2f, 3f * s);
			r.panel(x + (w - bar) / 2f, row + (h - bh) / 2f, bar, bh, bh / 2f, textColor.argb, 0f, 0, false);
		}
	}

	private void key(Renderer r, int index, boolean down, float x, float y, float w, float h, String label, String sub, float s, float dt) {
		float a = press[index].update(down ? 1f : 0f, dt);
		box(r, x, y, w, h, s);
		if (a > 0.01f) r.panel(x, y, w, h, radius.v * s, Onix.get().theme.withAlpha(pressed.argb, a), 0f, 0, false);
		float size = (sub == null ? 22f : 18f) * s;
		float lh = r.lineHeight(size);
		float subSize = 11f * s, subH = sub == null ? 0f : r.lineHeight(subSize);
		float top = y + (h - lh - subH) / 2f;
		r.text(label, x + (w - r.textWidth(label, size, Renderer.REGULAR)) / 2f, top, size, textColor.argb, Renderer.REGULAR);
		if (sub != null) {
			r.text(sub, x + (w - r.textWidth(sub, subSize, Renderer.REGULAR)) / 2f, top + lh, subSize, textColor.argb, Renderer.REGULAR);
		}
	}
}
