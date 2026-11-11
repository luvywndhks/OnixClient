package onix.launcher;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Gets a player's face from their skin, using Mojang's own servers only:
 * the session server says where the skin is, and the face is the 8 by 8
 * square on its front with the hat layer laid over it.
 */
final class SkinHead {
	private SkinHead() {
	}

	/** Returns an 8 by 8 image, or null if the skin could not be fetched. Blocks; call off the UI thread. */
	static BufferedImage fetch(String uuid) {
		try {
			JsonObject profile = json("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replace("-", ""));
			if (profile == null || !profile.has("properties")) return null;
			String encoded = profile.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
			JsonObject textures = new JsonParser().parse(new String(Base64.getDecoder().decode(encoded), "UTF-8")).getAsJsonObject();
			JsonObject skin = textures.getAsJsonObject("textures").getAsJsonObject("SKIN");
			if (skin == null) return null;
			// Skins are always served from Mojang's texture host; anything else is not fetched.
			String address = skin.get("url").getAsString().replace("http://", "https://");
			if (!address.startsWith("https://textures.minecraft.net/")) return null;
			BufferedImage image = ImageIO.read(new URL(address));
			if (image == null || image.getWidth() < 64) return null;
			BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = face.createGraphics();
			g.drawImage(image.getSubimage(8, 8, 8, 8), 0, 0, null);
			g.drawImage(image.getSubimage(40, 8, 8, 8), 0, 0, null);
			g.dispose();
			return face;
		} catch (Exception e) {
			System.err.println("Could not load the skin: " + e);
			return null;
		}
	}

	private static JsonObject json(String address) throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
		c.setConnectTimeout(10000);
		c.setReadTimeout(10000);
		if (c.getResponseCode() != 200) return null;
		InputStream in = c.getInputStream();
		try {
			return new JsonParser().parse(new InputStreamReader(in, "UTF-8")).getAsJsonObject();
		} finally {
			in.close();
		}
	}
}
