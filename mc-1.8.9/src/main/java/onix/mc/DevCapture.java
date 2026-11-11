package onix.mc;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;

import onix.core.module.Module;

/**
 * Development aid, off unless the game is started with
 * {@code -Donix.dev.capture=<file.png>}. It opens the menu on the title screen,
 * saves one frame to that file and quits, so a build can be checked without
 * anyone at the keyboard.
 */
public final class DevCapture {
	private static final String PATH = System.getProperty("onix.dev.capture");
	private static int ticks, menuFrames;
	private static boolean opened, done;

	private DevCapture() {
	}

	private static int titleFrames, optionFrames;
	private static boolean optionsOpen;
	private static net.minecraft.client.gui.screen.Screen shotScreen;

	/** Called after the main menu has drawn a frame. */
	public static void afterTitleFrame(Minecraft mc) {
		if (PATH != null && ++titleFrames == 25) save(mc, PATH.replace(".png", "-title.png"));
	}

	/** Called after any vanilla screen has drawn a frame. */
	public static void afterVanillaFrame() {
		if (PATH == null || !optionsOpen || opened) return;
		Minecraft mc = Minecraft.getInstance();
		// One picture of each vanilla screen the run opens, ten frames after it appears.
		if (mc.screen != shotScreen) {
			shotScreen = mc.screen;
			optionFrames = 0;
		}
		if (shotScreen != null && ++optionFrames == 10) save(mc, PATH.replace(".png", "-" + shotScreen.getClass().getSimpleName() + ".png"));
	}

	public static void onTick(Minecraft mc) {
		if (PATH == null || opened) return;
		ticks++;
		if (ticks == 35) {
			// A vanilla screen, to check the themed buttons, sliders and background.
			optionsOpen = true;
			mc.openScreen(new net.minecraft.client.gui.screen.menu.options.OptionsScreen(mc.screen, mc.options));
		}
		if (ticks == 47) mc.openScreen(new net.minecraft.client.gui.screen.menu.options.ControlsOptionsScreen(mc.screen, mc.options));
		if (ticks < 60) return;
		opened = true;
		// These classes normally load on joining a world. Loading them here applies their
		// mixins now, so a broken hook shows up in this run instead of on a server.
		try {
			Class<?> a = net.minecraft.client.network.handler.ClientPlayNetworkHandler.class;
			Class<?> b = net.minecraft.client.ClientPlayerInteractionManager.class;
			Class.forName(a.getName(), true, a.getClassLoader());
			Class.forName(b.getName(), true, b.getClassLoader());
			Class<?> c = net.minecraft.client.entity.living.player.ClientPlayerEntity.class;
			Class.forName(c.getName(), true, c.getClassLoader());
			Class<?> d = net.minecraft.client.gui.screen.menu.GameMenuScreen.class;
			Class.forName(d.getName(), true, d.getClassLoader());
			System.out.println("[Onix] World-only mixins applied");
		} catch (Throwable t) {
			System.err.println("[Onix] World-only mixin FAILED: " + t);
		}
		Bridge bridge = Bridge.get();
		for (Module m : bridge.onix.modules) {
			if (m.name.equals("Keystrokes") || m.name.equals("FPS Counter") || m.name.equals("CPS Counter") || m.name.equals("Clock") || m.name.equals("Memory Display")) m.setEnabled(true);
		}
		// The editor shot shows the HUD in the game font; the menu shots go back to the client font.
		bridge.onix.global.hudFont.set("Minecraft");
		mc.openScreen(new OnixScreen());
	}

	/** In capture mode, paints a bright stand-in for a game world so the glass has something to show. */
	static boolean background(Minecraft mc, onix.gl.GlRenderer r) {
		if (PATH == null) return false;
		float w = mc.width, h = mc.height;
		r.begin(w, h);
		r.panel(0, 0, w, h * 0.55f, 0, 0xFF7FB2FF, 0, 0, false);
		r.panel(0, h * 0.55f, w, h * 0.45f, 0, 0xFF5E9B3C, 0, 0, false);
		r.panel(w * 0.08f, h * 0.3f, w * 0.2f, h * 0.4f, 0, 0xFF8A5A2B, 0, 0, false);
		r.panel(w * 0.03f, h * 0.12f, w * 0.3f, h * 0.25f, 0, 0xFF2F7D32, 0, 0, false);
		r.panel(w * 0.55f, h * 0.6f, w * 0.3f, h * 0.2f, 0, 0xFF3F76E4, 0, 0, false);
		r.panel(w * 0.7f, h * 0.1f, w * 0.12f, h * 0.08f, 0, 0xFFFFFFFF, 0, 0, false);
		r.panel(w * 0.4f, h * 0.35f, w * 0.1f, h * 0.3f, 0, 0xFFD9C27A, 0, 0, false);
		r.end();
		return true;
	}

	static boolean active() {
		return PATH != null;
	}

	static void afterMenuFrame(Minecraft mc) {
		if (PATH == null || done) return;
		Bridge bridge = Bridge.get();

		menuFrames++;
		if (menuFrames == 20) System.out.println("[Onix] Motion blur self test, GL error " + bridge.selfTestMotionBlur());
		if (menuFrames == 40) {
			// First the HUD editor, then the menu behind its Settings button.
			save(mc, PATH.replace(".png", "-editor.png"));
			bridge.onix.global.hudFont.set("Onix");
			bridge.onix.gui.showMenu();
		}
		// A few frames into the menu, to see the entrance animation part way through.
		if (menuFrames == 52) save(mc, PATH.replace(".png", "-anim.png"));
		if (menuFrames == 105) {
			// The module menu first, then the Profiles page for the last picture.
			save(mc, PATH.replace(".png", "-menu.png"));
			bridge.onix.gui.showModule(bridge.onix.profiles);
		}
		if (menuFrames < 150) return;
		done = true;
		save(mc, PATH);
		mc.stop();
	}

	private static void save(Minecraft mc, String path) {
		try {
			int w = mc.width, h = mc.height;
			ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
			GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int i = (x + (h - 1 - y) * w) * 4;
					image.setRGB(x, y, (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | (pixels.get(i + 2) & 0xFF));
				}
			}
			ImageIO.write(image, "png", new File(path));
			System.out.println("[Onix] Dev capture saved to " + path + " (GL error " + GL11.glGetError() + ")");
		} catch (Exception e) {
			System.err.println("[Onix] Dev capture failed: " + e);
		}
	}
}
