package onix.core.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import onix.core.Onix;
import onix.core.anim.Anim;
import onix.core.config.ConfigManager;
import onix.core.module.HudModule;
import onix.core.module.Module;
import onix.core.render.Renderer;
import onix.core.setting.Setting;
import onix.core.theme.Theme;

/**
 * The client menu: search bar and module list on the left, the selected
 * module's settings on the right.
 *
 * All numbers are in "units": pixels on a 1080p screen, measured from the
 * official screenshot. They are scaled to the real window on draw.
 *
 * It is immediate mode: a click is remembered and consumed by whichever widget
 * is drawn under it in the next frame, so there is no separate hit-test pass
 * to keep in sync with the layout.
 */
public final class ClickGui {
	// Layout of the reference screenshot (1920x1080).
	private static final float LEFT = 480f, LIST_W = 463f, TOP = 55f, SEARCH_H = 46f;
	private static final float LIST_Y = 115f, BOTTOM = 1015f;
	private static final float SET_X = 975f, SET_W = 883f, TOTAL_W = SET_X + SET_W - LEFT;
	private static final float PANEL_R = 14f, ROW_R = 8f, BORDER = 2f;
	private static final float LIST_ROW_H = 38f, LIST_PITCH = 46f;
	private static final float SET_ROW_H = 46f, SET_PITCH = 57f;

	private static final int[] PALETTE = { 0xFFFFFFFF, 0xFF146B8C, 0xFF2D9CDB, 0xFF27AE60, 0xFFF2C94C, 0xFFF2994A,
			0xFFEB5757, 0xFFBB6BD9, 0xFFFF7EB6, 0xFF1A1A1A };

	private final Onix onix;
	private final Theme t;

	private final Anim open = new Anim(0f, 10f);
	private boolean closing = true;

	private String search = "";
	private String filterKey = null;
	private final List<Module> filtered = new ArrayList<Module>();

	private Module selected;
	private final Anim pageFade = new Anim(1f, 14f);
	private final Anim listScroll = new Anim(0f, 18f), setScroll = new Anim(0f, 18f);
	private float listTarget, setTarget, listMax, setMax;

	private final Map<Object, Anim> hover = new IdentityHashMap<Object, Anim>();
	private final Map<Object, Anim> switches = new IdentityHashMap<Object, Anim>();
	private final Map<Object, Anim> appearList = new IdentityHashMap<Object, Anim>();
	private final Map<Object, Anim> appearPage = new IdentityHashMap<Object, Anim>();
	/** Seconds since the menu, and the current settings page, were shown; they stagger the rows. */
	private float listClock, pageClock;
	private final Map<String, Anim> sections = new java.util.HashMap<String, Anim>();
	private final Set<String> collapsed = new HashSet<String>();

	private boolean menu;
	private final Anim menuFade = new Anim(0f, 9.5f);
	private HudModule dragHud;
	private float dragHudX, dragHudY;
	private final Map<Object, Anim> blockedAnim = new IdentityHashMap<Object, Anim>();
	private boolean advanced;
	private float openAlpha, menuAlpha;
	/** The profile whose name is being typed, or null. */
	private String renaming;
	private final StringBuilder renameText = new StringBuilder();
	private Setting.Number dragging;
	private float dragX, dragW;
	private Setting.Key listening;

	// Per-frame input state.
	private float s, ox, mx, my, dt;
	private boolean clickPending, mouseHeld;
	private int clickButton;
	private float clickX, clickY;
	private long caretStart = System.nanoTime();

	public ClickGui(Onix onix) {
		this.onix = onix;
		this.t = onix.theme;
		this.selected = onix.global;
	}

	public void open() {
		closing = false;
		menu = false;
		menuFade.snap(0f);
		dragHud = null;
		listening = null;
		dragging = null;
		clickPending = false;
		if (!onix.global.animations.on) open.snap(1f);
	}

	/** Leaves the HUD editor for the module menu. */
	public void showMenu() {
		menu = true;
		// Every row plays its entrance again each time the menu is shown.
		appearList.clear();
		appearPage.clear();
		listClock = 0f;
		pageClock = 0f;
		dragHud = null;
		if (!onix.global.animations.on) menuFade.snap(1f);
	}

	/** Starts the closing animation. The platform closes the screen once {@link #isClosed()}. */
	public void close() {
		if (closing) return;
		closing = true;
		listening = null;
		dragging = null;
		if (!onix.global.animations.on) open.snap(0f);
		onix.config.save();
	}

	public boolean isClosed() {
		return closing && open.value <= 0.01f;
	}

	public boolean isListeningForKey() {
		return listening != null;
	}

	// ---------------------------------------------------------------- input

	public void mouseDown(float x, float y, int button) {
		clickPending = true;
		clickButton = button;
		clickX = x;
		clickY = y;
		if (button == 0) mouseHeld = true;
	}

	public void mouseUp(int button) {
		if (button != 0) return;
		mouseHeld = false;
		dragHud = null;
		dragging = null;
	}

	/** amount: positive scrolls up, one notch is 1. */
	public void scroll(float x, float y, float amount) {
		if (!menu) return;
		float ux = x / s - ox;
		if (ux >= SET_X) setTarget -= amount * SET_PITCH * 1.5f;
		else listTarget -= amount * LIST_PITCH * 2f;
	}

	/**
	 * @param code   the platform key code
	 * @param typed  the character, or 0
	 * @param escape true for the escape key
	 * @param erase  true for backspace
	 */
	public void keyDown(int code, char typed, boolean escape, boolean erase) {
		if (listening != null) {
			listening.set(escape ? 0 : code);
			listening = null;
			return;
		}
		if (renaming != null) {
			// Typing a profile name: Enter keeps it, Escape drops it.
			if (escape) finishRename(false);
			else if (typed == 13 || typed == 10) finishRename(true);
			else if (erase) {
				if (renameText.length() > 0) renameText.setLength(renameText.length() - 1);
			} else if ((Character.isLetterOrDigit(typed) || typed == ' ' || typed == '-' || typed == '_')
					&& renameText.length() < ConfigManager.MAX_NAME) {
				renameText.append(typed);
			}
			return;
		}
		if (escape) {
			close();
			return;
		}
		if (!menu) return;
		if (erase) {
			if (search.length() > 0) search = search.substring(0, search.length() - 1);
		} else if (typed >= ' ' && typed != 127 && search.length() < 32) {
			search += typed;
		}
		caretStart = System.nanoTime();
	}

	// --------------------------------------------------------------- render

	/** Steps the open and menu fades. Runs once per frame, from whichever draw call comes first. */
	private void advance(float delta) {
		if (advanced) return;
		advanced = true;
		dt = Math.min(delta, 0.1f);
		openAlpha = open.update(closing ? 0f : 1f, dt);
		menuAlpha = menuFade.update(menu ? 1f : 0f, dt);
	}

	/**
	 * Frosts the game behind the editor and the menu. The platform draws this first,
	 * then the HUD, then {@link #render}, so HUD elements stay sharp while being edited.
	 */
	public void renderBackdrop(Renderer r, float width, float height, float delta) {
		advance(delta);
		if (openAlpha <= 0.01f || !onix.global.blur.on) return;
		r.pushAlpha(openAlpha);
		r.panel(0f, 0f, width, height, 0f, 0x00000000, 0f, 0, true);
		r.popAlpha();
	}

	/** How visible the HUD should be: full in the editor, gone behind the menu. */
	public float hudAlpha() {
		return 1f - menuFade.value * open.value;
	}

	public void render(Renderer r, float width, float height, float mouseX, float mouseY, float delta) {
		advance(delta);
		advanced = false;
		s = height / 1080f;
		// The layout lives in a 1920-wide frame, exactly where the original puts it; wider or
		// narrower screens centre that frame.
		ox = (width / s - 1920f) / 2f;
		mx = mouseX;
		my = mouseY;

		float a = openAlpha;
		if (a <= 0.01f) {
			clickPending = false;
			return;
		}
		float eased = a;
		float rise = 0f;
		listClock += dt;
		pageClock += dt;

		r.pushAlpha(eased);
		// Right Shift lands on the HUD editor first; its Settings button opens the menu.
		if (menuAlpha < 0.99f) drawEditor(r, width, height, 1f - menuAlpha);
		if (menuAlpha > 0.01f) {
			r.pushAlpha(menuAlpha);
			// Each panel grows out of its bottom right corner while the menu fades in.
			float pop = onix.global.animations.on ? 0.72f + 0.28f * Anim.smooth(menuAlpha) : 1f;
			r.pushScale(pop, px(LEFT + LIST_W), py(TOP + SEARCH_H));
			drawSearch(r, rise, false);
			r.popScale();
			r.pushScale(pop, px(LEFT + LIST_W), py(BOTTOM));
			drawList(r, rise, false);
			r.popScale();
			r.pushScale(pop, px(SET_X + SET_W), py(BOTTOM));
			drawSettings(r, rise, false);
			r.popScale();
			r.popAlpha();
		}
		r.popAlpha();

		if (dragging != null && mouseHeld) {
			float f = (mx - dragX) / dragW;
			dragging.set(dragging.min + Math.max(0f, Math.min(1f, f)) * (dragging.max - dragging.min));
		}
		// A click that landed on nothing is dropped, never carried to a later frame.
		clickPending = false;
	}

	/** The HUD editor: drag enabled HUD modules around, or press Settings for the menu. */
	private void drawEditor(Renderer r, float width, float height, float alpha) {
		r.pushAlpha(alpha);
		float bw = 180f, bh = 60f, bx = 960f - bw / 2f, by = 540f - bh / 2f;
		boolean overButton = !menu && over(bx, by, bw, bh);
		float hv = anim(hover, this, 16f).update(overButton ? 1f : 0f, dt);
		// The button sits in a soft halo of its own color, brighter under the cursor.
		r.glow(px(bx), py(by), u(bw), u(bh), u(12f), u(38f), t.withAlpha(t.button, 0.60f + 0.25f * hv));
		r.panel(px(bx), py(by), u(bw), u(bh), u(12f), Theme.mix(t.button, 0xFFFFFFFF, 0.14f * hv), 0f, 0, false);
		float size = u(24f);
		r.text("Settings", px(bx) + (u(bw) - r.textWidth("Settings", size, Renderer.REGULAR)) / 2f,
				py(by) + (u(bh) - r.lineHeight(size)) / 2f, size, t.text, Renderer.REGULAR);
		if (!menu) {
			if (click(bx, by, bw, bh, 0)) showMenu();
			for (int i = onix.modules.size() - 1; i >= 0; i--) {
				Module m = onix.modules.get(i);
				if (!(m instanceof HudModule) || !m.isEnabled()) continue;
				HudModule h = (HudModule) m;
				if (h.lastW <= 0f) continue;
				boolean inside = mx >= h.lastX && mx < h.lastX + h.lastW && my >= h.lastY && my < h.lastY + h.lastH;
				float glow = anim(hover, h, 16f).update(inside || h == dragHud ? 1f : 0f, dt);
				// A module under the cursor gets a frame, so it is clear what a drag will move.
				if (glow > 0.01f) {
					float pad = u(4f);
					r.panel(h.lastX - pad, h.lastY - pad, h.lastW + pad * 2f, h.lastH + pad * 2f, u(8f),
							t.withAlpha(0xFFFFFFFF, 0.10f * glow), u1(1.5f), t.withAlpha(t.text, glow), false);
				}
				boolean hit = clickPending && clickX >= h.lastX && clickX < h.lastX + h.lastW && clickY >= h.lastY && clickY < h.lastY + h.lastH;
				if (hit && clickButton == 0) {
					clickPending = false;
					dragHud = h;
					dragHudX = clickX - (h.lastX + h.lastW / 2f);
					dragHudY = clickY - (h.lastY + h.lastH / 2f);
				} else if (hit && clickButton == 1) {
					// Right click jumps straight to that module's settings.
					clickPending = false;
					select(h);
					showMenu();
				}
			}
			if (dragHud != null && mouseHeld) {
				dragHud.posX = Math.max(0f, Math.min(1f, (mx - dragHudX) / width));
				dragHud.posY = Math.max(0f, Math.min(1f, (my - dragHudY) / height));
			}
		}
		r.popAlpha();
	}

	private void drawSearch(Renderer r, float rise, boolean blur) {
		float x = LEFT, y = TOP + rise;
		r.panel(px(x), py(y), u(LIST_W), u(SEARCH_H), u(12f), t.panel, u1(BORDER), t.outline, blur);
		float size = u(17f);
		float ty = py(y) + (u(SEARCH_H) - r.lineHeight(size)) / 2f;
		if (search.isEmpty()) {
			r.text("Search...", px(x + 16f), ty, size, t.textDim, Renderer.REGULAR);
		} else {
			r.text(search, px(x + 16f), ty, size, t.text, Renderer.REGULAR);
		}
		if (((System.nanoTime() - caretStart) / 500000000L) % 2 == 0 && !search.isEmpty()) {
			float cx = px(x + 16f) + r.textWidth(search, size, Renderer.REGULAR) + u(2f);
			r.panel(cx, ty + u(2f), Math.max(1f, u(1.5f)), r.lineHeight(size) - u(4f), 0f, t.text, 0f, 0, false);
		}
	}

	private void refilter() {
		if (search.equals(filterKey)) return;
		filterKey = search;
		filtered.clear();
		String needle = search.toLowerCase(Locale.ROOT);
		for (Module m : onix.modules) {
			if (needle.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(needle)) filtered.add(m);
		}
		listTarget = 0f;
	}

	private void drawList(Renderer r, float rise, boolean blur) {
		refilter();
		float x = LEFT, y = LIST_Y + rise, h = BOTTOM - LIST_Y;
		r.panel(px(x), py(y), u(LIST_W), u(h), u(PANEL_R), t.panel, u1(BORDER), t.outline, blur);

		float inner = h - 20f;
		float content = filtered.size() * LIST_PITCH - (LIST_PITCH - LIST_ROW_H) + 8f;
		listMax = Math.max(0f, content - inner);
		listTarget = Math.max(0f, Math.min(listMax, listTarget));
		float scroll = listScroll.update(listTarget, dt);

		float clipY = y + 10f;
		r.pushClip(px(x), py(clipY), u(LIST_W), u(inner));
		boolean inside = over(x, clipY, LIST_W, inner);
		float rowX = x + 12f, rowW = LIST_W - 12f - 36f;
		for (int i = 0; i < filtered.size(); i++) {
			float ry = y + 14f + i * LIST_PITCH - scroll;
			Module m = filtered.get(i);
			float grow = grow(appearList, m, ry + LIST_ROW_H >= clipY && ry <= clipY + inner, i, listClock);
			if (grow <= 0f) continue;
			// A row coming into view grows out of its bottom right corner.
			r.pushScale(grow, px(rowX + rowW), py(ry + LIST_ROW_H));
			r.pushAlpha(grow);
			listRow(r, m, rowX, ry, rowW, inside && grow > 0.9f);
			r.popAlpha();
			r.popScale();
		}
		r.popClip();
		scrollbar(r, x + LIST_W - 22f, clipY + 4f, inner - 8f, content, inner, scroll);
	}

	private void listRow(Renderer r, Module m, float x, float y, float w, boolean inside) {
		boolean hovered = inside && over(x, y, w, LIST_ROW_H);
		float hv = anim(hover, m, 16f).update(hovered ? 1f : 0f, dt);
		float sel = anim(hover, m.settings, 16f).update(m == selected ? 1f : 0f, dt);
		float on = anim(switches, m, 14f).update(m.toggleable && m.isEnabled() ? 1f : 0f, dt);
		float red = anim(blockedAnim, m, 14f).update(m.isBlocked() ? 1f : 0f, dt);

		// An enabled module's row turns the dark teal seen in the original.
		int off = Theme.mix(Theme.mix(t.row, t.rowHover, hv), t.rowSelected, sel);
		int lit = Theme.mix(t.rowEnabled, t.rowEnabledHover, Math.max(hv, sel));
		// A module this server does not allow is shown in red and cannot be switched on.
		r.panel(px(x), py(y), u(w), u(LIST_ROW_H), u(ROW_R), Theme.mix(Theme.mix(off, lit, on), t.rowBlocked, red), 0f, 0, false);

		float size = u(16f);
		r.text(m.name, px(x + 14f), py(y) + (u(LIST_ROW_H) - r.lineHeight(size)) / 2f, size, t.text, Renderer.REGULAR);

		if (m.toggleable) {
			float tx = x + w - 14f - 24f, ty = y + (LIST_ROW_H - 12f) / 2f;
			drawSwitch(r, tx, ty, on);
			if (red > 0.01f) r.panel(px(tx), py(ty), u(24f), u(12f), u(6f), t.withAlpha(t.blocked, red), 0f, 0, false);
			if (hovered && click(tx - 8f, y, 24f + 22f, LIST_ROW_H, 0)) {
				m.toggle();
				return;
			}
		}
		if (hovered && click(x, y, w, LIST_ROW_H, 0)) select(m);
		// Right click is a shortcut for the switch, as in most clients.
		if (hovered && m.toggleable && click(x, y, w, LIST_ROW_H, 1)) m.toggle();
	}

	/** Opens the page of a module, as if its row had been clicked. */
	public void showModule(Module m) {
		select(m);
	}

	private void select(Module m) {
		if (m == selected) return;
		finishRename(true);
		selected = m;
		setTarget = 0f;
		setScroll.snap(0f);
		listening = null;
		if (onix.global.animations.on) pageFade.snap(0f);
		// The rows of the new page grow in one after another.
		appearPage.clear();
		pageClock = 0f;
	}

	private void drawSettings(Renderer r, float rise, boolean blur) {
		float x = SET_X, y = TOP + rise, h = BOTTOM - TOP;
		r.panel(px(x), py(y), u(SET_W), u(h), u(PANEL_R), t.panel, u1(BORDER), t.outline, blur);
		Module m = selected;
		if (m == null) return;

		float fade = Anim.easeOut(pageFade.update(1f, dt));
		r.pushClip(px(x), py(y + 4f), u(SET_W), u(h - 8f));
		r.pushAlpha(fade);
		float cx = x;
		int order = 0;

		float title = u(34f);
		r.text(m.name, px(cx + 20f), py(y + 14f), title, t.text, Renderer.MEDIUM);
		float desc = u(13f);
		r.text(m.isBlocked() ? "Not allowed on this server. It turns back on when you leave." : m.description,
				px(cx + 20f), py(y + 66f), desc, m.isBlocked() ? t.blocked : t.textDim, Renderer.REGULAR);

		if (m.toggleable) {
			float bw = 118f, bh = 34f, bx = x + SET_W - 28f - bw, by = y + 20f;
			boolean hv = over(bx, by, bw, bh);
			float on = anim(switches, m, 14f).value;
			int bg = Theme.mix(hv ? t.rowHover : t.row, t.withAlpha(t.accent, 0.55f), on);
			if (m.isBlocked()) bg = t.rowBlocked;
			r.panel(px(bx), py(by), u(bw), u(bh), u(ROW_R), bg, 0f, 0, false);
			String label = m.isBlocked() ? "Blocked" : m.isEnabled() ? "Enabled" : "Disabled";
			float ls = u(18f);
			r.text(label, px(bx) + (u(bw) - r.textWidth(label, ls, Renderer.REGULAR)) / 2f,
					py(by) + (u(bh) - r.lineHeight(ls)) / 2f, ls, t.text, Renderer.REGULAR);
			if (click(bx, by, bw, bh, 0)) m.toggle();
		}

		if (m == onix.profiles) {
			drawProfiles(r, x, y + 100f, h - 100f - 12f);
			r.popAlpha();
			r.popClip();
			return;
		}

		float listY = y + 100f, listH = h - 100f - 12f;
		r.pushClip(px(x), py(listY), u(SET_W), u(listH));
		boolean inside = over(x, listY, SET_W, listH);
		float scroll = setScroll.update(setTarget, dt);
		float rowX = cx + 13f, rowW = SET_W - 13f - 40f;
		float cy = listY + 7f - scroll;

		String section = null;
		float factor = 1f;
		for (int i = 0; i < m.settings.size(); i++) {
			Setting<?> st = m.settings.get(i);
			if (st.section != null && !st.section.equals(section)) {
				section = st.section;
				String key = m.name + '/' + section;
				boolean shut = collapsed.contains(key);
				Anim sa = sections.get(key);
				if (sa == null) sections.put(key, sa = new Anim(shut ? 0f : 1f, 16f));
				factor = Anim.smooth(sa.update(shut ? 0f : 1f, dt));
				float headGrow = grow(appearPage, sa, cy + SET_ROW_H > listY && cy < listY + listH, order++, pageClock);
				if (headGrow > 0f) {
					boolean hv = inside && headGrow > 0.9f && over(rowX, cy, rowW, SET_ROW_H);
					r.pushScale(headGrow, px(rowX + rowW), py(cy + SET_ROW_H));
					r.pushAlpha(headGrow);
					r.panel(px(rowX), py(cy), u(rowW), u(SET_ROW_H), u(ROW_R), hv ? t.rowHover : t.row, 0f, 0, false);
					float ss = u(19f);
					r.text(section, px(rowX + 12f), py(cy) + (u(SET_ROW_H) - r.lineHeight(ss)) / 2f, ss, t.text, Renderer.REGULAR);
					chevron(r, rowX + rowW - 26f, cy + SET_ROW_H / 2f, factor);
					r.popAlpha();
					r.popScale();
					if (hv && click(rowX, cy, rowW, SET_ROW_H, 0)) {
						if (shut) collapsed.remove(key);
						else collapsed.add(key);
					}
				}
				cy += SET_PITCH;
			} else if (st.section == null) {
				section = null;
				factor = 1f;
			}
			float rowPitch = SET_PITCH + (st.note != null ? 22f : 0f);
			float grow = grow(appearPage, st, factor > 0.02f && cy + rowPitch > listY && cy < listY + listH, order++, pageClock);
			if (grow > 0f) {
				r.pushAlpha(factor * grow);
				// Like module rows, a setting grows out of its bottom right corner.
				r.pushScale(grow, px(rowX + rowW), py(cy + SET_ROW_H));
				settingRow(r, st, rowX, cy, rowW, inside && factor > 0.9f && grow > 0.9f);
				r.popScale();
				r.popAlpha();
			}
			cy += rowPitch * factor;
		}
		r.popClip();

		float content = cy + scroll - listY;
		setMax = Math.max(0f, content - listH);
		setTarget = Math.max(0f, Math.min(setMax, setTarget));
		scrollbar(r, x + SET_W - 22f, listY + 4f, listH - 8f, content, listH, scroll);
		r.popAlpha();
		r.popClip();
	}

	/**
	 * The Profiles page: a grid of cards instead of rows. Each card shows the
	 * profile name over the client logo, with Rename, Copy and Delete under it;
	 * clicking the card itself switches to that profile.
	 */
	private void drawProfiles(Renderer r, float x, float top, float viewH) {
		List<String> names = onix.config.profiles();
		float left = x + 20f, width = SET_W - 60f;

		// Actions above the grid.
		if (smallButton(r, "New Profile", left, top, 150f, 34f, true)) {
			String name = onix.config.freeName("New Profile");
			if (onix.config.create(name)) startRename(name);
		}
		if (smallButton(r, "Open Folder", left + 162f, top, 150f, 34f, false)) {
			onix.config.save();
			onix.platform.openFolder(onix.config.profilesDir());
		}
		float hint = u(13f);
		String using = "In use: " + onix.config.active();
		r.text(using, px(left + width) - r.textWidth(using, hint, Renderer.REGULAR), py(top) + (u(34f) - r.lineHeight(hint)) / 2f, hint, t.textDim, Renderer.REGULAR);

		float gridTop = top + 52f, gridH = viewH - 52f;
		int columns = 4;
		float gap = 14f, cardW = (width - gap * (columns - 1)) / columns, cardH = 236f;
		int rows = (names.size() + columns - 1) / columns;
		float content = rows * (cardH + gap);
		setMax = Math.max(0f, content - gridH);
		setTarget = Math.max(0f, Math.min(setMax, setTarget));
		float scroll = setScroll.update(setTarget, dt);

		r.pushClip(px(x), py(gridTop), u(SET_W), u(gridH));
		boolean inside = over(x, gridTop, SET_W, gridH);
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			float cx = left + (i % columns) * (cardW + gap), cy = gridTop + (i / columns) * (cardH + gap) - scroll;
			if (cy + cardH < gridTop || cy > gridTop + gridH) continue;
			profileCard(r, name, cx, cy, cardW, cardH, inside, names.size() > 1);
		}
		r.popClip();
		scrollbar(r, x + SET_W - 22f, gridTop + 4f, gridH - 8f, content, gridH, scroll);
	}

	private void profileCard(Renderer r, String name, float x, float y, float w, float h, boolean inside, boolean deletable) {
		boolean active = name.equals(onix.config.active());
		boolean editing = name.equals(renaming);
		boolean hovered = inside && over(x, y, w, h);
		// The profile in use is the one with the bright frame.
		int frame = active ? 0xFF2D9CDB : (hovered ? 0x80FFFFFF : 0x40FFFFFF);
		r.panel(px(x), py(y), u(w), u(h), u(10f), hovered ? t.rowHover : t.row, u1(active ? 2f : 1f), frame, false);

		float size = u(17f);
		String shown = editing ? renameText.toString() : name;
		float tw = r.textWidth(shown, size, Renderer.REGULAR);
		// A long name is shrunk to fit the card.
		if (tw > u(w - 20f)) {
			size *= u(w - 20f) / tw;
			tw = r.textWidth(shown, size, Renderer.REGULAR);
		}
		float tx = px(x) + (u(w) - tw) / 2f, ty = py(y + 14f);
		r.text(shown, tx, ty, size, t.text, Renderer.REGULAR);
		if (editing) {
			// While a name is being typed: an accent line under it and a blinking caret after it.
			r.panel(px(x + 16f), ty + r.lineHeight(size) + u(2f), u(w - 32f), Math.max(1f, u(1.5f)), 0f, t.accent | 0xFF000000, 0f, 0, false);
			if ((System.nanoTime() / 500000000L) % 2 == 0) r.panel(tx + tw + u(2f), ty + u(2f), Math.max(1f, u(1.5f)), r.lineHeight(size) - u(4f), 0f, t.text, 0f, 0, false);
		}

		float box = 92f, bx = x + (w - box) / 2f, by = y + 62f;
		r.panel(px(bx), py(by), u(box), u(box), u(6f), 0x33000000, u1(1.5f), 0xFF2D9CDB, false);
		r.logo(px(bx + 10f), py(by + 10f), u(box - 20f));

		float third = (w - 24f - 12f) / 3f, buttonY = y + h - 44f;
		if (smallButton(r, "Rename", x + 12f, buttonY, third, 28f, false) && inside) startRename(name);
		if (smallButton(r, "Copy", x + 12f + third + 6f, buttonY, third, 28f, false) && inside) onix.config.duplicate(name);
		// The profile in use cannot be deleted, so there is always one left.
		if (deletable && !active && smallButton(r, "Delete", x + 12f + (third + 6f) * 2f, buttonY, third, 28f, false) && inside) {
			onix.config.delete(name);
			if (editing) renaming = null;
		}
		if (hovered && !editing && click(x, y, w, h, 0)) onix.config.use(name);
	}

	private void startRename(String name) {
		renaming = name;
		renameText.setLength(0);
		renameText.append(name);
	}

	/** Applies the typed name. A name that is empty, taken or not allowed is dropped and the old one stays. */
	private void finishRename(boolean keep) {
		if (renaming != null && keep) onix.config.rename(renaming, renameText.toString().trim());
		renaming = null;
	}

	/** A compact button in unit coordinates. Returns true on the frame it is clicked. */
	private boolean smallButton(Renderer r, String label, float x, float y, float w, float h, boolean filled) {
		boolean hv = over(x, y, w, h);
		int fill = filled ? (hv ? Theme.mix(t.accent, 0xFFFFFFFF, 0.12f) : t.accent) : (hv ? t.rowSelected : t.rowHover);
		r.panel(px(x), py(y), u(w), u(h), u(6f), fill, 0f, 0, false);
		float size = u(13f);
		float tw = r.textWidth(label, size, Renderer.REGULAR);
		r.text(label, px(x) + (u(w) - tw) / 2f, py(y) + (u(h) - r.lineHeight(size)) / 2f, size, t.text, Renderer.REGULAR);
		return click(x, y, w, h, 0);
	}

	private void settingRow(Renderer r, Setting<?> st, float x, float y, float w, boolean inside) {
		boolean hovered = inside && over(x, y, w, SET_ROW_H);
		// A row scrolled out of view, or mid-collapse, must not take the click.
		if (!hovered) {
			boolean pending = clickPending;
			clickPending = false;
			settingRowBody(r, st, x, y, w, false);
			clickPending = pending;
		} else {
			settingRowBody(r, st, x, y, w, true);
		}
	}

	private void settingRowBody(Renderer r, Setting<?> st, float x, float y, float w, boolean hovered) {
		float hv = anim(hover, st, 16f).update(hovered ? 1f : 0f, dt);
		r.panel(px(x), py(y), u(w), u(SET_ROW_H), u(ROW_R), Theme.mix(t.row, t.rowHover, hv), 0f, 0, false);

		float size = u(19f);
		float textY = py(y) + (u(SET_ROW_H) - r.lineHeight(size)) / 2f;
		float right = x + w - 14f;

		if (st instanceof Setting.Number) {
			Setting.Number n = (Setting.Number) st;
			float f = anim(switches, st, 20f).update(n.fraction(), dt);
			if (f > 0.002f) {
				// Clipping a full-width bar keeps the rounded left end at any value.
				r.pushClip(px(x), py(y), u(w * f), u(SET_ROW_H));
				r.panel(px(x), py(y), u(w), u(SET_ROW_H), u(ROW_R), t.accent, 0f, 0, false);
				r.popClip();
			}
			String value = n.display();
			r.text(value, px(right) - r.textWidth(value, size, Renderer.REGULAR), textY, size, t.text, Renderer.REGULAR);
			if (hovered && click(x, y, w, SET_ROW_H, 0)) {
				dragging = n;
				dragX = px(x);
				dragW = u(w);
			}
			if (hovered && click(x, y, w, SET_ROW_H, 1)) n.reset();
		} else if (st instanceof Setting.Bool) {
			Setting.Bool b = (Setting.Bool) st;
			float on = anim(switches, st, 14f).update(b.on ? 1f : 0f, dt);
			drawSwitch(r, right - 24f, y + (SET_ROW_H - 12f) / 2f, on);
			if (hovered && click(x, y, w, SET_ROW_H, 0)) b.toggle();
		} else if (st instanceof Setting.Mode) {
			Setting.Mode mode = (Setting.Mode) st;
			float bw = pill(r, mode.options[mode.index], right, y, true);
			if (click(right - bw, y, bw, SET_ROW_H, 0)) mode.cycle(1);
			else if (click(right - bw, y, bw, SET_ROW_H, 1)) mode.cycle(-1);
		} else if (st instanceof Setting.Color) {
			Setting.Color c = (Setting.Color) st;
			float sw = 38f, sh = 22f;
			r.panel(px(right - sw), py(y + (SET_ROW_H - sh) / 2f), u(sw), u(sh), u(5f), c.argb | 0xFF000000, u1(1f), t.outline, false);
			float bw = pill(r, "Change Color", right - sw - 12f, y, true);
			if (click(right - sw - 12f - bw, y, bw + 12f + sw, SET_ROW_H, 0)) c.set(nextColor(c.argb, 1));
			else if (click(right - sw - 12f - bw, y, bw + 12f + sw, SET_ROW_H, 1)) c.set(nextColor(c.argb, -1));
		} else if (st instanceof Setting.Key) {
			Setting.Key k = (Setting.Key) st;
			String name = listening == k ? "..." : (k.code == 0 ? "none" : onix.platform.keyName(k.code));
			float kw = pill(r, name, right, y, false);
			if (click(right - kw, y, kw, SET_ROW_H, 0)) listening = listening == k ? null : k;
			float uw = pill(r, "Unbind", right - kw - 10f, y, true);
			if (click(right - kw - 10f - uw, y, uw, SET_ROW_H, 0)) {
				k.set(0);
				listening = null;
			}
		} else if (st instanceof Setting.Action) {
			Setting.Action act = (Setting.Action) st;
			float bw = pill(r, act.label, right, y, true);
			if (click(right - bw, y, bw, SET_ROW_H, 0)) act.action.run();
		}

		r.text(st.name, px(x + 12f), textY, size, t.text, Renderer.REGULAR);
		if (st.note != null) {
			float ns = u(12f);
			r.text(st.note, px(x + 4f), py(y + SET_ROW_H + 5f), ns, t.textDim, Renderer.REGULAR);
		}
	}

	/** Small button whose right edge is at {@code right}. Returns its width in units. */
	private float pill(Renderer r, String label, float right, float rowY, boolean filled) {
		float size = u(13f);
		float tw = r.textWidth(label, size, Renderer.REGULAR) / s;
		float w = Math.max(filled ? 0f : 40f, tw + 26f), h = 22f;
		float x = right - w, y = rowY + (SET_ROW_H - h) / 2f;
		boolean hv = over(x, y, w, h);
		if (filled) {
			r.panel(px(x), py(y), u(w), u(h), u(6f), hv ? Theme.mix(t.accent, 0xFFFFFFFF, 0.12f) : t.accent, 0f, 0, false);
		} else {
			r.panel(px(x), py(y), u(w), u(h), u(6f), hv ? t.rowHover : 0, u1(1.5f), t.text, false);
		}
		r.text(label, px(x) + (u(w) - tw * s) / 2f, py(y) + (u(h) - r.lineHeight(size)) / 2f, size, t.text, Renderer.REGULAR);
		return w;
	}

	private void drawSwitch(Renderer r, float x, float y, float on) {
		float w = 24f, h = 12f, knob = 9f;
		r.panel(px(x), py(y), u(w), u(h), u(h / 2f), Theme.mix(t.disabled, t.accent, on), 0f, 0, false);
		float kx = x + 2f + (w - knob - 4f) * on;
		r.panel(px(kx), py(y + (h - knob) / 2f), u(knob), u(knob), u(knob / 2f), t.knob, 0f, 0, false);
	}

	/** Two short strokes; open points down, shut points left. Built from dots since the batch has no rotation. */
	private void chevron(Renderer r, float cx, float cy, float openness) {
		float dot = Math.max(1.5f, u(2.2f));
		int n = 5;
		for (int i = -n; i <= n; i++) {
			float along = i * 1.3f, across = (n - Math.abs(i)) * 1.3f - n * 0.65f;
			// Blend between "v" (open) and "<" (shut).
			float dx = along * openness + (-across) * (1f - openness);
			float dy = across * openness + along * (1f - openness);
			r.panel(px(cx + dx) - dot / 2f, py(cy + dy) - dot / 2f, dot, dot, dot / 2f, t.text, 0f, 0, false);
		}
	}

	private void scrollbar(Renderer r, float x, float y, float h, float content, float view, float scroll) {
		if (content <= view + 0.5f) return;
		float thumb = Math.max(40f, h * view / content);
		float ty = y + (h - thumb) * (scroll / (content - view));
		r.panel(px(x), py(ty), u(9f), u(thumb), u(4.5f), t.scrollbar, 0f, 0, false);
	}

	private int nextColor(int current, int by) {
		int index = -1;
		for (int i = 0; i < PALETTE.length; i++) {
			if ((PALETTE[i] & 0xFFFFFF) == (current & 0xFFFFFF)) index = i;
		}
		int n = PALETTE.length;
		int rgb = PALETTE[((index + by) % n + n) % n] & 0xFFFFFF;
		// Keep the alpha the setting already had.
		return (current & 0xFF000000) | rgb;
	}

	// -------------------------------------------------------------- helpers

	private static Anim anim(Map<Object, Anim> map, Object key, float speed) {
		Anim a = map.get(key);
		if (a == null) map.put(key, a = new Anim(0f, speed));
		return a;
	}

	/**
	 * How far a row has grown in, 0 to 1. A row that leaves the view is reset, so it
	 * plays again every time scrolling brings it back.
	 */
	private float grow(Map<Object, Anim> map, Object key, boolean visible, int order, float clock) {
		Anim a = anim(map, key, 7.5f);
		if (!visible) {
			a.snap(0f);
			return 0f;
		}
		// Right after the menu or a page opens, rows start one after another, top to bottom.
		if (onix.global.animations.on && a.value == 0f && clock < Math.min(order, 22) * 0.03f) return 0f;
		if (!onix.global.animations.on) {
			a.snap(1f);
			return 1f;
		}
		return Math.max(0.02f, Anim.smooth(a.update(1f, dt)));
	}

	private float px(float unitsX) {
		return (unitsX + ox) * s;
	}

	private float py(float unitsY) {
		return unitsY * s;
	}

	private float u(float units) {
		return units * s;
	}

	/** A line width: scaled, but never thinner than one pixel. */
	private float u1(float units) {
		return Math.max(1f, units * s);
	}

	private boolean over(float x, float y, float w, float h) {
		float ux = mx / s - ox, uy = my / s;
		return ux >= x && ux < x + w && uy >= y && uy < y + h;
	}

	/** True once for the widget the pending click landed on. */
	private boolean click(float x, float y, float w, float h, int button) {
		if (!clickPending || clickButton != button) return false;
		float ux = clickX / s - ox, uy = clickY / s;
		if (ux < x || ux >= x + w || uy < y || uy >= y + h) return false;
		clickPending = false;
		return true;
	}
}
