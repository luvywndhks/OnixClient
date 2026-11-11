package onix.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * One option of a module. The settings screen builds a widget from the
 * concrete type, and the config file stores {@link #save()}.
 */
public abstract class Setting<T> {
	public final String name;
	/** Collapsible group this setting is listed under, or null for the top of the page. */
	public String section;
	/** Small grey line under the row, or null. */
	public String note;
	protected T value;
	private final T defaultValue;

	protected Setting(String name, T value) {
		this.name = name;
		this.value = value;
		this.defaultValue = value;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	public void reset() {
		set(defaultValue);
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S section(String section) {
		this.section = section;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S note(String note) {
		this.note = note;
		return (S) this;
	}

	public abstract JsonElement save();

	/** Must tolerate a value written by an older version; keep the current one on a mismatch. */
	public abstract void load(JsonElement json);

	public static final class Bool extends Setting<Boolean> {
		// Read every frame by modules, so it is kept unboxed.
		public boolean on;

		public Bool(String name, boolean value) {
			super(name, value);
			this.on = value;
		}

		@Override
		public void set(Boolean value) {
			super.set(value);
			this.on = value;
		}

		public void toggle() {
			set(!on);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(on);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) set(json.getAsBoolean());
		}
	}

	public static final class Number extends Setting<Float> {
		public final float min, max, step;
		public float v;

		public Number(String name, float value, float min, float max, float step) {
			super(name, value);
			this.min = min;
			this.max = max;
			this.step = step;
			this.v = value;
		}

		@Override
		public void set(Float value) {
			float x = Math.max(min, Math.min(max, value));
			if (step > 0) x = min + Math.round((x - min) / step) * step;
			x = Math.max(min, Math.min(max, x));
			super.set(x);
			this.v = x;
		}

		public boolean isInteger() {
			return step >= 1 && step == Math.rint(step);
		}

		public float fraction() {
			return max == min ? 0 : (v - min) / (max - min);
		}

		public String display() {
			if (isInteger()) return Integer.toString(Math.round(v));
			return String.format(java.util.Locale.ROOT, "%.2f", v);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(v);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsFloat());
		}
	}

	public static final class Mode extends Setting<String> {
		public final String[] options;
		public int index;

		public Mode(String name, String value, String... options) {
			super(name, value);
			this.options = options;
			this.index = indexOf(value);
		}

		private int indexOf(String option) {
			for (int i = 0; i < options.length; i++) {
				if (options[i].equals(option)) return i;
			}
			return -1;
		}

		@Override
		public void set(String value) {
			int i = indexOf(value);
			if (i < 0) return;
			super.set(value);
			this.index = i;
		}

		public void cycle(int by) {
			int n = options.length;
			set(options[((index + by) % n + n) % n]);
		}

		public boolean is(String option) {
			return options[index].equals(option);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive()) set(json.getAsString());
		}
	}

	/** ARGB color. */
	public static final class Color extends Setting<Integer> {
		public int argb;

		public Color(String name, int argb) {
			super(name, argb);
			this.argb = argb;
		}

		@Override
		public void set(Integer value) {
			super.set(value);
			this.argb = value;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(String.format("#%08X", argb));
		}

		@Override
		public void load(JsonElement json) {
			if (!json.isJsonPrimitive()) return;
			String s = json.getAsString();
			if (s.length() != 9 || s.charAt(0) != '#') return;
			try {
				set((int) Long.parseLong(s.substring(1), 16));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	/** A key code of the running platform; 0 means unbound. */
	public static final class Key extends Setting<Integer> {
		public int code;

		public Key(String name, int code) {
			super(name, code);
			this.code = code;
		}

		@Override
		public void set(Integer value) {
			super.set(value);
			this.code = value;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(code);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsInt());
		}
	}

	/** A button; it stores nothing. */
	public static final class Action extends Setting<Boolean> {
		public final String label;
		public final Runnable action;

		public Action(String name, String label, Runnable action) {
			super(name, false);
			this.label = label;
			this.action = action;
		}

		@Override
		public JsonElement save() {
			return null;
		}

		@Override
		public void load(JsonElement json) {
		}
	}
}
