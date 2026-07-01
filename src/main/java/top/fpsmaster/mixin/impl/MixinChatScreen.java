package top.fpsmaster.mixin.impl;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
//? if >=1.21.11 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.command.CommandManager;

import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public class MixinChatScreen {
    @Unique
    private static final String FPSMASTER_COPY_PREFIX = "\u0000#COPY";

    @Shadow
    protected EditBox input;

    @Unique
    private List<String> fpsmaster$completionCandidates = Collections.emptyList();

    @Unique
    private String fpsmaster$completionAppliedInput = "";

    @Unique
    private int fpsmaster$completionIndex = -1;

    // handleChatInput returns void on 1.21.1+ but boolean on 1.20.1/1.19.2, so the callback type differs:
    // CallbackInfo + cancel() vs CallbackInfoReturnable<Boolean> + setReturnValue(true) (true = handled).
    //? if >=1.21.1 {
    @Inject(method = "handleChatInput", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientPacketListener.sendChat(Ljava/lang/String;)V"), cancellable = true)
    public void onCharInput(String message, boolean addToRecentChat, CallbackInfo ci) {
        if (message.startsWith(FPSMASTER_COPY_PREFIX)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(message.substring(FPSMASTER_COPY_PREFIX.length()));
            ci.cancel();
            return;
        }

        if (CommandManager.isCommandMessage(message)) {
            CommandManager.parse(CommandManager.stripPrefix(message));
            ci.cancel();
        }
    }
    //?} elif >=1.20 {
    /*@Inject(method = "handleChatInput", at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientPacketListener.sendChat(Ljava/lang/String;)V"), cancellable = true)
    public void onCharInput(String message, boolean addToRecentChat, CallbackInfoReturnable<Boolean> cir) {
        if (message.startsWith(FPSMASTER_COPY_PREFIX)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(message.substring(FPSMASTER_COPY_PREFIX.length()));
            cir.setReturnValue(true);
            return;
        }

        if (CommandManager.isCommandMessage(message)) {
            CommandManager.parse(CommandManager.stripPrefix(message));
            cir.setReturnValue(true);
        }
    }*/
    //?} else {
    /*// 1.19.2 predates ClientPacketListener.sendChat(String); ChatScreen sends via LocalPlayer.chatSigned.
    @Inject(method = "handleChatInput", at = @At(value = "INVOKE", target = "net/minecraft/client/player/LocalPlayer.chatSigned(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V"), cancellable = true)
    public void onCharInput(String message, boolean addToRecentChat, CallbackInfoReturnable<Boolean> cir) {
        if (message.startsWith(FPSMASTER_COPY_PREFIX)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(message.substring(FPSMASTER_COPY_PREFIX.length()));
            cir.setReturnValue(true);
            return;
        }

        if (CommandManager.isCommandMessage(message)) {
            CommandManager.parse(CommandManager.stripPrefix(message));
            cir.setReturnValue(true);
        }
    }*///?}

    //? if >=1.20 {
    @ModifyArg(
            method = "handleChatInput",
            at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ClientPacketListener.sendChat(Ljava/lang/String;)V")
    )
    private String fpsmaster$trimOutgoingChatMessage(String message) {
        return message.trim();
    }
    //?} else {
    /*@ModifyArg(
            method = "handleChatInput",
            at = @At(value = "INVOKE", target = "net/minecraft/client/player/LocalPlayer.chatSigned(Ljava/lang/String;Lnet/minecraft/network/chat/Component;)V"),
            index = 0
    )
    private String fpsmaster$trimOutgoingChatMessage(String message) {
        return message.trim();
    }*///?}

    //? if >=1.21.11 {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$handleCommandCompletion(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() != GLFW.GLFW_KEY_TAB) {
            return;
        }
    //?} else {
    /*@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$handleCommandCompletion(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != GLFW.GLFW_KEY_TAB) {
            return;
        }
    *///?}

        String currentValue = input.getValue();
        if (!CommandManager.hasCommandPrefix(currentValue)) {
            return;
        }

        String prefix = CommandManager.getPrefix();
        String rawInput = CommandManager.stripPrefix(currentValue);
        if (!rawInput.equals(fpsmaster$completionAppliedInput)) {
            fpsmaster$completionCandidates = CommandManager.complete(rawInput);
            fpsmaster$completionIndex = -1;
        }

        if (fpsmaster$completionCandidates.isEmpty()) {
            return;
        }

        fpsmaster$completionIndex = (fpsmaster$completionIndex + 1) % fpsmaster$completionCandidates.size();
        String completion = fpsmaster$completionCandidates.get(fpsmaster$completionIndex);
        fpsmaster$completionAppliedInput = completion;
        input.setValue(prefix + completion);
        input.setCursorPosition(input.getValue().length());
        cir.setReturnValue(true);
    }
}
