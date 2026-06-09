package top.fpsmaster.mixin.impl;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.AutoGG;

@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @Inject(method = "handleSystemChat", at = @At("HEAD"))
    private void fpsmaster$handleSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            AutoGG.handleChat(packet.content());
        }
    }

    @Inject(method = "handlePlayerChat", at = @At("HEAD"))
    private void fpsmaster$handlePlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
        if (packet.unsignedContent() != null) {
            AutoGG.handleChat(packet.unsignedContent());
        }
    }

    @Inject(method = "handleDisguisedChat", at = @At("HEAD"))
    private void fpsmaster$handleDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        AutoGG.handleChat(packet.message());
    }
}
