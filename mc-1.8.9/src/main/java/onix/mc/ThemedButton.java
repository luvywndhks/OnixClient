package onix.mc;

/** Implemented on every vanilla button by a mixin, so a screen can be laid out afresh. */
public interface ThemedButton {
	void onix$bounds(int x, int y, int width, int height);
}
