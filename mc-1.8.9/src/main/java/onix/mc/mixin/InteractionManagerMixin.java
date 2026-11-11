package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;

import onix.mc.Bridge;

@Mixin(ClientPlayerInteractionManager.class)
public class InteractionManagerMixin {
	/** Feeds Reach Display and Combo Counter. Read only: the attack itself is untouched. */
	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void onix$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
		Bridge.get().onAttack(target);
	}
}
