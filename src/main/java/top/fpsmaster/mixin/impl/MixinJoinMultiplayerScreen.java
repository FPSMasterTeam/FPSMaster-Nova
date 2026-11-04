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
 * Screen-side half of the server pinning (see {@link ServerBrowser}): adds the Pin/Unpin button.
 *
 * <p>The list-side half ({@link MixinServerSelectionList}) owns the display order.
 */
@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {
    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Shadow
    private ServerList servers;

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
    }

    @Inject(method = "onSelectedChange", at = @At("TAIL"))
    private void fpsmaster$updatePinOnSelect(CallbackInfo ci) {
        fpsmaster$updatePinButton();
    }

    @Unique
    private void fpsmaster$togglePin() {
        ServerSelectionList.Entry selected = this.serverSelectionList.getSelected();
        if (!(selected instanceof ServerSelectionList.OnlineServerEntry entry)) {
            return;
        }
        ServerData data = entry.getServerData();
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
            pinnable = true;
            pinned = ServerBrowser.INSTANCE.isPinned(address);
        }
        fpsmaster$pinButton.active = pinnable;
        fpsmaster$pinButton.setMessage(
                Component.literal(Language.Companion.get(pinned ? "multiplayer.unpin" : "multiplayer.pin")));
    }
}
