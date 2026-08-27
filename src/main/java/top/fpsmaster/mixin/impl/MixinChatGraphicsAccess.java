package top.fpsmaster.mixin.impl;

//? if >=1.21.11 {
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.fpsmaster.module.impl.ui.ChatAvatars;

@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess"
})
public abstract class MixinChatGraphicsAccess {
    @Unique
    private boolean fpsmaster$indented;
    //? if >=26 {
    /*@Shadow
    private net.minecraft.client.gui.GuiGraphicsExtractor graphics;
    *///?} else {
    @Shadow
    private net.minecraft.client.gui.GuiGraphics graphics;
    //?}

    @Inject(method = "handleMessage", at = @At("HEAD"))
    private void fpsmaster$drawChatAvatar(int y, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> cir) {
        this.fpsmaster$indented = false;
        boolean drew;
        //? if >=26 {
        /*drew = ChatAvatars.drawFor(new top.fpsmaster.compat.GuiGraphics26(this.graphics), text, 0, y, opacity);
        *///?} else {
        drew = ChatAvatars.drawFor(this.graphics, text, 0, y, opacity);
        //?}
        int indent = drew ? ChatAvatars.indentPixels() : 0;
        if (indent > 0) {
            ((ChatComponent.ChatGraphicsAccess) (Object) this).updatePose(pose -> pose.translate(indent, 0f));
            this.fpsmaster$indented = true;
        }
    }

    @Inject(method = "handleMessage", at = @At("RETURN"))
    private void fpsmaster$restoreChatAvatarIndent(int y, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> cir) {
        if (this.fpsmaster$indented) {
            int indent = ChatAvatars.indentPixels();
            ((ChatComponent.ChatGraphicsAccess) (Object) this).updatePose(pose -> pose.translate(-indent, 0f));
            this.fpsmaster$indented = false;
        }
    }
}
//?}
