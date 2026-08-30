package top.fpsmaster.mixin.impl;

//? if <1.20 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
*///?}
//? if >=1.21.11 && <26 {
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
//?}
//? if >=1.20 && <1.21.11 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.multiplayer.ServerBrowser;
import top.fpsmaster.translation.Language;

/**
 * Draws the row badge that tells the two special server kinds apart: gold "Featured" on promoted
 * rows, aqua "Pinned" on player-pinned rows. Text-only, right-aligned just before the status icon,
 * drawn with the same per-version text call the row itself uses - no new visual language.
 */
@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinServerSelectionListEntry {
    @Shadow
    @Final
    private ServerData serverData;

    //? if >=26 {
    /*@Inject(method = "extractContent", at = @At("TAIL"))
    private void fpsmaster$drawBadge(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        String tag = fpsmaster$badgeText();
        if (tag == null) {
            return;
        }
        AbstractSelectionList.Entry<?> self = (AbstractSelectionList.Entry<?>) (Object) this;
        Font font = Minecraft.getInstance().font;
        guiGraphics.text(font, tag, self.getContentRight() - 19 - font.width(tag), self.getContentY() + 1, fpsmaster$badgeColor());
    }
    *///?} elif >=1.21.11 {
    @Inject(method = "renderContent", at = @At("TAIL"))
    private void fpsmaster$drawBadge(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        String tag = fpsmaster$badgeText();
        if (tag == null) {
            return;
        }
        AbstractSelectionList.Entry<?> self = (AbstractSelectionList.Entry<?>) (Object) this;
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, tag, self.getContentRight() - 19 - font.width(tag), self.getContentY() + 1, fpsmaster$badgeColor());
    }
    //?} elif >=1.20 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void fpsmaster$drawBadge(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        String tag = fpsmaster$badgeText();
        if (tag == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, tag, left + width - 19 - font.width(tag), top + 1, fpsmaster$badgeColor());
    }
    *///?} else {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void fpsmaster$drawBadge(PoseStack poseStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick, CallbackInfo ci) {
        String tag = fpsmaster$badgeText();
        if (tag == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        GuiComponent.drawString(poseStack, font, tag, left + width - 19 - font.width(tag), top + 1, fpsmaster$badgeColor());
    }
    *///?}

    @Unique
    private String fpsmaster$badgeText() {
        if (ServerBrowser.INSTANCE.shownAsPromoted(serverData.ip)) {
            return Language.Companion.get("multiplayer.badge.promoted");
        }
        if (ServerBrowser.INSTANCE.isPinned(serverData.ip)) {
            return Language.Companion.get("multiplayer.badge.pinned");
        }
        return null;
    }

    /** Valid only while {@link #fpsmaster$badgeText()} is non-null. Full alpha, per HUD color rules. */
    @Unique
    private int fpsmaster$badgeColor() {
        return ServerBrowser.INSTANCE.shownAsPromoted(serverData.ip) ? 0xFFFFAA00 : 0xFF55FFFF;
    }
}
