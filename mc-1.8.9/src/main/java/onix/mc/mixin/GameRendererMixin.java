package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;

import onix.core.modules.FreelookModule;
import onix.mc.Bridge;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	private Minecraft minecraft;

	@Unique
	private float onix$yaw, onix$pitch, onix$lastYaw, onix$lastPitch;
	@Unique
	private boolean onix$swapped;

	/** Zoom. Only the world FOV is scaled; the hand keeps its own fixed FOV. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void onix$zoom(float tickDelta, boolean worldFov, CallbackInfoReturnable<Float> cir) {
		if (!worldFov) return;
		float scale = Bridge.get().onix.zoom.fovScale();
		if (scale != 1f) cir.setReturnValue(cir.getReturnValue() * scale);
	}

	@Inject(method = "render(FJ)V", at = @At("HEAD"))
	private void onix$freelookState(float tickDelta, long limit, CallbackInfo ci) {
		Bridge.get().updateFreelook();
		Bridge.get().updateSnaplook();
	}

	/** Freelook: while it is held, the mouse turns the camera and leaves the player alone. */
	@Redirect(method = "render(FJ)V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;updateLocalPlayerCamera(FF)V"))
	private void onix$freelookTurn(LocalClientPlayerEntity player, float dx, float dy) {
		FreelookModule freelook = Bridge.get().onix.freelook;
		if (freelook.looking) freelook.turn(dx, dy);
		else player.updateLocalPlayerCamera(dx, dy);
	}

	/** The camera is placed from the player's angles, so lend it the freelook angles for this one call. */
	@Inject(method = "transformCamera", at = @At("HEAD"))
	private void onix$freelookCameraIn(float tickDelta, CallbackInfo ci) {
		FreelookModule freelook = Bridge.get().onix.freelook;
		LocalClientPlayerEntity player = minecraft.player;
		onix$swapped = freelook.looking && player != null;
		if (!onix$swapped) return;
		onix$yaw = player.yaw;
		onix$pitch = player.pitch;
		onix$lastYaw = player.lastYaw;
		onix$lastPitch = player.lastPitch;
		player.yaw = player.lastYaw = freelook.yaw;
		player.pitch = player.lastPitch = freelook.pitch;
	}

	@Inject(method = "transformCamera", at = @At("RETURN"))
	private void onix$freelookCameraOut(float tickDelta, CallbackInfo ci) {
		if (!onix$swapped) return;
		LocalClientPlayerEntity player = minecraft.player;
		onix$swapped = false;
		if (player == null) return;
		player.yaw = onix$yaw;
		player.pitch = onix$pitch;
		player.lastYaw = onix$lastYaw;
		player.lastPitch = onix$lastPitch;
	}

	/** No Hurt Cam: skip the tilt the game applies to the camera after damage. */
	@Inject(method = "applyHurtCam", at = @At("HEAD"), cancellable = true)
	private void onix$noHurtCam(float tickDelta, CallbackInfo ci) {
		if (Bridge.get().onix.global.noHurtCam.on) ci.cancel();
	}

	/** Global Settings, "Disable Weather Rendering". */
	@Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
	private void onix$noWeather(float tickDelta, CallbackInfo ci) {
		if (Bridge.get().onix.global.noWeather.on) ci.cancel();
	}

	/** Motion Blur: after the world and the hand, before the HUD. */
	@Inject(method = "renderWorld(FJ)V", at = @At("TAIL"))
	private void onix$motionBlur(float tickDelta, long limit, CallbackInfo ci) {
		Bridge.get().onWorldRendered();
	}
}
