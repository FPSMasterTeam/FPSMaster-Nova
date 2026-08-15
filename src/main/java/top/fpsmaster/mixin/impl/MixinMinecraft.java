package top.fpsmaster.mixin.impl;

import io.github.vlouboos.standaloneevent.api.StandaloneEventAPI;
import net.minecraft.client.Minecraft;
//? if >=1.21.5 {
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.Client;
import top.fpsmaster.event.client.MouseEvent;
import top.fpsmaster.event.client.TickEvent;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    //? if >=1.21.5 {
    @Shadow
    @Nullable
    public abstract ClientPacketListener getConnection();

    @Shadow
    public abstract @org.jetbrains.annotations.Nullable ServerData getCurrentServer();

    @Shadow
    @Nullable
    private IntegratedServer singleplayerServer;
    //?}

    @Inject(method = "runTick", at = @At("HEAD"))
    private void fpsmaster$tick(boolean renderLevel, CallbackInfo callback) {
        Client.tick();
    }

    //? if >=1.21.5 {
    @Inject(method = "tick", at = @At("HEAD"))
    private void fpsmaster$tick(CallbackInfo callback) {
        StandaloneEventAPI.getApi().call(new TickEvent());
    }

    //?}
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void fpsmaster$startAttack(CallbackInfoReturnable<Boolean> cir) {
        StandaloneEventAPI.getApi().call(new MouseEvent(0));
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void fpsmaster$startUseItem(CallbackInfo ci) {
        StandaloneEventAPI.getApi().call(new MouseEvent(1));
    }


    @Inject(method = "stop", at = @At("HEAD"))
    private void fpsmaster$shutdown(CallbackInfo callback) {
        Client.shutdown();
    }

    //? if >=1.21.5 {
    @Inject(method = "createTitle", at = @At(
            value = "INVOKE",
            target = "Ljava/lang/StringBuilder;append(Ljava/lang/String;)Ljava/lang/StringBuilder;",
            ordinal = 1),
            cancellable = true)
    private void getClientTitle(CallbackInfoReturnable<String> callback) {
        // Single version source: Client.VERSION (mod metadata <- gradle mod_version).
        StringBuilder titleBuilder = new StringBuilder("FPSMaster Nova ");
        titleBuilder.append(Client.VERSION);

        titleBuilder.append(" | ");

        titleBuilder.append(SharedConstants.getCurrentVersion().name());


        ClientPacketListener clientPlayNetworkHandler = this.getConnection();
        if (clientPlayNetworkHandler != null && clientPlayNetworkHandler.getConnection().isConnected()) {
            titleBuilder.append(" - ");
            ServerData serverInfo = this.getCurrentServer();
            if (this.singleplayerServer != null && !this.singleplayerServer.isPublished()) {
                titleBuilder.append(I18n.get("title.singleplayer"));
            } else if (serverInfo != null && serverInfo.isRealm()) {
                titleBuilder.append(I18n.get("title.multiplayer.realms"));
            } else if (this.singleplayerServer == null && (serverInfo == null || !serverInfo.isLan())) {
                titleBuilder.append(I18n.get("title.multiplayer.other"));
            } else {
                titleBuilder.append(I18n.get("title.multiplayer.lan"));
            }
        }

        callback.setReturnValue(titleBuilder.toString());
    }
    //?} else {
    /*@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void getClientTitle(CallbackInfoReturnable<String> callback) {
        callback.setReturnValue("FPSMaster Nova " + Client.VERSION + " | " + callback.getReturnValue());
    }
    *///?}
}
