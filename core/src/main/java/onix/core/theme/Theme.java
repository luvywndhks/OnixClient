package onix.core.theme;

/**
 * The client palette, matched to the real Onix Client menu: dark panels the
 * blurred game shows through a little, and teal accents. The field names follow
 * its theme editor.
 */
public final class Theme {
	public int text = 0xFFFFFFFF;
	public int textDim = 0xB3FFFFFF;
	/** "Blocked Color": the dark tint of the two big panels. */
	public int panel = 0xBF1C1C1E;
	/** "Content Background Color": a row inside a panel. */
	public int row = 0x1CFFFFFF;
	public int rowHover = 0x2BFFFFFF;
	/** "Highlight Color": the selected row. */
	public int rowSelected = 0x3DFFFFFF;
	/** "Enabled Color" and "Button Color". */
	public int accent = 0xFF146B8C;
	/** "Disabled Color": the track of a switch that is off. */
	public int disabled = 0xFFADADAD;
	/** "Outline Color". */
	public int outline = 0x6696C8E6;
	/** "Scrollbar Color". */
	public int scrollbar = 0xFF878787;
	public int knob = 0xFFFFFFFF;
	/** The big "Settings" button of the HUD editor. */
	public int button = 0xFF1E82F0;
	/** A module row whose module is switched on. */
	public int rowEnabled = 0xCC1C4A5E;
	public int rowEnabledHover = 0xCC245B73;
	/** A module the current server does not allow. */
	public int rowBlocked = 0xF05C2428;
	public int blocked = 0xFFC8474E;
	public int hudBackground = 0x991A1A1A;

	public int withAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/** Linear blend of two ARGB colors, t from 0 to 1. */
	public static int mix(int a, int b, float t) {
		if (t <= 0f) return a;
		if (t >= 1f) return b;
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}
}
