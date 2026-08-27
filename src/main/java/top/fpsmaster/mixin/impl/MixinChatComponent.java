package top.fpsmaster.mixin.impl;

//? if >=26 {

/*import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {
    @Shadow
    private List<GuiMessage> allMessages;

    @Unique
    private boolean fpsmaster$betterChatBypass;

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$decorateClient(Component component, CallbackInfo ci) {
        fpsmaster$decorate(component, null, null, true, ci);
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$decorateServer(Component component, CallbackInfo ci) {
        fpsmaster$decorate(component, null, null, false, ci);
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$decoratePlayer(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        fpsmaster$decorate(component, signature, tag, false, ci);
    }

    @Unique
    private void fpsmaster$decorate(Component component, MessageSignature signature, GuiMessageTag tag, boolean client, CallbackInfo ci) {
        if (this.fpsmaster$betterChatBypass || !BetterChat.isActive()) {
            return;
        }
        String rawText = component.getString();
        boolean folded = BetterChat.shouldFold(rawText);
        Component decorated = BetterChat.decorate(component, rawText, folded);
        if (!folded && decorated == component) {
            return;
        }
        if (folded && !this.allMessages.isEmpty()) {
            this.allMessages.remove(0);
            ((ChatComponent) (Object) this).rescaleChat();
        }
        this.fpsmaster$betterChatBypass = true;
        try {
            ChatComponent self = (ChatComponent) (Object) this;
            if (signature != null || tag != null) {
                self.addPlayerMessage(decorated, signature, tag);
            } else if (client) {
                self.addClientSystemMessage(decorated);
            } else {
                self.addServerSystemMessage(decorated);
            }
        } finally {
            this.fpsmaster$betterChatBypass = false;
        }
        ci.cancel();
    }
}*/

//?} elif >=1.21.9 {

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

//?} elif >=1.21.5 {

/*import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
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
import top.fpsmaster.module.impl.ui.ChatAvatars;

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

    @Redirect(
            method = "method_71991",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V")
    )
    private void fpsmaster$chatAvatars(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        boolean drew = ChatAvatars.drawFor(graphics, text, x, y, alpha);
        graphics.drawString(font, text, x + (drew ? ChatAvatars.indentPixels() : 0), y, color);
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;
import top.fpsmaster.module.impl.ui.ChatAvatars;

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

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I")
    )
    private int fpsmaster$chatAvatars(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        boolean drew = ChatAvatars.drawFor(graphics, text, x, y, alpha);
        return graphics.drawString(font, text, x + (drew ? ChatAvatars.indentPixels() : 0), y, color);
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

//?} elif >=1.20 {

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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;
import top.fpsmaster.module.impl.ui.ChatAvatars;

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

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I")
    )
    private int fpsmaster$chatAvatars(GuiGraphics graphics, net.minecraft.client.gui.Font font, net.minecraft.util.FormattedCharSequence text, int x, int y, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        boolean drew = ChatAvatars.drawFor(graphics, text, x, y, alpha);
        return graphics.drawString(font, text, x + (drew ? ChatAvatars.indentPixels() : 0), y, color);
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
}

*///?} else {

/*import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.ui.BetterChat;
import top.fpsmaster.module.impl.ui.ChatAvatars;

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

    // 1.19.2 render() takes a PoseStack and the chat line background is drawn by the static
    // GuiComponent.fill(PoseStack,…) — javac emits that invokestatic with ChatComponent as the owner,
    // which is the owner the injection point has to name. The colour is argument 5 (arg 0 is the pose).
    @ModifyArg(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;fill(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V",
                    ordinal = 0
            ),
            index = 5
    )
    private int fpsmaster$betterChatBackgroundColor(int color) {
        if (!BetterChat.isActive()) {
            return color;
        }
        return BetterChat.backgroundColor(((color >>> 24) & 0xFF) / 255.0F);
    }

    @Redirect(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I")
    )
    private int fpsmaster$chatAvatars(net.minecraft.client.gui.Font font, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.util.FormattedCharSequence text, float x, float y, int color) {
        float alpha = ((color >>> 24) & 0xFF) / 255.0F;
        boolean drew = ChatAvatars.drawFor(new top.fpsmaster.compat.GuiGraphics(pose), text, (int) x, (int) y, alpha);
        return font.draw(pose, text, x + (drew ? ChatAvatars.indentPixels() : 0), y, color);
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
}

*///?}
