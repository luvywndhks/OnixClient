package onix.mc;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.screen.Screen;

import onix.core.ui.ClickGui;
import onix.gl.GlRenderer;

/** Hosts the core menu inside a vanilla screen and forwards input to it. */
public final class OnixScreen extends Screen {
	private final Bridge bridge = Bridge.get();
	private final ClickGui gui = bridge.onix.gui;
	private long lastFrame = System.nanoTime();

	@Override
	public void init() {
		Keyboard.enableRepeatEvents(true);
		gui.open();
	}

	@Override
	public void removed() {
		Keyboard.enableRepeatEvents(false);
		// Covers the screen being replaced without our closing animation.
		gui.close();
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		long now = System.nanoTime();
		float delta = Math.min(0.25f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		// The menu belongs to a game in progress; it never stays open on the title screen.
		if (gui.isClosed() || (minecraft.world == null && !DevCapture.active())) {
			minecraft.openScreen(null);
			return;
		}
		GlRenderer r = bridge.renderer;
		int w = minecraft.width, h = minecraft.height;
		// Outside a world nothing repaints the screen, and blurring a stale frame would
		// feed the menu back into itself. Paint a background first.
		if (minecraft.world == null && !DevCapture.background(minecraft, r)) renderBackground();
		r.begin(w, h);
		gui.renderBackdrop(r, w, h, delta);
		r.end();
		// The HUD goes between the blur and the menu: sharp in the editor, fading out behind the menu.
		if (minecraft.world != null || DevCapture.active()) {
			r.begin(w, h);
			r.pushAlpha(gui.hudAlpha());
			bridge.onix.renderHud(r, w, h, delta);
			r.popAlpha();
			r.end();
		}
		r.begin(w, h);
		// The menu works in real pixels, so it ignores the vanilla GUI scale.
		gui.render(r, w, h, Mouse.getX(), h - Mouse.getY() - 1, delta);
		r.end();
		DevCapture.afterMenuFrame(minecraft);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		// A capture run must not react to whatever the mouse happens to be doing.
		if (DevCapture.active()) return;
		gui.mouseDown(Mouse.getX(), minecraft.height - Mouse.getY() - 1, button);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		gui.mouseUp(button);
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0 && !DevCapture.active()) gui.scroll(Mouse.getX(), minecraft.height - Mouse.getY() - 1, wheel / 120f);
	}

	@Override
	protected void keyPressed(char typed, int code) {
		// No super call: escape runs our closing animation instead of closing at once.
		if (DevCapture.active()) return;
		gui.keyDown(code, typed, code == Keyboard.KEY_ESCAPE, code == Keyboard.KEY_BACK);
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
