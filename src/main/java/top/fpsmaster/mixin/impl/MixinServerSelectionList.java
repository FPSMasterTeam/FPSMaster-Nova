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
import java.util.List;

/**
 * Feeds {@code updateOnlineServers} the arranged display order from {@link ServerBrowser}
 * (pinned servers first, then the rest) instead of raw servers.dat order.
 *
 * <p>Only the loop's data source is swapped ({@code ServerList.size/get} redirects): the entries
 * are still vanilla {@code OnlineServerEntry} objects backed by the real ServerList data, so
 * delete/move still operate on servers.dat.
 */
@Mixin(ServerSelectionList.class)
public class MixinServerSelectionList {
    @Unique
    private List<ServerData> fpsmaster$arranged;

    @Inject(method = "updateOnlineServers", at = @At("HEAD"))
    private void fpsmaster$arrangeServers(ServerList list, CallbackInfo ci) {
        top.fpsmaster.diagnostics.Smoke.mixin("server_selection_list");
        List<ServerData> userServers = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            userServers.add(list.get(i));
        }
        fpsmaster$arranged = ServerBrowser.INSTANCE.arrange(userServers, data -> data.ip);
        ServerBrowser.INSTANCE.rememberDisplayed(fpsmaster$arranged);
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
}
