package onix.core.input;

import onix.core.platform.Platform;

/**
 * Counts attack and use clicks over the last second. Shared by the CPS
 * counter and Keystrokes so the mouse is only sampled once per frame.
 */
public final class ClickTracker {
	private static final int CAPACITY = 64;
	private static final long WINDOW = 1000000000L;

	private final long[][] times = new long[2][CAPACITY];
	private final int[] head = new int[2];
	private final boolean[] down = new boolean[2];

	public void poll(Platform platform) {
		int[] keys = platform.controlKeys();
		sample(0, platform.isDown(keys[7]));
		sample(1, platform.isDown(keys[8]));
	}

	private void sample(int button, boolean isDown) {
		if (isDown && !down[button]) {
			times[button][head[button]] = System.nanoTime();
			head[button] = (head[button] + 1) % CAPACITY;
		}
		down[button] = isDown;
	}

	/** button 0 is attack, 1 is use. */
	public int cps(int button) {
		long cutoff = System.nanoTime() - WINDOW;
		int count = 0;
		long[] t = times[button];
		for (int i = 0; i < CAPACITY; i++) {
			if (t[i] != 0 && t[i] > cutoff) count++;
		}
		return count;
	}

	public boolean isDown(int button) {
		return down[button];
	}
}
