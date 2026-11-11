package onix.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Everything the launcher remembers, kept in launcher.json in the launcher's
 * own folder. The file holds sign-in tokens, so it stays on this computer and
 * is never sent anywhere except to Microsoft's and Mojang's own servers.
 */
public final class Settings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Memory given to the game, in megabytes. */
	public int memoryMb = 2048;
	public int width = 1280, height = 720;
	public boolean fullscreen;
	public boolean closeOnLaunch = true;
	/** Empty means: use the Java that is running the launcher. */
	public String javaPath = "";

	/**
	 * The Application (client) ID of the Azure app registration used for
	 * Microsoft sign-in. Each launcher needs its own; see the Account page.
	 */
	public static final String DEFAULT_CLIENT_ID = "d65437a9-2720-40b5-947d-534dd4ddf4f4";
	public String azureClientId = DEFAULT_CLIENT_ID;

	// The signed-in account. The refresh token lets the launcher sign in again without asking.
	public String playerName = "";
	public String playerUuid = "";
	public String refreshToken = "";

	public static File dir() {
		String appData = System.getenv("APPDATA");
		File base = appData != null ? new File(appData) : new File(System.getProperty("user.home"));
		return new File(base, ".onixclient");
	}

	private static File file() {
		return new File(dir(), "launcher.json");
	}

	public static Settings load() {
		File file = file();
		if (file.isFile()) {
			try {
				Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
				try {
					Settings loaded = GSON.fromJson(reader, Settings.class);
					if (loaded != null) return loaded.checked();
				} finally {
					reader.close();
				}
			} catch (Exception e) {
				System.err.println("Could not read " + file + ": " + e);
			}
		}
		return new Settings();
	}

	/** Brings values from an edited or older file back into range. */
	private Settings checked() {
		memoryMb = Math.max(1024, Math.min(16384, memoryMb));
		width = Math.max(640, width);
		height = Math.max(360, height);
		if (javaPath == null) javaPath = "";
		if (azureClientId == null || azureClientId.isEmpty()) azureClientId = DEFAULT_CLIENT_ID;
		if (playerName == null) playerName = "";
		if (playerUuid == null) playerUuid = "";
		if (refreshToken == null) refreshToken = "";
		return this;
	}

	public void save() {
		try {
			File file = file();
			File parent = file.getParentFile();
			if (!parent.isDirectory() && !parent.mkdirs()) throw new java.io.IOException("cannot create " + parent);
			// Write beside the real file first, so a crash mid-write cannot wipe the settings.
			File temp = new File(file.getPath() + ".tmp");
			Writer writer = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8");
			try {
				GSON.toJson(this, writer);
			} finally {
				writer.close();
			}
			if (file.exists() && !file.delete()) throw new java.io.IOException("cannot replace " + file);
			if (!temp.renameTo(file)) throw new java.io.IOException("cannot rename " + temp);
		} catch (Exception e) {
			System.err.println("Could not save settings: " + e);
		}
	}

	public boolean signedIn() {
		return !playerName.isEmpty() && !refreshToken.isEmpty();
	}
}
