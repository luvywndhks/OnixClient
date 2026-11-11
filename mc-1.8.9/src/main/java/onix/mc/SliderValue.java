package onix.mc;

/** Implemented on vanilla sliders by a mixin, so the themed button drawing can read the position. */
public interface SliderValue {
	/** 0 at the left end, 1 at the right end. */
	float onix$sliderValue();
}
