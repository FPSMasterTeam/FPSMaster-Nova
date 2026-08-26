package top.fpsmaster.mixin.impl;

import net.minecraft.client.multiplayer.ClientPacketListener;
//? if >=1.19.3 {
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
//?}
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
        //? if >=1.19.3 {
        if (packet.unsignedContent() != null) AutoGG.handleChat(packet.unsignedContent());
        //?} else {
        /*AutoGG.handleChat(packet.message().serverContent());*/
        //?}
    }

    //? if >=1.19.3 {
    @Inject(method = "handleDisguisedChat", at = @At("HEAD"))
    private void fpsmaster$handleDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
        AutoGG.handleChat(packet.message());
    }
    //?}
}
