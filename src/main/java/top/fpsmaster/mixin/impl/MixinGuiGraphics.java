package top.fpsmaster.mixin.impl;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.fpsmaster.mixin.interfaces.IGuiGraphics;
import top.fpsmaster.text.GlobalTextFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics implements IGuiGraphics {
    private static final Field FPSMASTER_SCISSOR_STACK_FIELD = fpsmaster$scissorStackField();

    @Accessor("guiRenderState")
    @Override
    public abstract GuiRenderState fpsmasterGuiRenderState();

    @Override
    public ScreenRectangle fpsmasterScissorArea() {
        try {
            Object scissorStack = FPSMASTER_SCISSOR_STACK_FIELD.get(this);
            return (ScreenRectangle) fpsmaster$scissorStackPeekMethod(scissorStack).invoke(scissorStack);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to read GuiGraphics scissor area", exception);
        }
    }

    private static Field fpsmaster$scissorStackField() {
        for (String name : new String[]{"scissorStack", "field_44659"}) {
            try {
                Field field = GuiGraphics.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new ExceptionInInitializerError("GuiGraphics scissorStack field not found");
    }

    private static Method fpsmaster$scissorStackPeekMethod(Object scissorStack) {
        for (Method method : scissorStack.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == ScreenRectangle.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("GuiGraphics scissorStack peek method not found");
    }

    @ModifyVariable(
            method = {
                    "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
                    "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V",
                    "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String fpsmaster$filterString(String text) {
        return GlobalTextFilter.filter(text);
    }

    @ModifyVariable(
            method = {
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V",
                    "drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component fpsmaster$filterComponent(Component component) {
        return GlobalTextFilter.filter(component);
    }

    @ModifyVariable(
            method = {
                    "drawWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V",
                    "drawWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIIIZ)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private FormattedText fpsmaster$filterFormattedText(FormattedText text) {
        return GlobalTextFilter.filter(text);
    }
}
