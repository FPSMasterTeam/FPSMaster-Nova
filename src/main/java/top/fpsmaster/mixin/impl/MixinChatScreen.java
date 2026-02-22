package top.fpsmaster.mixin.impl;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.command.CommandManager;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Inject(method = "handleChatInput", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientPacketListener.sendChat(Ljava/lang/String;)V"), cancellable = true)
    public void onCharInput(String message, boolean addToRecentChat, CallbackInfo ci) {
        if (message.startsWith(".") && message.length() > 1) {
            CommandManager.parse(message.substring(1));
            ci.cancel();
        }
    }
}
