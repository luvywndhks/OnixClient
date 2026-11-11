package onix.core.anim;

/**
 * A value that glides towards a target. It is driven by frame time, so it
 * looks the same at 60 and at 600 FPS, and it stops doing work once settled.
 */
public final class Anim {
	public float value;
	public float target;
	private final float speed;

	/** speed: higher is snappier. 18 settles in about a fifth of a second. */
	public Anim(float value, float speed) {
		this.value = value;
		this.target = value;
		this.speed = speed;
	}

	public float update(float target, float deltaSeconds) {
		this.target = target;
		float diff = target - value;
		if (diff == 0f) return value;
		if (Math.abs(diff) < 0.0005f) {
			value = target;
			return value;
		}
		value += diff * (1f - (float) Math.exp(-speed * deltaSeconds));
		return value;
	}

	public void snap(float value) {
		this.value = value;
		this.target = value;
	}

	public boolean settled() {
		return value == target;
	}

	/** Slow start, slow end. */
	public static float smooth(float t) {
		t = t < 0f ? 0f : (t > 1f ? 1f : t);
		return t * t * (3f - 2f * t);
	}

	/** Fast start, slow end. */
	public static float easeOut(float t) {
		t = t < 0f ? 0f : (t > 1f ? 1f : t);
		float u = 1f - t;
		return 1f - u * u * u;
	}
}
