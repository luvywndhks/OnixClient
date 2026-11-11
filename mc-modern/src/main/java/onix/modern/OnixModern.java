package onix.modern;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import onix.core.Onix;

/**
 * Entry point of the client on modern Minecraft (Fabric).
 *
 * It uses Fabric's own events for ticking and for drawing on the HUD, so it
 * needs no mixins and survives small changes in the game.
 */
public final class OnixModern implements ClientModInitializer {
	private final ModernRenderer renderer = new ModernRenderer();
	private Onix onix;
	private long lastFrame = System.nanoTime();
	private int selfTestTicks;

	/** The game is not ready when the mod is initialised, so the client is created on first use. */
	private Onix onix() {
		if (onix == null) {
			onix = Onix.init(new ModernPlatform());
			Runtime.getRuntime().addShutdownHook(new Thread(() -> onix.config.save(), "Onix config save"));
		}
		return onix;
	}

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			selfTest(mc);
			if (mc.level != null) onix().tick();
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Onix.ID, "hud"), this::hud);
	}

	/**
	 * Development check, off unless the game is started with -Donix.dev.selftest=true.
	 * Five seconds after start it creates the client, reports what loaded and quits,
	 * so a build can be checked without anyone at the keyboard.
	 */
	private void selfTest(Minecraft mc) {
		if (!Boolean.getBoolean("onix.dev.selftest") || ++selfTestTicks != 100) return;
		Onix client = onix();
		System.out.println("[Onix] Modern self test: " + client.modules.size() + " modules, profile " + client.config.active()
				+ ", menu key " + client.platform.keyName(client.global.menuKey.code));
		mc.stop();
	}

	private void hud(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		Onix client = onix();
		long now = System.nanoTime();
		float delta = Math.min(0.25f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		if (mc.gui.screen() == null) {
			if (client.pollInput()) {
				mc.gui.setScreen(new OnixScreen(renderer));
				return;
			}
		} else {
			client.resetInput();
		}
		// While the menu is open the HUD stays visible behind it, as on the HUD editor.
		int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
		renderer.begin(graphics);
		client.renderHud(renderer, w, h, delta);
		renderer.end();
	}
}
