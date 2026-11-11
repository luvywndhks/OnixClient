package onix.modern;


import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import onix.core.Onix;
import onix.core.ui.ClickGui;

/** Hosts the core menu inside a game screen and forwards input to it. */
public final class OnixScreen extends Screen {
	private final ModernRenderer renderer;
	private final ClickGui gui;
	private long lastFrame = System.nanoTime();

	public OnixScreen(ModernRenderer renderer) {
		super(Component.literal(Onix.NAME));
		this.renderer = renderer;
		this.gui = Onix.get().gui;
	}

	@Override
	protected void init() {
		gui.open();
	}

	@Override
	public void removed() {
		// Covers the screen being replaced without our closing animation.
		gui.close();
	}

	/** The mouse in real pixels, which is what the menu works in. */
	private float scale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
		long now = System.nanoTime();
		float delta = Math.min(0.25f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		Minecraft mc = Minecraft.getInstance();
		// The menu belongs to a game in progress.
		if (gui.isClosed() || mc.level == null) {
			mc.gui.setScreen(null);
			return;
		}
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		renderer.begin(graphics);
		gui.render(renderer, w, h, (float) mc.mouseHandler.xpos(), (float) mc.mouseHandler.ypos(), delta);
		renderer.end();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		gui.mouseDown((float) event.x() * scale(), (float) event.y() * scale(), event.button());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		gui.mouseUp(event.button());
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		gui.scroll((float) x * scale(), (float) y * scale(), (float) vertical);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		// Enter arrives as a key, not as a typed character; the menu expects it as a carriage return.
		char typed = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER ? '\r' : 0;
		gui.keyDown(key, typed, key == InputConstants.KEY_ESCAPE, key == InputConstants.KEY_BACKSPACE);
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String text = event.codepointAsString();
		for (int i = 0; i < text.length(); i++) gui.keyDown(0, text.charAt(i), false, false);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
