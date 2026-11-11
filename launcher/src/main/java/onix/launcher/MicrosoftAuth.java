package onix.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Microsoft sign-in for Minecraft, using the device code flow.
 *
 * The player never types a password into the launcher. The launcher shows a
 * short code, the player enters it on Microsoft's own page in their browser,
 * and Microsoft hands the launcher a token. That token is traded, step by
 * step, for an Xbox Live token, an XSTS token and finally a Minecraft token.
 *
 * It needs the client ID of an Azure app registration that Mojang has
 * approved for the Minecraft API.
 */
public final class MicrosoftAuth {
	private static final String TENANT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/";
	private static final String SCOPE = "XboxLive.signin offline_access";

	/** What the player has to do: open the page and type the code. */
	public static final class Prompt {
		public String userCode, verificationUri, deviceCode;
		public int intervalSeconds, expiresInSeconds;
	}

	/** A finished sign-in. */
	public static final class Session {
		public String playerName, playerUuid, minecraftToken, refreshToken;
	}

	public static final class AuthException extends Exception {
		private static final long serialVersionUID = 1L;

		public AuthException(String message) {
			super(message);
		}
	}

	private final String clientId;

	public MicrosoftAuth(String clientId) {
		this.clientId = clientId;
	}

	/** Step one: ask Microsoft for a code the player can enter. */
	public Prompt begin() throws IOException, AuthException {
		JsonObject json = form(TENANT + "devicecode", "client_id=" + enc(clientId) + "&scope=" + enc(SCOPE));
		if (!json.has("user_code")) throw new AuthException(describe(json, "Microsoft did not return a sign-in code"));
		Prompt p = new Prompt();
		p.userCode = json.get("user_code").getAsString();
		p.deviceCode = json.get("device_code").getAsString();
		p.verificationUri = json.get("verification_uri").getAsString();
		p.intervalSeconds = json.has("interval") ? json.get("interval").getAsInt() : 5;
		p.expiresInSeconds = json.has("expires_in") ? json.get("expires_in").getAsInt() : 900;
		return p;
	}

	/** Step two: wait until the player has entered the code, then finish the sign-in. Blocks. */
	public Session finish(Prompt prompt) throws IOException, AuthException, InterruptedException {
		long deadline = System.currentTimeMillis() + prompt.expiresInSeconds * 1000L;
		int interval = Math.max(1, prompt.intervalSeconds);
		while (System.currentTimeMillis() < deadline) {
			Thread.sleep(interval * 1000L);
			JsonObject json = form(TENANT + "token", "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
					+ "&client_id=" + enc(clientId) + "&device_code=" + enc(prompt.deviceCode));
			if (json.has("access_token")) return minecraft(json);
			String error = json.has("error") ? json.get("error").getAsString() : "";
			if ("authorization_pending".equals(error)) continue;
			if ("slow_down".equals(error)) {
				interval += 5;
				continue;
			}
			throw new AuthException(describe(json, "Sign-in was not completed"));
		}
		throw new AuthException("The sign-in code expired. Start again.");
	}

	/** Signs in again with the saved refresh token, without asking the player anything. */
	public Session refresh(String refreshToken) throws IOException, AuthException {
		JsonObject json = form(TENANT + "token", "grant_type=refresh_token&client_id=" + enc(clientId)
				+ "&refresh_token=" + enc(refreshToken) + "&scope=" + enc(SCOPE));
		if (!json.has("access_token")) throw new AuthException(describe(json, "The saved sign-in is no longer valid"));
		return minecraft(json);
	}

	/** Microsoft token -> Xbox Live -> XSTS -> Minecraft -> profile. */
	private Session minecraft(JsonObject microsoft) throws IOException, AuthException {
		String access = microsoft.get("access_token").getAsString();

		JsonObject xblProps = new JsonObject();
		xblProps.addProperty("AuthMethod", "RPS");
		xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
		xblProps.addProperty("RpsTicket", "d=" + access);
		JsonObject xblBody = new JsonObject();
		xblBody.add("Properties", xblProps);
		xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
		xblBody.addProperty("TokenType", "JWT");
		JsonObject xbl = json("https://user.auth.xboxlive.com/user/authenticate", xblBody, null);
		if (!xbl.has("Token")) throw new AuthException("Xbox Live sign-in failed.");
		String xblToken = xbl.get("Token").getAsString();
		String userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

		JsonArray tokens = new JsonArray();
		tokens.add(xblToken);
		JsonObject xstsProps = new JsonObject();
		xstsProps.addProperty("SandboxId", "RETAIL");
		xstsProps.add("UserTokens", tokens);
		JsonObject xstsBody = new JsonObject();
		xstsBody.add("Properties", xstsProps);
		xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		xstsBody.addProperty("TokenType", "JWT");
		JsonObject xsts = json("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody, null);
		if (!xsts.has("Token")) throw new AuthException(xstsProblem(xsts));

		JsonObject loginBody = new JsonObject();
		loginBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xsts.get("Token").getAsString());
		JsonObject login = json("https://api.minecraftservices.com/authentication/login_with_xbox", loginBody, null);
		if (!login.has("access_token")) {
			throw new AuthException("Minecraft refused the sign-in. The Azure app may not be approved for the Minecraft API yet.");
		}
		String minecraftToken = login.get("access_token").getAsString();

		JsonObject profile = get("https://api.minecraftservices.com/minecraft/profile", minecraftToken);
		if (!profile.has("name")) throw new AuthException("This Microsoft account does not own Minecraft: Java Edition.");

		Session s = new Session();
		s.playerName = profile.get("name").getAsString();
		s.playerUuid = profile.get("id").getAsString();
		s.minecraftToken = minecraftToken;
		s.refreshToken = microsoft.has("refresh_token") ? microsoft.get("refresh_token").getAsString() : "";
		return s;
	}

	private static String xstsProblem(JsonObject xsts) {
		long code = xsts.has("XErr") ? xsts.get("XErr").getAsLong() : 0L;
		if (code == 2148916233L) return "This Microsoft account has no Xbox profile yet. Create one at xbox.com first.";
		if (code == 2148916235L) return "Xbox Live is not available in this account's country.";
		if (code == 2148916238L) return "This is a child account. It must be added to a family by an adult first.";
		return "Xbox sign-in was refused (code " + code + ").";
	}

	private static String describe(JsonObject json, String fallback) {
		if (json.has("error_description")) {
			String text = json.get("error_description").getAsString();
			int line = text.indexOf('\r');
			return line > 0 ? text.substring(0, line) : text;
		}
		return fallback + ".";
	}

	// ----------------------------------------------------------------- http

	private static String enc(String value) throws IOException {
		return URLEncoder.encode(value, "UTF-8");
	}

	private static JsonObject form(String url, String body) throws IOException {
		return request(url, "POST", "application/x-www-form-urlencoded", body, null);
	}

	private static JsonObject json(String url, JsonObject body, String bearer) throws IOException {
		return request(url, "POST", "application/json", body.toString(), bearer);
	}

	private static JsonObject get(String url, String bearer) throws IOException {
		return request(url, "GET", null, null, bearer);
	}

	private static JsonObject request(String url, String method, String contentType, String body, String bearer) throws IOException {
		HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
		c.setRequestMethod(method);
		c.setConnectTimeout(15000);
		c.setReadTimeout(20000);
		c.setRequestProperty("Accept", "application/json");
		if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
		if (body != null) {
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", contentType);
			OutputStream out = c.getOutputStream();
			try {
				out.write(body.getBytes("UTF-8"));
			} finally {
				out.close();
			}
		}
		// Error replies carry a JSON body too, and it says what went wrong.
		InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream();
		if (in == null) return new JsonObject();
		try {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			byte[] chunk = new byte[4096];
			for (int n; (n = in.read(chunk)) > 0;) buffer.write(chunk, 0, n);
			String text = new String(buffer.toByteArray(), "UTF-8").trim();
			if (!text.startsWith("{")) return new JsonObject();
			return new JsonParser().parse(text).getAsJsonObject();
		} finally {
			in.close();
		}
	}
}
