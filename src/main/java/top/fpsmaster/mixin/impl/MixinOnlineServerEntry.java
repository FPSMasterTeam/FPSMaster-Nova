package top.fpsmaster.mixin.impl;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.fpsmaster.multiplayer.ServerBrowser;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's move arrows / Shift+Up/Down compute positions with {@code children().indexOf(this)},
 * i.e. in display order, and hand them straight to {@code ServerList.swap}. Once
 * {@link MixinServerSelectionList} inserts promoted rows and lifts pinned ones, display positions
 * no longer match servers.dat: moving the last row up threw IndexOutOfBoundsException, other moves
 * reordered the wrong servers. Translate both positions back to servers.dat and skip any swap that
 * involves a promoted row, which is not in servers.dat at all.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.multiplayer.ServerSelectionList$OnlineServerEntry")
public class MixinOnlineServerEntry {
    @Redirect(
            method = "swap(II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;swap(II)V")
    )
    private void fpsmaster$swapStoredPositions(ServerList list, int displayFrom, int displayTo) {
        List<ServerData> stored = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            stored.add(list.get(i));
        }
        int from = ServerBrowser.INSTANCE.storedIndex(stored, displayFrom);
        int to = ServerBrowser.INSTANCE.storedIndex(stored, displayTo);
        if (from >= 0 && to >= 0) {
            list.swap(from, to);
        }
    }
}
