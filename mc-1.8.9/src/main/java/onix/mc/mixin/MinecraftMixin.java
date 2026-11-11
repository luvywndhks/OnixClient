package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.GameOptions;

import onix.core.modules.AnimationModules;
import onix.mc.Bridge;
import onix.mc.DevCapture;

@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Shadow
	private int attackCooldown;
	@Shadow
	public LocalClientPlayerEntity player;
	@Shadow
	public GameOptions options;
	@Shadow
	public net.minecraft.client.gui.screen.Screen screen;

	@Inject(method = "tick", at = @At("HEAD"))
	private void onix$tick(CallbackInfo ci) {
		Bridge bridge = Bridge.get();
		bridge.onTick();
		DevCapture.onTick((Minecraft) (Object) this);

		// 1.7 Animations, "Punching During Usage": 1.8 ignores the attack key while an
		// item is in use, so the arm never swings. Start the swing on the client only;
		// nothing is sent to the server.
		AnimationModules.OldAnimations old = bridge.onix.oldAnimations;
		if (old.isEnabled() && old.punching.on && player != null && screen == null
				&& player.getItemInUse() != null && options.attackKey.isPressed()
				&& (!player.armSwinging || player.armSwingingTicks < 0 || player.armSwingingTicks >= 3)) {
			player.armSwingingTicks = -1;
			player.armSwinging = true;
		}
	}

	/** Hit Delay Fix: 1.8 blocks attacks for 10 ticks after a miss; 1.7 did not. */
	@Inject(method = "doAttack", at = @At("HEAD"))
	private void onix$hitDelayFix(CallbackInfo ci) {
		if (Bridge.get().onix.global.hitDelayFix.on) attackCooldown = 0;
	}
}
