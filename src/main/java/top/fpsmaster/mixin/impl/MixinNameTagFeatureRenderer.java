package top.fpsmaster.mixin.impl;

//? if >=26 {

/*import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(EntityRenderer.class)
public class MixinNameTagFeatureRenderer {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void fpsmaster$appendHealth(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (!LevelTag.isActive() || !LevelTag.Companion.getHealth().getValue()) {
            return;
        }
        if (!(entity instanceof Player) || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (state.nameTag == null || state.nameTag.getString().contains("[NPC]")) {
            return;
        }
        state.nameTag = Component.literal(state.nameTag.getString() + " " + Math.round(living.getHealth()) + " hp");
    }
}*/

//?} elif >=1.21.11 {

import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(NameTagFeatureRenderer.Storage.class)
public class MixinNameTagFeatureRenderer {
    @ModifyArg(
            method = "add",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeStorage$NameTagSubmit;<init>(Lorg/joml/Matrix4f;FFLnet/minecraft/network/chat/Component;IIID)V"
            ),
            index = 6
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}

//?} elif >=1.21.5 {

/*import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(EntityRenderer.class)
public class MixinNameTagFeatureRenderer {
    // The feature-renderer name-tag pipeline is 1.21.11+. On 1.21.5..1.21.8 the name tag is still drawn
    // immediately by EntityRenderer.renderNameTag, and the background colour is drawInBatch's
    // backgroundColor argument (index 8). Unlike 1.20.1/1.21.1, drawInBatch returns void here.
    @ModifyArg(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
            ),
            index = 8
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}

*///?} elif >=1.20 {

/*import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(EntityRenderer.class)
public class MixinNameTagFeatureRenderer {
    // On 1.20.1/1.21.1 the nametag background colour is the alpha-only int passed as Font.drawInBatch's
    // backgroundColor argument (index 8) inside EntityRenderer.renderNameTag. The second drawInBatch
    // call passes 0, which LevelTag.nameTagBackgroundColor returns unchanged, so matching both is safe.
    @ModifyArg(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"
            ),
            index = 8
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}

*///?} else {

/*import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import top.fpsmaster.module.impl.auxiliary.LevelTag;

@Mixin(EntityRenderer.class)
public class MixinNameTagFeatureRenderer {
    // 1.19.2 predates the JOML migration and Font.DisplayMode: Font.drawInBatch takes a
    // com.mojang.math.Matrix4f and a boolean (seeThrough) where later versions pass a Font$DisplayMode.
    // backgroundColor is still index 8.
    @ModifyArg(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;ZII)I"
            ),
            index = 8
    )
    private int fpsmaster$replaceNameTagBackgroundColor(int backgroundColor) {
        return LevelTag.nameTagBackgroundColor(backgroundColor);
    }
}

*///?}
