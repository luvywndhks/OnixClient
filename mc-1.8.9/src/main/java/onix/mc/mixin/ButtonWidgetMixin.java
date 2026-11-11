package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;

import onix.mc.SliderValue;
import onix.mc.VanillaTheme;

/** Every vanilla button and slider is drawn in the client style; its behaviour is untouched. */
@Mixin(ButtonWidget.class)
public abstract class ButtonWidgetMixin implements onix.mc.ThemedButton {
	@Shadow
	protected int width;
	@Shadow
	protected int height;
	@Shadow
	public int x;
	@Shadow
	public int y;
	@Shadow
	public String message;
	@Shadow
	public boolean active;
	@Shadow
	public boolean visible;
	@Shadow
	protected boolean hovered;

	@Shadow
	protected abstract void renderBackground(Minecraft minecraft, int mouseX, int mouseY);

	@Unique
	private float onix$hover;
	@Unique
	private long onix$last;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void onix$render(Minecraft minecraft, int mouseX, int mouseY, CallbackInfo ci) {
		ci.cancel();
		if (!visible) return;
		hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;

		long now = System.nanoTime();
		float dt = onix$last == 0L ? 0f : Math.min(0.1f, (now - onix$last) / 1e9f);
		onix$last = now;
		float target = hovered && active ? 1f : 0f;
		onix$hover += (target - onix$hover) * (1f - (float) Math.exp(-14f * dt));

		// A slider updates its value from the mouse in here while it is being dragged.
		renderBackground(minecraft, mouseX, mouseY);
		Object self = this;
		float slider = self instanceof SliderValue ? ((SliderValue) self).onix$sliderValue() : -1f;
		VanillaTheme.button(x, y, width, height, message, active, onix$hover, slider);
	}

	@Override
	public void onix$bounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
}
