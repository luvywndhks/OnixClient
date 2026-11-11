package onix.launcher;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Starts the launcher window.
 *
 * With {@code --preview <folder>} it opens no window: it paints the screen, and each card over it, to
 * a PNG in that folder and exits, which is how a build is checked.
 */
public final class Main {
	private Main() {
	}

	public static void main(final String[] args) throws Exception {
		if (args.length == 2 && args[0].equals("--preview")) {
			preview(new File(args[1]));
			return;
		}
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				open();
			}
		});
	}

	private static void open() {
		final JFrame frame = new JFrame("Onix Client");
		frame.setUndecorated(true);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(LauncherView.WIDTH, LauncherView.HEIGHT);
		frame.setShape(new RoundRectangle2D.Float(0, 0, LauncherView.WIDTH, LauncherView.HEIGHT, 22, 22));
		frame.setLocationRelativeTo(null);

		final Settings settings = Settings.load();
		final LauncherView view = new LauncherView(settings, new LauncherView.Host() {
			@Override
			public void close() {
				settings.save();
				frame.dispose();
				System.exit(0);
			}

			@Override
			public void minimize() {
				frame.setState(JFrame.ICONIFIED);
			}

			@Override
			public void dragBy(int dx, int dy) {
				frame.setLocation(frame.getX() + dx, frame.getY() + dy);
			}

			@Override
			public void openLink(String url) {
				try {
					Desktop.getDesktop().browse(new URI(url));
				} catch (Exception e) {
					System.err.println("Could not open " + url + ": " + e);
				}
			}

			@Override
			public void copy(String text) {
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			}
		});
		frame.setContentPane(view);
		frame.setVisible(true);
		// About 60 frames a second keeps the animations smooth.
		new Timer(16, e -> view.repaint()).start();
	}

	private static void preview(File folder) throws Exception {
		if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("cannot create " + folder);
		Settings settings = new Settings();
		LauncherView view = new LauncherView(settings, new LauncherView.Host() {
			@Override
			public void close() {
			}

			@Override
			public void minimize() {
			}

			@Override
			public void dragBy(int dx, int dy) {
			}

			@Override
			public void openLink(String url) {
			}

			@Override
			public void copy(String text) {
			}
		});
		view.setSize(LauncherView.WIDTH, LauncherView.HEIGHT);
		String[] names = { "main", "settings", "account" };
		for (int i = 0; i < names.length; i++) {
			view.showOverlay(i);
			BufferedImage image = new BufferedImage(LauncherView.WIDTH, LauncherView.HEIGHT, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			// Twice, so values that settle over a frame are in their final place.
			view.paint(g);
			view.showOverlay(i);
			view.paint(g);
			g.dispose();
			ImageIO.write(image, "png", new File(folder, "launcher-" + names[i] + ".png"));
		}
		System.out.println("Preview pages written to " + folder);
	}
}
