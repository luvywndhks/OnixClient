package onix.mc;

import java.io.File;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import org.lwjgl.opengl.Display;

import java.util.List;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.options.KeyBinding;

import onix.core.Onix;
import onix.core.event.Events;
import onix.core.platform.Platform;
import onix.core.modules.MotionBlurModule;
import onix.gl.GlRenderer;
import onix.gl.MotionBlur;

/** Minecraft 1.8.9 behind the core's {@link Platform} interface, plus the per-frame hooks. */
public final class Bridge implements Platform {
	private static Bridge instance;

	public final Onix onix;
	public final GlRenderer renderer = new GlRenderer();
	private final Minecraft mc = Minecraft.getInstance();
	private final int[] controls = new int[9];
	private long lastFrame = System.nanoTime();

	private Bridge() {
		instance = this;
		onix = Onix.init(this);
		renderer.setTextDrawer(new GlRenderer.TextDrawer() {
			@Override
			public int width(String text) {
				return mc.textRenderer.getWidth(text);
			}

			@Override
			public void draw(String text, float x, float y, float scale, int argb) {
				drawGameText(text, x, y, scale, argb);
			}
		});
		renderer.setItemDrawer(new GlRenderer.ItemDrawer() {
			@Override
			public void draw(int slot, float x, float y, float size, float alpha) {
				drawItem(slot, x, y, size, alpha);
			}
		});
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				onix.config.save();
			}
		}, "Onix config save"));
	}

	/** Created on first use, which is always on the client thread. */
	public static Bridge get() {
		if (instance == null) new Bridge();
		return instance;
	}

	// ---------------------------------------------------------------- hooks

	public void onTick() {
		if (mc.world != null) {
			onix.tick();
			tickCombat();
		}
	}

	/** Called at the end of the vanilla HUD pass. */
	public void onHudRender() {
		long now = System.nanoTime();
		float delta = Math.min(0.25f, (now - lastFrame) / 1e9f);
		lastFrame = now;

		if (mc.screen == null) {
			if (onix.pollInput()) {
				mc.openScreen(new OnixScreen());
				return;
			}
		} else {
			onix.resetInput();
		}
		// While the menu is open it draws the HUD itself, between its blur and its panels.
		if (mc.options.hideGui || mc.screen instanceof OnixScreen) return;

		renderer.begin(mc.width, mc.height);
		onix.renderHud(renderer, mc.width, mc.height, delta);
		renderer.end();
	}

	// ------------------------------------------------------------- platform

	@Override
	public File configDir() {
		return new File(mc.gameDir, Onix.ID);
	}

	@Override
	public int fps() {
		return Minecraft.getCurrentFps();
	}

	@Override
	public boolean inWorld() {
		return mc.player != null && mc.world != null;
	}

	@Override
	public double playerX() {
		return mc.player == null ? 0 : mc.player.x;
	}

	@Override
	public double playerY() {
		return mc.player == null ? 0 : mc.player.y;
	}

	@Override
	public double playerZ() {
		return mc.player == null ? 0 : mc.player.z;
	}

	@Override
	public boolean isDown(int code) {
		if (code == 0) return false;
		if (code < 0) return Mouse.isButtonDown(code + 100);
		return code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code);
	}

	@Override
	public String keyName(int code) {
		if (code < 0) {
			int button = code + 100;
			return button == 0 ? "LMB" : button == 1 ? "RMB" : button == 2 ? "MMB" : "Mouse " + (button + 1);
		}
		String name = Keyboard.getKeyName(code);
		if (name == null) return "Key " + code;
		// LWJGL names are upper case ("RSHIFT"); single letters stay as they are.
		return name.length() <= 1 ? name : name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public int[] controlKeys() {
		GameOptions o = mc.options;
		controls[0] = o.forwardKey.getKeyCode();
		controls[1] = o.leftKey.getKeyCode();
		controls[2] = o.backKey.getKeyCode();
		controls[3] = o.rightKey.getKeyCode();
		controls[4] = o.jumpKey.getKeyCode();
		controls[5] = o.sneakKey.getKeyCode();
		controls[6] = o.sprintKey.getKeyCode();
		controls[7] = o.attackKey.getKeyCode();
		controls[8] = o.useKey.getKeyCode();
		return controls;
	}

	@Override
	public void setSprinting(boolean held) {
		int code = mc.options.sprintKey.getKeyCode();
		// On release, fall back to whether the player is really holding the key.
		KeyBinding.set(code, held || isDown(code));
	}

	@Override
	public float gamma() {
		return mc.options.gamma;
	}

	@Override
	public void setGamma(float gamma) {
		mc.options.gamma = gamma;
	}

	@Override
	public int defaultMenuKey() {
		return Keyboard.KEY_RSHIFT;
	}

	@Override
	public int ping() {
		if (mc.player == null || mc.getNetworkHandler() == null) return -1;
		PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid());
		return info == null ? -1 : info.getPing();
	}

	@Override
	public String serverAddress() {
		ServerListEntry entry = mc.getCurrentServerEntry();
		return entry == null ? null : entry.ip;
	}

	@Override
	public float yaw() {
		return mc.player == null ? 0f : mc.player.yaw;
	}

	@Override
	public long timeOfDay() {
		return mc.world == null ? 0L : mc.world.getTimeOfDay();
	}

	@Override
	public float health() {
		return mc.player == null ? 0f : mc.player.getHealth();
	}

	@Override
	public float maxHealth() {
		return mc.player == null ? 20f : mc.player.getMaxHealth();
	}

	@Override
	public void setHitboxes(boolean shown) {
		mc.getEntityRenderDispatcher().setRenderHitboxes(shown);
	}

	@Override
	public boolean windowFocused() {
		return Display.isActive();
	}

	@Override
	public int fpsLimit() {
		return mc.options.fpsLimit;
	}

	@Override
	public void setFpsLimit(int limit) {
		mc.options.fpsLimit = limit;
	}

	@Override
	public int defaultZoomKey() {
		return Keyboard.KEY_C;
	}

	@Override
	public int defaultFreelookKey() {
		return Keyboard.KEY_LMENU;
	}

	private ItemStack stack(int slot) {
		if (mc.player == null) return null;
		// Armor is stored boots first; slot 0 is the helmet.
		if (slot >= INVENTORY) return slot - INVENTORY < 36 ? mc.player.inventory.items[slot - INVENTORY] : null;
		return slot == 4 ? mc.player.getItemInHand() : mc.player.inventory.armor[3 - slot];
	}

	@Override
	public int itemDurability(int slot) {
		ItemStack stack = stack(slot);
		if (stack == null) return NO_ITEM;
		return stack.isDamageable() ? stack.getMaxDamage() - stack.getDamage() : UNBREAKABLE;
	}

	@Override
	public int itemMaxDurability(int slot) {
		ItemStack stack = stack(slot);
		return stack == null ? 0 : stack.getMaxDamage();
	}

	@Override
	public int itemCount(int slot) {
		ItemStack stack = stack(slot);
		return stack == null ? 0 : stack.size;
	}

	private static final String[] ROMAN = { "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X" };

	@Override
	public void effects(List<String> lines, List<Integer> ticksLeft) {
		lines.clear();
		ticksLeft.clear();
		if (mc.player == null) return;
		for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
			int level = effect.getAmplifier();
			String name = I18n.translate(effect.getName()) + (level > 0 && level < ROMAN.length ? ROMAN[level] : "");
			int seconds = effect.getDuration() / 20;
			String time = effect.getDuration() > 32000 ? "**:**" : seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
			lines.add(name + "  " + time);
			ticksLeft.add(effect.getDuration());
		}
	}

	/** Item icons for the renderer: the game's own GUI item drawing, at any pixel size. */
	private void drawItem(int slot, float x, float y, float size, float alpha) {
		ItemStack stack = stack(slot);
		if (stack == null) return;
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glOrtho(0, mc.width, mc.height, 0, 1000, 3000);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glTranslatef(x, y, -2000f);
		GL11.glScalef(size / 16f, size / 16f, 1f);
		GlStateManager.enableRescaleNormal();
		GlStateManager.enableBlend();
		GlStateManager.color4f(1f, 1f, 1f, 1f);
		Lighting.turnOnGui();
		mc.getItemRenderer().renderGuiItem(stack, 0, 0);
		Lighting.turnOff();
		GlStateManager.disableRescaleNormal();
		GlStateManager.disableBlend();
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();
	}

	// --------------------------------------------------------------- combat

	private Entity lastTarget;
	private int targetTicks, targetHurt, playerHurt;

	/** Called when the player attacks an entity. */
	public void onAttack(Entity target) {
		if (mc.player == null || target == null) return;
		Vec3d eyes = mc.player.getEyePosition(1f);
		// Reach is the distance to the nearest point of the target's hitbox.
		Box box = target.getShape();
		double cx = Math.max(box.minX, Math.min(eyes.x, box.maxX));
		double cy = Math.max(box.minY, Math.min(eyes.y, box.maxY));
		double cz = Math.max(box.minZ, Math.min(eyes.z, box.maxZ));
		double dx = eyes.x - cx, dy = eyes.y - cy, dz = eyes.z - cz;
		Events.Attack.INSTANCE.reach = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		onix.events.post(Events.Attack.INSTANCE);
		lastTarget = target;
		targetTicks = 0;
		targetHurt = target instanceof LivingEntity ? ((LivingEntity) target).damagedTime : 0;
	}

	private void tickCombat() {
		if (mc.player == null) return;
		// A hit landed when the entity we just attacked starts a new hurt animation.
		if (lastTarget instanceof LivingEntity) {
			int hurt = ((LivingEntity) lastTarget).damagedTime;
			if (hurt > targetHurt) {
				onix.events.post(Events.HitLanded.INSTANCE);
				lastTarget = null;
			} else {
				targetHurt = hurt;
				if (++targetTicks > 10) lastTarget = null;
			}
		}
		int hurt = mc.player.damagedTime;
		if (hurt > playerHurt) onix.events.post(Events.PlayerHurt.INSTANCE);
		playerHurt = hurt;
	}

	// ------------------------------------------------------------- freelook

	private int savedPerspective;

	/** Called once per frame before the camera is set up. */
	public void updateFreelook() {
		if (mc.player == null) return;
		int change = onix.freelook.update(mc.player.yaw, mc.player.pitch);
		if (change > 0) {
			savedPerspective = mc.options.perspective;
			mc.options.perspective = 1;
		} else if (change < 0) {
			mc.options.perspective = savedPerspective;
		}
	}

	@Override
	public float saturation() {
		return mc.player == null ? 0f : mc.player.getHungerManager().getSaturationLevel();
	}

	@Override
	public int countItems(int kind) {
		if (mc.player == null) return 0;
		int total = 0;
		ItemStack[] items = mc.player.inventory.items;
		for (int i = 0; i < items.length; i++) {
			ItemStack stack = items[i];
			if (stack == null) continue;
			Item item = stack.getItem();
			boolean match;
			if (kind == ARROWS) match = item == Items.ARROW;
			else if (kind == SOUPS) match = item == Items.MUSHROOM_STEW;
			// The low four bits of a potion's metadata are its effect; 5 is instant health.
			else match = item == Items.POTION && (stack.getMetadata() & 15) == 5;
			if (match) total += stack.size;
		}
		return total;
	}

	/** Set every frame by the crosshair hook: whether vanilla would draw a crosshair. */
	public boolean crosshairWanted;

	@Override
	public boolean crosshairWanted() {
		return crosshairWanted && mc.screen == null;
	}

	@Override
	public boolean lookingAtEntity() {
		return mc.crosshairTarget != null && mc.crosshairTarget.type == HitResult.Type.ENTITY;
	}

	@Override
	public void setTimeOfDay(long time) {
		if (mc.world != null) mc.world.setTimeOfDay(time);
	}

	@Override
	public void setWeather(float rain, float thunder) {
		if (mc.world == null) return;
		mc.world.setRain(rain);
		mc.world.setThunder(thunder);
	}

	@Override
	public void setSneaking(boolean held) {
		int code = mc.options.sneakKey.getKeyCode();
		KeyBinding.set(code, held || isDown(code));
	}

	@Override
	public void sendChat(String message) {
		if (mc.player != null) mc.player.sendChat(message);
	}

	@Override
	public String username() {
		return mc.getSession().getUsername();
	}

	/** Called for each chat line the server sends. */
	public void onChat(String formatted) {
		// Strip the two-character color codes so modules can match plain text.
		StringBuilder plain = new StringBuilder(formatted.length());
		for (int i = 0; i < formatted.length(); i++) {
			char c = formatted.charAt(i);
			if (c == '§' && i + 1 < formatted.length()) i++;
			else plain.append(c);
		}
		Events.Chat.INSTANCE.text = plain.toString();
		onix.events.post(Events.Chat.INSTANCE);
	}

	/** Text in the game's own font, for HUD modules when the player picks it. */
	private void drawGameText(String text, float x, float y, float scale, int argb) {
		// The game treats an alpha under 4 as "no alpha given" and draws fully opaque.
		if (((argb >>> 24) & 0xFF) < 8) return;
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glOrtho(0, mc.width, mc.height, 0, 1000, 3000);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		GL11.glTranslatef(x, y, -2000f);
		GL11.glScalef(scale, scale, 1f);
		GlStateManager.enableBlend();
		GlStateManager.enableAlphaTest();
		mc.textRenderer.drawWithShadow(text, 0f, 0f, argb);
		GlStateManager.disableBlend();
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPopMatrix();
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		GL11.glPopMatrix();
	}

	// ---------------------------------------------------------- motion blur

	private final MotionBlur motionBlurEffect = new MotionBlur();
	private long lastBlurFrame = System.nanoTime();

	/** Called when the world has been drawn and the HUD has not, so only the world is blurred. */
	public void onWorldRendered() {
		long now = System.nanoTime();
		float delta = Math.min(0.25f, (now - lastBlurFrame) / 1e9f);
		lastBlurFrame = now;
		MotionBlurModule m = onix.motionBlur;
		if (!m.isEnabled() || mc.world == null) {
			motionBlurEffect.reset();
			return;
		}
		motionBlurEffect.apply(m.mode(), m.strength(), m.frames(), delta, mc.width, mc.height);
	}

	/** Development check: runs every blur mode on the current framebuffer and reports the GL error code. */
	int selfTestMotionBlur() {
		for (int mode = MotionBlur.TRAIL; mode <= MotionBlur.AVERAGE; mode++) {
			for (int i = 0; i < 4; i++) motionBlurEffect.apply(mode, 0.5f, 5, 0.016f, mc.width, mc.height);
		}
		motionBlurEffect.reset();
		return GL11.glGetError();
	}

	@Override
	public void openFolder(File folder) {
		try {
			if (!folder.isDirectory() && !folder.mkdirs()) return;
			// Explorer directly on Windows; the generic way elsewhere.
			if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
				new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
			} else {
				java.awt.Desktop.getDesktop().open(folder);
			}
		} catch (Exception e) {
			System.err.println("[Onix] Could not open " + folder + ": " + e);
		}
	}

	// -------------------------------------------------------------- snaplook

	private int snapSaved = -1;

	/** Called once per frame: forces the view while a Snaplook key is held and puts it back on release. */
	public void updateSnaplook() {
		if (mc.player == null || onix.freelook.looking) return;
		int wanted = onix.perspective.wanted();
		if (wanted >= 0) {
			if (snapSaved < 0) snapSaved = mc.options.perspective;
			mc.options.perspective = wanted;
		} else if (snapSaved >= 0) {
			mc.options.perspective = snapSaved;
			snapSaved = -1;
		}
	}
}
