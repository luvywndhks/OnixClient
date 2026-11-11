package onix.launcher;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * The whole launcher window, painted by hand.
 *
 * Everything is on one screen: the logo and Play in the middle of the
 * wallpaper, the account at the top right, a gear at the top left, and the
 * links along the bottom. Settings and sign-in open as cards laid over the
 * screen rather than as separate pages.
 *
 * It is immediate mode: each paint draws the screen and records which
 * rectangles can be clicked, and a click runs the action of the topmost one
 * under it. Animations are values that glide towards a target every frame.
 */
public final class LauncherView extends JPanel {
	private static final long serialVersionUID = 1L;

	public static final int WIDTH = 1280, HEIGHT = 760;
	private static final int BAR = 64;
	public static final int NONE = 0, SETTINGS = 1, ACCOUNT = 2;

	private static final Color WHITE = new Color(0xFFFFFF), INK = new Color(0xF2F6FA), SOFT_INK = new Color(0x9FB0C3);
	private static final Color BLUE = new Color(0x1A9AD7), CYAN = new Color(0x01D9BB), DEEP = new Color(0x6CC5F5);
	private static final Color CARD = new Color(13, 21, 35, 242), FIELD = new Color(0x1C2A3E);
	private static final int[] MEMORY_STEPS = { 1024, 2048, 3072, 4096, 6144, 8192, 12288, 16384 };
	private static final int[][] SIZES = { { 854, 480 }, { 1280, 720 }, { 1600, 900 }, { 1920, 1080 }, { 2560, 1440 } };

	/** What the window frame must do for the view. */
	public interface Host {
		void close();

		void minimize();

		void dragBy(int dx, int dy);

		void openLink(String url);

		void copy(String text);
	}

	private final Settings settings;
	private final Host host;
	private final Font regular, medium, bold;
	private BufferedImage logo, scenery, head;
	private String headFor = "";

	private int overlay = NONE, overlayShown = NONE;
	private float overlayFade, playPress, progress, progressShown, intro;
	private final Map<String, Float> glides = new HashMap<String, Float>();
	private final List<Rectangle> hitAreas = new ArrayList<Rectangle>();
	private final List<Runnable> hitActions = new ArrayList<Runnable>();
	private int mouseX = -1, mouseY = -1, pressX, pressY;
	private boolean dragWindow, dragMemory;
	private Rectangle memoryTrack = new Rectangle();
	private long lastFrame = System.nanoTime();

	private String status = "Ready to play";
	private boolean busy;
	private MicrosoftAuth.Prompt prompt;
	private String accountMessage = "";

	public LauncherView(Settings settings, Host host) {
		this.settings = settings;
		this.host = host;
		regular = font("Poppins-Regular.ttf", Font.PLAIN);
		medium = font("Poppins-Medium.ttf", Font.PLAIN);
		bold = font("Poppins-SemiBold.ttf", Font.BOLD);
		try {
			InputStream in = LauncherView.class.getResourceAsStream("/onix/logo.png");
			if (in != null) {
				logo = ImageIO.read(in);
				in.close();
			}
		} catch (Exception e) {
			System.err.println("Could not load the logo: " + e);
		}
		setOpaque(true);
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				mouseX = -1;
				mouseY = -1;
			}

			@Override
			public void mousePressed(MouseEvent e) {
				pressX = e.getXOnScreen();
				pressY = e.getYOnScreen();
				if (overlay == SETTINGS && memoryTrack.contains(e.getPoint())) {
					dragMemory = true;
					setMemoryFrom(e.getX());
					return;
				}
				// Later areas are drawn on top, so they are asked first.
				for (int i = hitAreas.size() - 1; i >= 0; i--) {
					if (hitAreas.get(i).contains(e.getPoint())) {
						hitActions.get(i).run();
						return;
					}
				}
				dragWindow = overlay == NONE && e.getY() < BAR;
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				mouseX = e.getX();
				mouseY = e.getY();
				if (dragMemory) {
					setMemoryFrom(e.getX());
				} else if (dragWindow) {
					host.dragBy(e.getXOnScreen() - pressX, e.getYOnScreen() - pressY);
					pressX = e.getXOnScreen();
					pressY = e.getYOnScreen();
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (dragMemory) settings.save();
				dragMemory = false;
				dragWindow = false;
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	private static Font font(String file, int fallbackStyle) {
		try {
			InputStream in = LauncherView.class.getResourceAsStream("/onix/fonts/" + file);
			if (in != null) {
				try {
					return Font.createFont(Font.TRUETYPE_FONT, in);
				} finally {
					in.close();
				}
			}
		} catch (Exception e) {
			System.err.println("Could not load " + file + ": " + e);
		}
		return new Font(Font.SANS_SERIF, fallbackStyle, 1);
	}

	/** Shows the screen with the given card open and every animation finished. Used for previews. */
	public void showOverlay(int which) {
		overlay = which;
		overlayShown = which;
		overlayFade = which == NONE ? 0f : 1f;
		intro = 1f;
		progressShown = progress;
	}

	// ------------------------------------------------------------- painting

	@Override
	protected void paintComponent(Graphics graphics) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		intro = glide(intro, 1f, 4f, dt);
		playPress = glide(playPress, 0f, 10f, dt);
		progressShown = glide(progressShown, progress, 6f, dt);
		overlayFade = glide(overlayFade, overlay == NONE ? 0f : 1f, 11f, dt);
		if (overlay != NONE) overlayShown = overlay;
		loadHeadIfNeeded();

		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		hitAreas.clear();
		hitActions.clear();

		if (scenery == null) scenery = paintScenery();
		g.drawImage(scenery, 0, 0, WIDTH, HEIGHT, null);
		// A light veil at the top and bottom keeps the bar and the links readable.
		g.setPaint(new GradientPaint(0, 0, new Color(6, 14, 30, 150), 0, 150, new Color(6, 14, 30, 0)));
		g.fillRect(0, 0, WIDTH, 150);
		g.setPaint(new GradientPaint(0, HEIGHT - 170, new Color(6, 14, 30, 0), 0, HEIGHT, new Color(6, 14, 30, 190)));
		g.fillRect(0, HEIGHT - 170, WIDTH, 170);

		boolean front = overlay == NONE;
		topBar(g, dt, front);
		centre(g, dt, front);
		footer(g, dt, front);

		if (overlayFade > 0.004f) {
			float a = smooth(overlayFade);
			g.setColor(new Color(8, 24, 40, Math.round(120 * a)));
			g.fillRect(0, 0, WIDTH, HEIGHT);
			// Anywhere outside the card closes it.
			if (overlay != NONE) area(new Rectangle(0, 0, WIDTH, HEIGHT), new Runnable() {
				@Override
				public void run() {
					closeOverlay();
				}
			});
			// The card fades in while growing slightly from its centre.
			Composite before = g.getComposite();
			AffineTransform at = g.getTransform();
			g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.001f, a)));
			float scale = 0.94f + 0.06f * a;
			g.translate(WIDTH / 2.0, HEIGHT / 2.0);
			g.scale(scale, scale);
			g.translate(-WIDTH / 2.0, -HEIGHT / 2.0);
			if (overlayShown == SETTINGS) settingsCard(g, dt);
			else if (overlayShown == ACCOUNT) accountCard(g, dt);
			g.setTransform(at);
			g.setComposite(before);
		}
		g.dispose();
	}

	private void topBar(Graphics2D g, float dt, boolean active) {
		// Gear, top left: opens the settings card. It turns a little under the cursor.
		Rectangle gear = new Rectangle(22, 14, 40, 40);
		float gh = hover("gear", gear, dt, active ? new Runnable() {
			@Override
			public void run() {
				overlay = SETTINGS;
			}
		} : null);
		glass(g, gear.x, gear.y, gear.width, gear.height, 40, gh);
		gearIcon(g, gear.x + 20, gear.y + 20, 9.5f, gh * 0.6f);

		// Window buttons, top right.
		String[] glyphs = { "–", "×" };
		for (int i = 0; i < 2; i++) {
			final boolean close = i == 1;
			int x = WIDTH - 100 + i * 42;
			float h = hover("win" + i, new Rectangle(x, 16, 36, 36), dt, active ? new Runnable() {
				@Override
				public void run() {
					if (close) host.close();
					else host.minimize();
				}
			} : null);
			if (close && h > 0.01f) {
				g.setColor(new Color(225, 70, 80, Math.round(230 * h)));
				g.fill(round(x, 16, 36, 36, 36));
			} else {
				glass(g, x, 16, 36, 36, 36, h);
			}
			centered(g, glyphs[i], x, 16, 36, 36, medium, 18f, WHITE);
		}

		// Account, left of the window buttons: the player's head and name, or "Account".
		boolean in = settings.signedIn();
		String label = in ? settings.playerName : "Account";
		int tw = g.getFontMetrics(medium.deriveFont(14f)).stringWidth(label);
		int w = tw + 66, x = WIDTH - 116 - w;
		float ah = hover("account", new Rectangle(x, 14, w, 40), dt, active ? new Runnable() {
			@Override
			public void run() {
				overlay = ACCOUNT;
			}
		} : null);
		glass(g, x, 14, w, 40, 40, ah);
		head(g, x + 7, 20, 28);
		text(g, label, x + 44, 23, medium, 14f, WHITE);
	}

	private void centre(Graphics2D g, float dt, boolean active) {
		// The logo and name rise into place when the launcher opens.
		float in = smooth(intro);
		Composite before = g.getComposite();
		g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.001f, in)));
		int lift = Math.round((1f - in) * 26f);
		int cx = WIDTH / 2;
		if (logo != null) g.drawImage(logo, cx - 66, 148 + lift, 132, 132, null);
		shadowText(g, "ONIX CLIENT", cx, 292 + lift, bold, 58f);
		Font small = regular.deriveFont(15f);
		g.setFont(small);
		String line = "Java Edition  ·  Minecraft 1.8.9";
		g.setColor(new Color(255, 255, 255, 235));
		g.drawString(line, cx - g.getFontMetrics(small).stringWidth(line) / 2, 384 + lift);
		g.setComposite(before);

		// Play: glows under the cursor and dips when pressed.
		int bw = 300, bh = 72, bx = cx - bw / 2, by = 428;
		float hv = hover("play", new Rectangle(bx, by, bw, bh), dt, active ? new Runnable() {
			@Override
			public void run() {
				playPress = 1f;
				play();
			}
		} : null);
		float press = 1f - 0.045f * playPress;
		int pw = Math.round(bw * press), ph = Math.round(bh * press), px = bx + (bw - pw) / 2, py = by + (bh - ph) / 2;
		for (int i = 7; i >= 1; i--) {
			g.setColor(new Color(255, 255, 255, Math.round((5 + 10 * hv) * (8 - i) / 7f)));
			g.fill(round(px - i * 2, py - i * 2, pw + i * 4, ph + i * 4, bh + i * 4));
		}
		g.setPaint(new GradientPaint(px, py, mix(BLUE, new Color(0x3DBBF2), hv), px + pw, py + ph, mix(CYAN, new Color(0x3AF2D6), hv)));
		g.fill(round(px, py, pw, ph, bh));
		g.setColor(new Color(255, 255, 255, 70));
		g.setStroke(new BasicStroke(1.5f));
		g.draw(round(px + 1, py + 1, pw - 2, ph - 2, bh));
		centered(g, busy ? "WORKING..." : "PLAY", px, py, pw, ph, bold, 24f, WHITE);

		Font st = medium.deriveFont(13.5f);
		g.setFont(st);
		g.setColor(WHITE);
		g.drawString(status, cx - g.getFontMetrics(st).stringWidth(status) / 2, by + bh + 38);
		int barW = 300;
		g.setColor(new Color(255, 255, 255, 70));
		g.fill(round(cx - barW / 2, by + bh + 52, barW, 5, 5));
		if (progressShown > 0.004f) {
			g.setColor(WHITE);
			g.fill(round(cx - barW / 2, by + bh + 52, barW * Math.min(1f, progressShown), 5, 5));
		}
	}

	private void footer(Graphics2D g, float dt, boolean active) {
		// Small and out of the way: the two links, then who made it.
		String[] labels = { "Discord", "Website" };
		final String[] urls = { "https://onixclient.com/discord", "https://onixclient.com" };
		Font f = medium.deriveFont(13f);
		FontMetrics m = g.getFontMetrics(f);
		String made = "Made by Onix";
		int gap = 30, total = m.stringWidth(labels[0]) + m.stringWidth(labels[1]) + m.stringWidth(made) + gap * 2;
		int x = (WIDTH - total) / 2, y = HEIGHT - 34;
		for (int i = 0; i < labels.length; i++) {
			final int index = i;
			int w = m.stringWidth(labels[i]);
			float h = hover("link" + i, new Rectangle(x - 8, y - 20, w + 16, 30), dt, active ? new Runnable() {
				@Override
				public void run() {
					host.openLink(urls[index]);
				}
			} : null);
			g.setFont(f);
			g.setColor(mix(new Color(255, 255, 255, 215), WHITE, h));
			g.drawString(labels[i], x, y);
			// An underline grows from the left under the cursor.
			if (h > 0.01f) g.fill(round(x, y + 5, w * h, 2, 2));
			x += w + gap;
			g.setColor(new Color(255, 255, 255, 110));
			g.fillOval(x - gap / 2 - 2, y - 6, 4, 4);
		}
		g.setFont(f);
		g.setColor(new Color(255, 255, 255, 215));
		g.drawString(made, x, y);

		Font v = regular.deriveFont(11.5f);
		g.setFont(v);
		g.setColor(new Color(255, 255, 255, 170));
		String version = "Launcher " + version();
		g.drawString(version, WIDTH - 22 - g.getFontMetrics(v).stringWidth(version), y);
	}

	private void settingsCard(Graphics2D g, float dt) {
		int w = 620, h = 500, x = (WIDTH - w) / 2, y = (HEIGHT - h) / 2;
		card(g, x, y, w, h);
		text(g, "Settings", x + 32, y + 26, bold, 24f, INK);
		closeButton(g, x + w - 56, y + 24, dt);

		int ix = x + 32, iw = w - 64, cy = y + 84;
		text(g, "Memory", ix, cy, medium, 15f, INK);
		String amount = settings.memoryMb % 1024 == 0 ? settings.memoryMb / 1024 + " GB" : settings.memoryMb / 1024f + " GB";
		rightText(g, amount, ix + iw, cy - 2, bold, 17f, DEEP);
		text(g, "How much RAM Minecraft may use. 2 GB is plenty for 1.8.9.", ix, cy + 24, regular, 12f, SOFT_INK);
		int ty = cy + 62;
		memoryTrack = new Rectangle(ix - 8, ty - 12, iw + 16, 30);
		float f = memoryFraction();
		g.setColor(FIELD);
		g.fill(round(ix, ty, iw, 8, 8));
		g.setPaint(new GradientPaint(ix, 0, BLUE, ix + iw, 0, CYAN));
		g.fill(round(ix, ty, iw * f, 8, 8));
		float knob = hover("knob", new Rectangle(memoryTrack), dt, null);
		int kr = Math.round(9 + 3 * Math.max(knob, dragMemory ? 1f : 0f));
		g.setColor(new Color(26, 154, 215, 60));
		g.fillOval(Math.round(ix + iw * f) - kr - 4, ty + 4 - kr - 4, (kr + 4) * 2, (kr + 4) * 2);
		g.setColor(WHITE);
		g.fillOval(Math.round(ix + iw * f) - kr, ty + 4 - kr, kr * 2, kr * 2);
		g.setColor(BLUE);
		g.setStroke(new BasicStroke(2f));
		g.drawOval(Math.round(ix + iw * f) - kr, ty + 4 - kr, kr * 2, kr * 2);

		cy += 112;
		line(g, ix, cy - 14, iw);
		text(g, "Resolution", ix, cy, medium, 15f, INK);
		text(g, "The size of the game window.", ix, cy + 23, regular, 12f, SOFT_INK);
		String size = settings.width + " × " + settings.height;
		int bw = g.getFontMetrics(medium.deriveFont(13f)).stringWidth(size) + 34;
		button(g, ix + iw - bw, cy + 3, bw, 34, size, false, "size", dt, new Runnable() {
			@Override
			public void run() {
				int next = 0;
				for (int i = 0; i < SIZES.length; i++) {
					if (SIZES[i][0] == settings.width && SIZES[i][1] == settings.height) next = (i + 1) % SIZES.length;
				}
				settings.width = SIZES[next][0];
				settings.height = SIZES[next][1];
				settings.save();
			}
		});

		cy += 74;
		line(g, ix, cy - 14, iw);
		toggle(g, ix, cy, iw, "Fullscreen", "Start the game in fullscreen.", settings.fullscreen, "full", dt, new Runnable() {
			@Override
			public void run() {
				settings.fullscreen = !settings.fullscreen;
				settings.save();
			}
		});
		cy += 74;
		line(g, ix, cy - 14, iw);
		toggle(g, ix, cy, iw, "Close launcher when the game starts", "The launcher quits once Minecraft is running.", settings.closeOnLaunch, "close", dt, new Runnable() {
			@Override
			public void run() {
				settings.closeOnLaunch = !settings.closeOnLaunch;
				settings.save();
			}
		});
		cy += 74;
		line(g, ix, cy - 14, iw);
		text(g, "Java", ix, cy, medium, 15f, INK);
		String java = settings.javaPath.isEmpty() ? System.getProperty("java.home") : settings.javaPath;
		text(g, java.length() > 62 ? "..." + java.substring(java.length() - 59) : java, ix, cy + 23, regular, 12f, SOFT_INK);
		rightText(g, "Automatic", ix + iw, cy + 10, regular, 12.5f, SOFT_INK);
	}

	private void accountCard(Graphics2D g, float dt) {
		int w = 540, h = 330, x = (WIDTH - w) / 2, y = (HEIGHT - h) / 2;
		card(g, x, y, w, h);
		text(g, "Account", x + 32, y + 26, bold, 24f, INK);
		closeButton(g, x + w - 56, y + 24, dt);
		int ix = x + 32, iw = w - 64, cy = y + 88;

		if (settings.signedIn()) {
			head(g, ix, cy, 64);
			text(g, settings.playerName, ix + 82, cy + 4, bold, 22f, INK);
			text(g, "Microsoft account", ix + 82, cy + 36, regular, 12.5f, SOFT_INK);
			text(g, "You stay signed in on this computer.", ix, cy + 92, regular, 12.5f, SOFT_INK);
			button(g, ix, cy + 130, 150, 40, "Sign out", false, "signout", dt, new Runnable() {
				@Override
				public void run() {
					settings.playerName = "";
					settings.playerUuid = "";
					settings.refreshToken = "";
					settings.save();
					accountMessage = "";
					head = null;
					headFor = "";
				}
			});
		} else if (settings.azureClientId.isEmpty()) {
			text(g, "Microsoft sign-in is not set up yet", ix, cy, medium, 16f, INK);
			wrapped(g, "A launcher needs its own Azure app registration, approved by Mojang for the Minecraft API. Put its Application (client) ID in the \"azureClientId\" field of this file, then restart:", ix, cy + 32, iw, regular, 12.5f, SOFT_INK);
			text(g, new java.io.File(Settings.dir(), "launcher.json").getPath(), ix, cy + 118, medium, 12.5f, DEEP);
		} else if (prompt != null) {
			text(g, "Enter this code on the Microsoft page", ix, cy, medium, 16f, INK);
			text(g, prompt.userCode, ix, cy + 30, bold, 34f, DEEP);
			button(g, ix, cy + 96, 200, 40, "Open sign-in page", true, "openlogin", dt, new Runnable() {
				@Override
				public void run() {
					host.openLink(prompt.verificationUri);
				}
			});
			button(g, ix + 212, cy + 96, 130, 40, "Copy code", false, "copycode", dt, new Runnable() {
				@Override
				public void run() {
					host.copy(prompt.userCode);
					accountMessage = "Code copied.";
				}
			});
		} else {
			text(g, "Sign in to play", ix, cy, medium, 16f, INK);
			wrapped(g, "You will get a short code to enter on Microsoft's own page. Your password never passes through the launcher.", ix, cy + 32, iw, regular, 12.5f, SOFT_INK);
			button(g, ix, cy + 96, 250, 44, busy ? "Contacting Microsoft..." : "Sign in with Microsoft", true, "signin", dt, new Runnable() {
				@Override
				public void run() {
					signIn();
				}
			});
		}
		if (!accountMessage.isEmpty()) wrapped(g, accountMessage, ix, y + h - 62, iw, regular, 12.5f, SOFT_INK);
	}

	// -------------------------------------------------------------- widgets

	/** Frosted white glass for the controls that sit on the landscape. */
	private void glass(Graphics2D g, int x, int y, int w, int h, int radius, float hover) {
		g.setColor(new Color(255, 255, 255, Math.round(52 + 50 * hover)));
		g.fill(round(x, y, w, h, radius));
		g.setColor(new Color(255, 255, 255, Math.round(120 + 90 * hover)));
		g.setStroke(new BasicStroke(1f));
		g.draw(round(x, y, w - 1, h - 1, radius));
	}

	private void card(Graphics2D g, int x, int y, int w, int h) {
		// The card swallows clicks so they do not reach the "close" area behind it.
		area(new Rectangle(x, y, w, h), new Runnable() {
			@Override
			public void run() {
			}
		});
		for (int i = 8; i >= 1; i--) {
			g.setColor(new Color(10, 40, 70, 7));
			g.fill(round(x - i * 2, y - i * 2 + 6, w + i * 4, h + i * 4, 26 + i * 2));
		}
		g.setColor(CARD);
		g.fill(round(x, y, w, h, 26));
	}

	private void closeButton(Graphics2D g, int x, int y, float dt) {
		float h = hover("closecard", new Rectangle(x, y, 32, 32), dt, new Runnable() {
			@Override
			public void run() {
				closeOverlay();
			}
		});
		g.setColor(mix(FIELD, new Color(0x2B405C), h));
		g.fill(round(x, y, 32, 32, 32));
		centered(g, "×", x, y, 32, 32, medium, 18f, INK);
	}

	private void line(Graphics2D g, int x, int y, int w) {
		g.setColor(new Color(0x223247));
		g.fillRect(x, y, w, 1);
	}

	private void toggle(Graphics2D g, int x, int y, int w, String title, String detail, boolean on, String id, float dt, Runnable action) {
		text(g, title, x, y, medium, 15f, INK);
		text(g, detail, x, y + 23, regular, 12f, SOFT_INK);
		hover(id, new Rectangle(x, y - 6, w, 52), dt, action);
		// The knob slides and the track changes colour together.
		Float value = glides.get(id + ".on");
		float p = glide(value == null ? (on ? 1f : 0f) : value, on ? 1f : 0f, 14f, dt);
		glides.put(id + ".on", p);
		int tx = x + w - 48, ty = y + 8;
		g.setColor(mix(new Color(0x34465C), BLUE, p));
		g.fill(round(tx, ty, 48, 26, 26));
		g.setColor(WHITE);
		g.fillOval(Math.round(tx + 3 + 22 * p), ty + 3, 20, 20);
	}

	private void button(Graphics2D g, int x, int y, int w, int h, String label, boolean primary, String id, float dt, Runnable action) {
		float hv = hover(id, new Rectangle(x, y, w, h), dt, action);
		if (primary) g.setPaint(new GradientPaint(x, y, mix(BLUE, new Color(0x3DBBF2), hv), x + w, y + h, mix(CYAN, new Color(0x3AF2D6), hv)));
		else g.setColor(mix(FIELD, new Color(0x2B405C), hv));
		g.fill(round(x, y, w, h, h));
		centered(g, label, x, y, w, h, medium, 13f, primary ? WHITE : INK);
	}

	/** The player's face, or a plain placeholder until one is known. */
	private void head(Graphics2D g, int x, int y, int size) {
		Shape clip = g.getClip();
		g.clip(round(x, y, size, size, size * 0.28f));
		if (head != null) {
			Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
			// Skins are pixel art; smoothing would smear them.
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(head, x, y, size, size, null);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
		} else {
			g.setColor(new Color(0xB98E6B));
			g.fillRect(x, y, size, size);
			g.setColor(new Color(0x5A3A22));
			g.fillRect(x, y, size, size * 3 / 8);
			g.setColor(WHITE);
			g.fillRect(x + size / 8, y + size / 2, size / 4, size / 8);
			g.fillRect(x + size * 5 / 8, y + size / 2, size / 4, size / 8);
		}
		g.setClip(clip);
	}

	/** A cog: a ring with eight teeth, drawn from shapes because the font has no such glyph. */
	private void gearIcon(Graphics2D g, float cx, float cy, float radius, float turn) {
		Area cog = new Area(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
		for (int i = 0; i < 8; i++) {
			AffineTransform t = AffineTransform.getRotateInstance(Math.PI / 4 * i + turn, cx, cy);
			cog.add(new Area(t.createTransformedShape(new RoundRectangle2D.Float(cx - radius * 0.23f, cy - radius * 1.36f, radius * 0.46f, radius * 0.6f, 2, 2))));
		}
		cog.subtract(new Area(new Ellipse2D.Float(cx - radius * 0.42f, cy - radius * 0.42f, radius * 0.84f, radius * 0.84f)));
		g.setColor(WHITE);
		g.fill(cog);
	}

	/** Registers a clickable area and returns how far its hover animation has come, 0 to 1. */
	private float hover(String id, Rectangle area, float dt, Runnable action) {
		// The memory knob has no click action of its own but still reacts to the cursor.
		boolean over = (action != null || id.equals("knob")) && area.contains(mouseX, mouseY);
		Float value = glides.get(id);
		float next = glide(value == null ? 0f : value, over ? 1f : 0f, 14f, dt);
		glides.put(id, next);
		if (action != null) area(area, action);
		return next;
	}

	private void area(Rectangle area, Runnable action) {
		hitAreas.add(area);
		hitActions.add(action);
	}

	/**
	 * The wallpaper that ships with the launcher, scaled to cover the window.
	 * It is drawn small and stretched back up, which softens it the way a blur
	 * would, and done once since it never changes.
	 */
	private static BufferedImage paintScenery() {
		int w = WIDTH / 3, h = HEIGHT / 3 + 1;
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = small.createGraphics();
		g.setColor(new Color(0x0A1220));
		g.fillRect(0, 0, w, h);
		try {
			InputStream in = LauncherView.class.getResourceAsStream("/onix/background.jpg");
			if (in != null) {
				try {
					BufferedImage picture = ImageIO.read(in);
					if (picture != null) {
						g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						// Cover: fill the window and let the overflow fall outside it.
						double scale = Math.max(w / (double) picture.getWidth(), h / (double) picture.getHeight());
						int pw = (int) Math.ceil(picture.getWidth() * scale), ph = (int) Math.ceil(picture.getHeight() * scale);
						g.drawImage(picture, (w - pw) / 2, (h - ph) / 2, pw, ph, null);
					}
				} finally {
					in.close();
				}
			}
		} catch (Exception e) {
			System.err.println("Could not load the wallpaper: " + e);
		}
		g.dispose();
		return small;
	}

	// ---------------------------------------------------------------- logic

	private void closeOverlay() {
		overlay = NONE;
		dragMemory = false;
	}

	private float memoryFraction() {
		int index = 0;
		for (int i = 0; i < MEMORY_STEPS.length; i++) {
			if (settings.memoryMb >= MEMORY_STEPS[i]) index = i;
		}
		return index / (float) (MEMORY_STEPS.length - 1);
	}

	private void setMemoryFrom(int mouse) {
		float f = (mouse - (memoryTrack.x + 8)) / (float) (memoryTrack.width - 16);
		int index = Math.round(Math.max(0f, Math.min(1f, f)) * (MEMORY_STEPS.length - 1));
		settings.memoryMb = MEMORY_STEPS[index];
	}

	private void play() {
		if (busy) return;
		if (!settings.signedIn()) {
			status = "Sign in first";
			overlay = ACCOUNT;
			return;
		}
		// Installing and starting the game is the next part of the launcher to be built.
		status = "This build cannot install or start the game yet";
	}

	private void signIn() {
		if (busy) return;
		busy = true;
		accountMessage = "";
		final MicrosoftAuth auth = new MicrosoftAuth(settings.azureClientId);
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					prompt = auth.begin();
					host.copy(prompt.userCode);
					accountMessage = "The code is on your clipboard. Waiting for you to finish on Microsoft's page...";
					MicrosoftAuth.Session session = auth.finish(prompt);
					settings.playerName = session.playerName;
					settings.playerUuid = session.playerUuid;
					settings.refreshToken = session.refreshToken;
					settings.save();
					accountMessage = "Signed in as " + session.playerName + ".";
					status = "Ready to play";
				} catch (MicrosoftAuth.AuthException e) {
					accountMessage = e.getMessage();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					accountMessage = "Could not reach Microsoft: " + e.getMessage();
				} finally {
					prompt = null;
					busy = false;
				}
			}
		}, "Microsoft sign-in");
		worker.setDaemon(true);
		worker.start();
	}

	/** Fetches the signed-in player's face in the background, once per account. */
	private void loadHeadIfNeeded() {
		final String uuid = settings.playerUuid;
		if (!settings.signedIn() || uuid.isEmpty() || uuid.equals(headFor)) return;
		headFor = uuid;
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				BufferedImage face = SkinHead.fetch(uuid);
				if (face != null && uuid.equals(headFor)) head = face;
			}
		}, "Skin head");
		worker.setDaemon(true);
		worker.start();
	}

	private static String version() {
		String v = LauncherView.class.getPackage().getImplementationVersion();
		return v == null ? "dev" : v;
	}

	// -------------------------------------------------------------- helpers

	private static float glide(float value, float target, float speed, float dt) {
		float diff = target - value;
		if (Math.abs(diff) < 0.0005f) return target;
		return value + diff * (1f - (float) Math.exp(-speed * dt));
	}

	private static float smooth(float t) {
		t = Math.max(0f, Math.min(1f, t));
		return t * t * (3f - 2f * t);
	}

	private static Color mix(Color a, Color b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
	}

	private static Shape round(float x, float y, float w, float h, float radius) {
		return new RoundRectangle2D.Float(x, y, w, h, radius, radius);
	}

	/** y is the top of the text, not the baseline. */
	private static void text(Graphics2D g, String s, int x, int y, Font font, float size, Color color) {
		Font f = font.deriveFont(size);
		g.setFont(f);
		g.setColor(color);
		g.drawString(s, x, y + g.getFontMetrics(f).getAscent());
	}

	private static void rightText(Graphics2D g, String s, int right, int y, Font font, float size, Color color) {
		Font f = font.deriveFont(size);
		text(g, s, right - g.getFontMetrics(f).stringWidth(s), y, font, size, color);
	}

	private static void centered(Graphics2D g, String s, int x, int y, int w, int h, Font font, float size, Color color) {
		Font f = font.deriveFont(size);
		FontMetrics m = g.getFontMetrics(f);
		g.setFont(f);
		g.setColor(color);
		g.drawString(s, x + (w - m.stringWidth(s)) / 2, y + (h - m.getAscent() - m.getDescent()) / 2 + m.getAscent());
	}

	/** The wordmark: large light blue text, centred on cx, with a soft shadow under it. */
	private static void shadowText(Graphics2D g, String s, int cx, int y, Font font, float size) {
		Font f = font.deriveFont(size);
		FontMetrics m = g.getFontMetrics(f);
		int x = cx - m.stringWidth(s) / 2, baseline = y + m.getAscent();
		g.setFont(f);
		for (int i = 4; i >= 1; i--) {
			g.setColor(new Color(4, 12, 28, 50));
			g.drawString(s, x, baseline + i);
		}
		g.setColor(DEEP);
		g.drawString(s, x, baseline);
	}

	/** Draws text broken into lines no wider than the given width. */
	private static void wrapped(Graphics2D g, String s, int x, int y, int width, Font font, float size, Color color) {
		Font f = font.deriveFont(size);
		FontMetrics m = g.getFontMetrics(f);
		g.setFont(f);
		g.setColor(color);
		StringBuilder line = new StringBuilder();
		int baseline = y + m.getAscent();
		for (String word : s.split(" ")) {
			String trial = line.length() == 0 ? word : line + " " + word;
			if (m.stringWidth(trial) > width && line.length() > 0) {
				g.drawString(line.toString(), x, baseline);
				baseline += m.getHeight() + 2;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(trial);
			}
		}
		if (line.length() > 0) g.drawString(line.toString(), x, baseline);
	}
}
