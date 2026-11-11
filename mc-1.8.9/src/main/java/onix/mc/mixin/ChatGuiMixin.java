package onix.mc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.chat.ChatGui;

import onix.core.modules.ExtraModules;
import onix.mc.Bridge;

/** The Chat module: no box behind each line, and a longer scrollback. */
@Mixin(ChatGui.class)
public class ChatGuiMixin {
	/** The first fill in the chat drawing is the dark box behind a line. */
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;fill(IIIII)V", ordinal = 0))
	private void onix$lineBackground(int x1, int y1, int x2, int y2, int color) {
		ExtraModules.Chat chat = Bridge.get().onix.chat;
		if (chat.isEnabled() && chat.clearBackground.on) return;
		GuiElement.fill(x1, y1, x2, y2, color);
	}

	/** The game drops lines beyond 100, in two lists; both limits are raised together. */
	@ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
	private int onix$history(int vanilla) {
		return Bridge.get().onix.chat.historyLength();
	}
}
