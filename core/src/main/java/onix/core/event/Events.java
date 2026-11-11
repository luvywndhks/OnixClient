package onix.core.event;

import onix.core.render.Renderer;

/**
 * Every event is a single reused instance, so posting one never allocates.
 */
public final class Events {
	private Events() {
	}

	/** Once per client tick (20 per second). */
	public static final class Tick {
		public static final Tick INSTANCE = new Tick();
	}

	/** Once per frame while the in-game HUD is drawn. Sizes are in real pixels. */
	public static final class HudRender {
		public static final HudRender INSTANCE = new HudRender();
		public Renderer renderer;
		public float width, height;
		/** Seconds since the previous frame. */
		public float delta;
	}

	/** A key or mouse button changed state while no screen is open. */
	public static final class Key {
		public static final Key INSTANCE = new Key();
		public int code;
		public boolean pressed;
	}

	/** The player attacked an entity. */
	public static final class Attack {
		public static final Attack INSTANCE = new Attack();
		/** Distance from the player's eyes to the target, in blocks. */
		public float reach;
	}

	/** An entity the player just attacked took the damage. */
	public static final class HitLanded {
		public static final HitLanded INSTANCE = new HitLanded();
	}

	/** The player took damage. */
	public static final class PlayerHurt {
		public static final PlayerHurt INSTANCE = new PlayerHurt();
	}

	/** The server sent its clock, which it does once every 20 of its ticks. */
	public static final class ServerTime {
		public static final ServerTime INSTANCE = new ServerTime();
	}

	/** A line arrived in chat. */
	public static final class Chat {
		public static final Chat INSTANCE = new Chat();
		/** The line without color codes. */
		public String text;
	}
}
