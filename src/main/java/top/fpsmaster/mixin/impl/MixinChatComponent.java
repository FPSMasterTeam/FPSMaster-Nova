package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {
    @Shadow
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError();
    }

    @Unique
    private boolean fpsmaster$betterChatBypass;

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;black(F)I")
    )
    private int fpsmaster$betterChatBackgroundColor(float alpha) {
        return BetterChat.backgroundColor(alpha);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$decorateSimpleMessage(Component component, CallbackInfo ci) {
        if (this.fpsmaster$betterChatBypass || !BetterChat.isActive()) {
            return;
        }

        this.fpsmaster$handleBetterChat(component, null, null, ci);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$decorateTaggedMessage(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (this.fpsmaster$betterChatBypass || !BetterChat.isActive()) {
            return;
        }

        this.fpsmaster$handleBetterChat(component, signature, tag, ci);
    }

    @Unique
    private void fpsmaster$handleBetterChat(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        String rawText = component.getString();
        boolean folded = BetterChat.shouldFold(rawText);
        Component decorated = BetterChat.decorate(component, rawText, folded);

        if (!folded && decorated == component) {
            return;
        }

        if (folded && !this.allMessages.isEmpty()) {
            this.allMessages.remove(0);
            this.refreshTrimmedMessages();
        }

        this.fpsmaster$betterChatBypass = true;
        try {
            ((ChatComponent) (Object) this).addMessage(decorated, signature, tag);
        } finally {
            this.fpsmaster$betterChatBypass = false;
        }
        ci.cancel();
    }
}

//?}
