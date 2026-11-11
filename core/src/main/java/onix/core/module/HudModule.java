package onix.core.module;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.event.Listener;
import onix.core.render.Renderer;
import onix.core.setting.Setting;
import onix.core.theme.Theme;

/**
 * A module that draws a movable box on the HUD.
 *
 * Position is stored as a fraction of the screen so a layout survives a
 * resolution change. Sizes are designed at 1080p and scale with the window.
 */
public abstract class HudModule extends Module {
	/** Centre of the box, 0..1 of the screen. */
	public float posX, posY;
	private final float defaultX, defaultY;

	public final Setting.Number scale = add(new Setting.Number("Scale", 1f, 0.5f, 3f, 0.05f).section("Display Settings"));
	public final Setting.Bool background = add(new Setting.Bool("Background", true).section("Display Settings"));
	public final Setting.Bool blur = add(new Setting.Bool("Blurred Background", true).section("Display Settings"));
	public final Setting.Bool border = add(new Setting.Bool("Outline", true).section("Display Settings"));
	public final Setting.Bool glow = add(new Setting.Bool("Glow", false).section("Display Settings"));
	public final Setting.Number radius = add(new Setting.Number("Corner Radius", 6f, 0f, 20f, 1f).section("Display Settings"));
	public final Setting.Color textColor = add(new Setting.Color("Text Color", 0xFFFFFFFF).section("Display Settings"));

	/** Last drawn bounds in pixels, used by the HUD editor for hit testing. */
	public float lastX, lastY, lastW, lastH;

	protected HudModule(String name, String description, float posX, float posY) {
		super(name, description);
		this.posX = posX;
		this.defaultX = posX;
		this.defaultY = posY;
		this.posY = posY;
		on(Events.HudRender.class, new Listener<Events.HudRender>() {
			@Override
			public void on(Events.HudRender e) {
				render(e.renderer, e.width, e.height);
			}
		});
	}

	/** Puts the box back where it starts on a fresh install. */
	public final void resetPosition() {
		posX = defaultX;
		posY = defaultY;
	}

	/** Content size in 1080p units, before the module scale. */
	protected abstract float contentWidth(Renderer r);

	protected abstract float contentHeight(Renderer r);

	/** Draws the content with its top left corner at x,y. s converts 1080p units to pixels. */
	protected abstract void draw(Renderer r, float x, float y, float s);

	/** False hides the box, for example when there is nothing to show. */
	protected boolean visible() {
		return true;
	}

	/** Set false by modules that draw their own boxes, such as Keystrokes. */
	protected boolean drawsBox() {
		return true;
	}

	public final void render(Renderer r, float screenW, float screenH) {
		if (!visible()) return;
		float s = screenH / 1080f * scale.v;
		float w = contentWidth(r) * s, h = contentHeight(r) * s;
		float x = Math.round(clamp(posX * screenW - w / 2f, 0, screenW - w));
		float y = Math.round(clamp(posY * screenH - h / 2f, 0, screenH - h));
		lastX = x;
		lastY = y;
		lastW = w;
		lastH = h;
		if (drawsBox()) box(r, x, y, w, h, s);
		draw(r, x, y, s);
	}

	/** The standard glass box: optional glow, blurred backdrop, tint and outline. */
	protected final void box(Renderer r, float x, float y, float w, float h, float s) {
		if (!background.on) return;
		Theme t = Onix.get().theme;
		float rad = radius.v * s;
		if (glow.on) r.glow(x, y, w, h, rad, 14f * s, t.withAlpha(t.accent, 0.55f));
		r.panel(x, y, w, h, rad, t.hudBackground, border.on ? Math.max(1f, 1.5f * s) : 0f, t.outline, blur.on);
	}

	private static float clamp(float v, float min, float max) {
		return v < min ? min : (v > max ? Math.max(min, max) : v);
	}
}
