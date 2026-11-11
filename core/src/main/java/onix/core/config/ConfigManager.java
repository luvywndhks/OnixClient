package onix.core.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import onix.core.Onix;
import onix.core.module.HudModule;
import onix.core.module.Module;
import onix.core.setting.Setting;

/**
 * Settings are kept as profiles: one JSON file each in the "profiles" folder
 * inside the client's folder in the game directory. Exactly one profile is in
 * use at a time; saving writes to it, and switching loads another.
 *
 * A broken or missing file never stops the game.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String DEFAULT = "Default";
	public static final int MAX_NAME = 24;

	private final Onix onix;
	private final File dir, profilesDir, activeFile;
	private String active = DEFAULT;

	public ConfigManager(Onix onix, File dir) {
		this.onix = onix;
		this.dir = dir;
		this.profilesDir = new File(dir, "profiles");
		this.activeFile = new File(dir, "profile.txt");
	}

	public File profilesDir() {
		return profilesDir;
	}

	public String active() {
		return active;
	}

	private File file(String profile) {
		return new File(profilesDir, profile + ".json");
	}

	/** Profile names, sorted, always including the one in use. */
	public List<String> profiles() {
		List<String> names = new ArrayList<String>();
		String[] files = profilesDir.list();
		if (files != null) {
			for (String f : files) {
				if (f.endsWith(".json")) names.add(f.substring(0, f.length() - 5));
			}
		}
		if (!names.contains(active)) names.add(active);
		Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Letters, digits, spaces, dashes and underscores; anything else cannot be a file name everywhere. */
	public static boolean validName(String name) {
		if (name == null) return false;
		String trimmed = name.trim();
		if (trimmed.isEmpty() || trimmed.length() > MAX_NAME || !trimmed.equals(name)) return false;
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_')) return false;
		}
		return true;
	}

	private boolean exists(String name) {
		for (String p : profiles()) {
			if (p.equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	/** A name not taken yet: "Base", then "Base 2", "Base 3" and so on. */
	public String freeName(String base) {
		if (base.length() > MAX_NAME - 4) base = base.substring(0, MAX_NAME - 4).trim();
		if (!exists(base)) return base;
		for (int n = 2;; n++) {
			if (!exists(base + " " + n)) return base + " " + n;
		}
	}

	// ----------------------------------------------------------- start, save

	public void load() {
		// Before profiles existed there was a single config.json; it becomes the Default profile.
		File legacy = new File(dir, "config.json");
		if (legacy.isFile() && !file(DEFAULT).isFile() && (profilesDir.isDirectory() || profilesDir.mkdirs())) {
			if (!legacy.renameTo(file(DEFAULT))) System.err.println("[" + Onix.NAME + "] Could not move " + legacy);
		}
		if (activeFile.isFile()) {
			try {
				String name = readText(activeFile).trim();
				if (validName(name) && file(name).isFile()) active = name;
			} catch (Exception e) {
				System.err.println("[" + Onix.NAME + "] Could not read " + activeFile + ": " + e);
			}
		}
		readFile(file(active));
	}

	/** Writes the current settings into the profile in use. */
	public void save() {
		if (writeFile(file(active), write())) writeText(activeFile, active);
	}

	// -------------------------------------------------------------- profiles

	/** Makes a new profile holding the current settings, without switching to it. */
	public boolean create(String name) {
		if (!validName(name) || exists(name)) return false;
		return writeFile(file(name), write());
	}

	/** Copies a profile under a free name and returns that name, or null if it failed. */
	public String duplicate(String name) {
		if (name.equals(active)) save();
		File source = file(name);
		if (!source.isFile()) return null;
		String copy = freeName(name + " copy");
		try {
			return writeFile(file(copy), new JsonParser().parse(readText(source)).getAsJsonObject()) ? copy : null;
		} catch (Exception e) {
			System.err.println("[" + Onix.NAME + "] Could not copy profile " + name + ": " + e);
			return null;
		}
	}

	public boolean rename(String from, String to) {
		if (!validName(to) || from.equals(to)) return false;
		// Changing only the letter case of a name is fine; taking another profile's name is not.
		if (!from.equalsIgnoreCase(to) && exists(to)) return false;
		if (from.equals(active)) save();
		File source = file(from);
		if (!source.isFile() || !source.renameTo(file(to))) return false;
		if (from.equals(active)) {
			active = to;
			writeText(activeFile, active);
		}
		return true;
	}

	/** The profile in use cannot be deleted, so there is always one left. */
	public boolean delete(String name) {
		if (name.equals(active)) return false;
		File target = file(name);
		return target.isFile() && target.delete();
	}

	/** Saves the profile in use, then loads another in its place. */
	public boolean use(String name) {
		if (name.equals(active)) return true;
		File target = file(name);
		if (!target.isFile()) return false;
		save();
		// Start from defaults, so a setting the other profile never stored does not carry over.
		for (Module m : onix.modules) {
			if (m.toggleable) m.setEnabled(false);
			for (Setting<?> s : m.settings) s.reset();
			if (m instanceof HudModule) ((HudModule) m).resetPosition();
		}
		active = name;
		readFile(target);
		writeText(activeFile, active);
		return true;
	}

	// ------------------------------------------------------------------ json

	public JsonObject write() {
		JsonObject modules = new JsonObject();
		for (Module m : onix.modules) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isWanted());
			o.addProperty("favorite", m.favorite);
			if (m instanceof HudModule) {
				o.addProperty("x", ((HudModule) m).posX);
				o.addProperty("y", ((HudModule) m).posY);
			}
			JsonObject settings = new JsonObject();
			for (Setting<?> s : m.settings) {
				JsonElement value = s.save();
				if (value != null) settings.add(s.name, value);
			}
			o.add("settings", settings);
			modules.add(m.name, o);
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", 1);
		root.add("modules", modules);
		return root;
	}

	public void read(JsonObject root) {
		JsonElement list = root.get("modules");
		if (list == null || !list.isJsonObject()) return;
		for (Module m : onix.modules) {
			JsonElement e = list.getAsJsonObject().get(m.name);
			if (e == null || !e.isJsonObject()) continue;
			JsonObject o = e.getAsJsonObject();
			try {
				JsonElement settings = o.get("settings");
				if (settings != null && settings.isJsonObject()) {
					for (Setting<?> s : m.settings) {
						JsonElement value = settings.getAsJsonObject().get(s.name);
						if (value != null && !value.isJsonNull()) s.load(value);
					}
				}
				if (m instanceof HudModule) {
					if (o.has("x")) ((HudModule) m).posX = o.get("x").getAsFloat();
					if (o.has("y")) ((HudModule) m).posY = o.get("y").getAsFloat();
				}
				if (o.has("favorite")) m.favorite = o.get("favorite").getAsBoolean();
				// Enable last, so onEnable sees the loaded settings.
				if (m.toggleable && o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
			} catch (RuntimeException ex) {
				System.err.println("[" + Onix.NAME + "] Skipped bad config for " + m.name + ": " + ex);
			}
		}
	}

	// ----------------------------------------------------------------- files

	private void readFile(File file) {
		if (!file.isFile()) return;
		try {
			JsonElement root = new JsonParser().parse(readText(file));
			if (root.isJsonObject()) read(root.getAsJsonObject());
		} catch (Exception e) {
			System.err.println("[" + Onix.NAME + "] Could not read " + file + ": " + e);
		}
	}

	private boolean writeFile(File file, JsonObject json) {
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new java.io.IOException("cannot create " + parent);
			// Write beside the real file first, so a crash mid-write cannot wipe the profile.
			File temp = new File(file.getPath() + ".tmp");
			Writer writer = new OutputStreamWriter(new FileOutputStream(temp), "UTF-8");
			try {
				GSON.toJson(json, writer);
			} finally {
				writer.close();
			}
			if (file.exists() && !file.delete()) throw new java.io.IOException("cannot replace " + file);
			if (!temp.renameTo(file)) throw new java.io.IOException("cannot rename " + temp);
			return true;
		} catch (Exception e) {
			System.err.println("[" + Onix.NAME + "] Could not save " + file + ": " + e);
			return false;
		}
	}

	private static String readText(File file) throws java.io.IOException {
		Reader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
		try {
			StringBuilder text = new StringBuilder();
			char[] chunk = new char[4096];
			for (int n; (n = reader.read(chunk)) > 0;) text.append(chunk, 0, n);
			return text.toString();
		} finally {
			reader.close();
		}
	}

	private void writeText(File file, String text) {
		try {
			File parent = file.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new java.io.IOException("cannot create " + parent);
			Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
			try {
				writer.write(text);
			} finally {
				writer.close();
			}
		} catch (Exception e) {
			System.err.println("[" + Onix.NAME + "] Could not write " + file + ": " + e);
		}
	}
}
