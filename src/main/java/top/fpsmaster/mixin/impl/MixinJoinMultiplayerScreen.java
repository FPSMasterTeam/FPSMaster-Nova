package top.fpsmaster.mixin.impl;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.multiplayer.ServerBrowser;
import top.fpsmaster.translation.Language;

/**
 * Screen-side half of the promoted/pinned server browser (see {@link ServerBrowser}): kicks the
 * async fetch of the promoted list, adds the Pin/Unpin button for player-added servers, blocks
 * "Edit" on promoted rows (they are not in servers.dat, editing would change a phantom), and turns
 * "Delete" on a promoted row into a local, persistent hide instead of a no-op ServerList removal.
 *
 * <p>The list-side half ({@link MixinServerSelectionList}) owns the display order.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Shadow
    private ServerList servers;

    @Shadow
    private Button editButton;

    @Unique
    private Button fpsmaster$pinButton;

    protected MixinJoinMultiplayerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fpsmaster$initServerBrowser(CallbackInfo ci) {
        top.fpsmaster.diagnostics.Smoke.mixin("join_multiplayer_screen");
        Component label = Component.literal(Language.Companion.get("multiplayer.pin"));
        //? if >=1.20 {
        fpsmaster$pinButton = this.addRenderableWidget(
                Button.builder(label, button -> fpsmaster$togglePin()).bounds(this.width - 54, 6, 50, 20).build());
        //?} else {
        /*fpsmaster$pinButton = this.addRenderableWidget(
                new Button(this.width - 54, 6, 50, 20, label, button -> fpsmaster$togglePin()));
        *///?}
        fpsmaster$updatePinButton();
        // The fetch callback runs on the HTTP executor; hop to the client thread and only touch the
        // list if this screen is still the one on display (the fetch can outlive it).
        ServerBrowser.INSTANCE.refreshAsync(() -> this.minecraft.execute(() -> {
            //? if >=26 {
            /*if (this.minecraft.gui.screen() != (Object) this) {
            *///?} else {
            if (this.minecraft.screen != (Object) this) {
            //?}
                return;
            }
            this.serverSelectionList.updateOnlineServers(this.servers);
        }));
    }

    /**
     * Deleting a promoted row must not fall through to vanilla: the promoted ServerData is not in
     * {@code servers}, so vanilla's remove+save would silently do nothing and the row would come
     * back on refresh. Record the hide locally (persisted) and rebuild the list instead.
     */
    @Inject(method = "deleteCallback", at = @At("HEAD"), cancellable = true)
    private void fpsmaster$hidePromotedInsteadOfDelete(boolean confirmed, CallbackInfo ci) {
        ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
        if (!(selected instanceof ServerSelectionList.OnlineServerEntry entry)
                || !ServerBrowser.INSTANCE.shownAsPromoted(entry.getServerData().ip)) {
            return;
        }
        if (confirmed) {
            ServerBrowser.INSTANCE.hidePromoted(entry.getServerData().ip);
            this.serverSelectionList.setSelected(null);
            this.serverSelectionList.updateOnlineServers(this.servers);
        }
        // Same screen restore vanilla does after the confirm dialog.
        //? if >=26 {
        /*this.minecraft.gui.setScreen(this);
        *///?} else {
        this.minecraft.setScreen(this);
        //?}
        ci.cancel();
    }

    @Inject(method = "onSelectedChange", at = @At("TAIL"))
    private void fpsmaster$adjustButtonsForPromoted(CallbackInfo ci) {
        ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
        if (selected instanceof ServerSelectionList.OnlineServerEntry entry
                && ServerBrowser.INSTANCE.shownAsPromoted(entry.getServerData().ip)) {
            this.editButton.active = false;
        }
        fpsmaster$updatePinButton();
    }

    @Unique
    private void fpsmaster$togglePin() {
        ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
        if (!(selected instanceof ServerSelectionList.OnlineServerEntry entry)) {
            return;
        }
        ServerData data = entry.getServerData();
        if (ServerBrowser.INSTANCE.shownAsPromoted(data.ip)) {
            return;
        }
        ServerBrowser.INSTANCE.togglePin(data.ip);
        this.serverSelectionList.updateOnlineServers(this.servers);
        fpsmaster$reselect(data);
    }

    /**
     * {@code updateOnlineServers} rebuilds every entry object, so the previous selection points at
     * an entry that is no longer displayed. Re-select the row backing the same ServerData; the
     * {@code setSelected} call also re-runs {@code onSelectedChange}, refreshing the button states.
     */
    @Unique
    private void fpsmaster$reselect(ServerData data) {
        for (ServerSelectionList.Entry child : this.serverSelectionList.children()) {
            if (child instanceof ServerSelectionList.OnlineServerEntry entry && entry.getServerData() == data) {
                this.serverSelectionList.setSelected(child);
                return;
            }
        }
        this.serverSelectionList.setSelected(null);
    }

    @Unique
    private void fpsmaster$updatePinButton() {
        // onSelectedChange can run from vanilla init before our init tail created the button.
        if (fpsmaster$pinButton == null) {
            return;
        }
        ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
        boolean pinnable = false;
        boolean pinned = false;
        if (selected instanceof ServerSelectionList.OnlineServerEntry entry) {
            String address = entry.getServerData().ip;
            pinnable = !ServerBrowser.INSTANCE.shownAsPromoted(address);
            pinned = pinnable && ServerBrowser.INSTANCE.isPinned(address);
        }
        fpsmaster$pinButton.active = pinnable;
        fpsmaster$pinButton.setMessage(
                Component.literal(Language.Companion.get(pinned ? "multiplayer.unpin" : "multiplayer.pin")));
    }
}
