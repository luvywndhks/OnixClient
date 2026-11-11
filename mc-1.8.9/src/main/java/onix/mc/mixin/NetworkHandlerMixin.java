package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeS2CPacket;

import onix.core.event.Events;
import onix.mc.Bridge;

@Mixin(ClientPlayNetworkHandler.class)
public class NetworkHandlerMixin {
	/**
	 * Feeds the TPS display. At the tail, because a packet handler first runs on the
	 * network thread and bails out early; only the client-thread run gets this far.
	 */
	@Inject(method = "handleWorldTime", at = @At("TAIL"))
	private void onix$serverTime(WorldTimeS2CPacket packet, CallbackInfo ci) {
		Bridge.get().onix.events.post(Events.ServerTime.INSTANCE);
	}

	/** Chat lines for AutoGG. Tail for the same reason as above. */
	@Inject(method = "handleChatMessage", at = @At("TAIL"))
	private void onix$chat(net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket packet, CallbackInfo ci) {
		if (packet.getMessage() != null) Bridge.get().onChat(packet.getMessage().getFormattedString());
	}
}
