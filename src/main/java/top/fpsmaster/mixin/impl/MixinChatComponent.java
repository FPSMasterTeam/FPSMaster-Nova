package top.fpsmaster.mixin.impl;

//? if >=1.21.9 {

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

    // 1.21.9+ renders the chat via ChatGraphicsAccess and draws the line background with ARGB.black(alpha).
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

//?} elif >=1.21.5 {

/*import net.minecraft.client.GuiMessage;
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

    // 1.21.5..1.21.8 render each chat line via a forEachLine lambda (synthetic method_71992) which draws
    // the line background with ARGB.color(alpha, 0xFF000000). That first ARGB.color(F, I) call (ordinal 0)
    // is the background fill; the second is the message-tag indicator.
    @Redirect(
            method = "method_71992",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;color(FI)I", ordinal = 0)
    )
    private int fpsmaster$betterChatBackgroundColor(float alpha, int rgb) {
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
}*/

//?} elif >=1.21.1 {

/*import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;

import java.util.List;

// 1.21.1 is pre-1.21.5 immediate-mode render (GuiGraphics.fill, no ARGB class) but its render takes the
// extra "focused" boolean and the trim helper is plural (refreshTrimmedMessages), unlike 1.20.1/1.19.2.
@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError();
    }

    @Unique
    private boolean fpsmaster$betterChatBypass;

    // The chat line background is drawn by GuiGraphics.fill(...) with an ARGB int (vanilla alpha in the
    // top byte). Recolor the first fill in render() via the vanilla alpha.
    @ModifyArg(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0),
            index = 4
    )
    private int fpsmaster$betterChatBackgroundColor(int color) {
        if (!BetterChat.isActive()) {
            return color;
        }
        return BetterChat.backgroundColor(((color >>> 24) & 0xFF) / 255.0F);
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
}*/

//?} else {

/*import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    private void refreshTrimmedMessage() {
        throw new AssertionError();
    }

    @Unique
    private boolean fpsmaster$betterChatBypass;

    // 1.20.1 has no ARGB class; the chat line background is drawn by GuiGraphics.fill(...) with an
    // ARGB int (vanilla alpha in the top byte). Recolor the first fill in render() via the vanilla alpha.
    @ModifyArg(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0),
            index = 4
    )
    private int fpsmaster$betterChatBackgroundColor(int color) {
        if (!BetterChat.isActive()) {
            return color;
        }
        return BetterChat.backgroundColor(((color >>> 24) & 0xFF) / 255.0F);
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
            this.refreshTrimmedMessage();
        }

        this.fpsmaster$betterChatBypass = true;
        try {
            ((ChatComponent) (Object) this).addMessage(decorated, signature, tag);
        } finally {
            this.fpsmaster$betterChatBypass = false;
        }
        ci.cancel();
    }
}*/

//?}
