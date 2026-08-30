package top.fpsmaster.mixin.impl;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import top.fpsmaster.multiplayer.ServerBrowser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds {@code updateOnlineServers} the arranged display order from {@link ServerBrowser}
 * (pinned player servers, then promoted servers, then the rest) instead of raw servers.dat order.
 *
 * <p>Only the loop's data source is swapped ({@code ServerList.size/get} redirects): the entries
 * are still vanilla {@code OnlineServerEntry} objects, so promoted rows ping, join and render like
 * any other server, and player rows keep their real ServerList-backed ServerData (delete/move still
 * operate on servers.dat). Promoted ServerData never enters {@code ServerList}, so nothing promoted
 * can leak into servers.dat through any vanilla save path.
 */
@Mixin(ServerSelectionList.class)
public class MixinServerSelectionList {
    @Unique
    private List<ServerData> fpsmaster$arranged;

    /**
     * Promoted rows keyed by their backend snapshot. Recreating the ServerData on every rearrange
     * would reset {@code pinged} and flash "Pinging..." on each pin/hide; reusing the instance
     * keeps the ping state. A changed backend entry (name/address) is a different key, so it gets
     * a fresh ServerData.
     */
    @Unique
    private static final Map<ServerBrowser.PromotedServer, ServerData> fpsmaster$promotedData = new HashMap<>();

    @Inject(method = "updateOnlineServers", at = @At("HEAD"))
    private void fpsmaster$arrangeServers(ServerList list, CallbackInfo ci) {
        List<ServerData> userServers = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            userServers.add(list.get(i));
        }
        fpsmaster$arranged = ServerBrowser.INSTANCE.arrange(
                userServers,
                data -> data.ip,
                MixinServerSelectionList::fpsmaster$promotedServerData);
    }

    @Redirect(
            method = "updateOnlineServers",
            at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ServerList.size()I")
    )
    private int fpsmaster$arrangedSize(ServerList list) {
        return fpsmaster$arranged.size();
    }

    @Redirect(
            method = "updateOnlineServers",
            at = @At(value = "INVOKE", target = "net/minecraft/client/multiplayer/ServerList.get(I)Lnet/minecraft/client/multiplayer/ServerData;")
    )
    private ServerData fpsmaster$arrangedEntry(ServerList list, int index) {
        return fpsmaster$arranged.get(index);
    }

    @Unique
    private static ServerData fpsmaster$promotedServerData(ServerBrowser.PromotedServer promoted) {
        return fpsmaster$promotedData.computeIfAbsent(promoted, key -> {
            //? if >=1.21 {
            return new ServerData(key.getName(), key.getAddress(), ServerData.Type.OTHER);
            //?} else {
            /*return new ServerData(key.getName(), key.getAddress(), false);*/
            //?}
        });
    }
}
