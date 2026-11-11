# Onix Client for Java Edition

A client and launcher for **Minecraft: Java Edition**, in the style of Lunar Client and Badlion Client.
It is a Java Edition remake of [Onix Client](https://onixclient.com), a Bedrock Edition client, made with
the permission of the Onix Client developers.

> **Status: work in progress.** The 1.8.9 client works. The modern-version client is an early preview.
> The launcher has its screens and Microsoft sign-in; installing and starting the game from it is not built yet.

*Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.*

## What it is

| Part | Folder | What it does |
| --- | --- | --- |
| Client core | `core` | Modules, settings, profiles, the menu and its animations. Contains no Minecraft code, so every game version shares it. |
| Client for 1.8.9 | `mc-1.8.9` | Fabric/Ornithe mod. Draws the HUD and menu with its own OpenGL renderer (frosted glass, rounded boxes, text) and hooks the game with mixins. |
| Client for modern versions | `mc-modern` | Fabric mod for Minecraft 26.3. Early preview: HUD and menu through the game's own drawing calls. |
| Launcher | `launcher` | Desktop app (one window, drawn by hand in Java Swing). Account, settings, Microsoft sign-in. |

### Client features

* More than 40 modules: FPS, CPS, keystrokes, coordinates, armor, potion and inventory HUDs, combo and reach,
  TPS, Item Size, Zoom, Freelook, Snaplook, Motion Blur (two versions), custom crosshair, chat tweaks, AutoGG,
  Nick Hider, and more.
* A module menu with search, animations, per-module settings and an editor for moving HUD elements.
* **Profiles:** save settings under a name, switch, copy, rename and open the profiles folder.
* A Global Settings page with performance switches and small fixes (block-break particles, weather, vignette,
  enchantment glint, FPS limit in the background, and others).
* Modules that servers commonly forbid (for example Freelook) switch themselves off on those servers.
* Disabled modules cost nothing: they are not even subscribed to the event bus.

## How the launcher signs in

The launcher uses the Microsoft **device code flow**. The password is never typed into the launcher.

1. The launcher asks Microsoft for a short code and shows it.
2. The player opens `https://www.microsoft.com/link` in their own browser and enters the code.
3. Microsoft gives the launcher a token. The launcher trades it, one step at a time, for an Xbox Live token,
   an XSTS token and a Minecraft access token, then reads the player's profile (name and UUID).

Servers the launcher talks to, and nothing else:

* `login.microsoftonline.com` — Microsoft sign-in
* `user.auth.xboxlive.com`, `xsts.auth.xboxlive.com` — Xbox Live
* `api.minecraftservices.com` — Minecraft token and profile
* `sessionserver.mojang.com` and `textures.minecraft.net` — the player's skin, for the head shown in the corner

### Privacy

* The launcher has **no analytics, no telemetry and no server of its own**.
* The refresh token is saved on the player's own computer in `%APPDATA%\.onixclient\launcher.json`
  (as plain text, like most launchers). Signing out deletes it. It is sent only to Microsoft.
* Settings and profiles are files on the player's computer. Nothing is uploaded.

## Building

You need JDK 25 or newer.

```bash
# Client core and the 1.8.9 client (the mod jar ends up in mc-1.8.9/build/libs)
./gradlew :core:test :mc-1.8.9:build

# Launcher as a Windows program with its own Java inside (launcher/build/dist/Onix Client)
./gradlew :launcher:packageExe

# Modern-version client (its own build, because it needs a newer Loom)
cd mc-modern
./gradlew build
```

To run the 1.8.9 client with Java 8, set `ONIX_RUN_JAVA` to the path of a Java 8 `java.exe` and run
`./gradlew :mc-1.8.9:runClient`.

## Credits

* [Onix Client](https://onixclient.com) — the design and the module ideas this project follows.
* [Polyfrost](https://polyfrost.org) (OneClient, OneConfig and its mods) — studied for ideas about what good
  modules and optimizations look like. No code was copied.
* [Latite Client](https://github.com/LatiteClient/Latite) — the "V2" motion blur follows the approach of its
  MotionBlur module (blending only clean frames), written again from scratch.
* [Fabric](https://fabricmc.net), [Ornithe](https://ornithemc.net) and [Mixin](https://github.com/SpongePowered/Mixin).
* [Poppins](https://fonts.google.com/specimen/Poppins) font, SIL Open Font License.

Minecraft is a trademark of Mojang AB. This project is not made by or endorsed by Mojang or Microsoft.

## License

Not chosen yet. Until a license file is added, all rights are reserved by the author.
