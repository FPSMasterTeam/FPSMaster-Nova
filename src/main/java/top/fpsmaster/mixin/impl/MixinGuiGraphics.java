package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.fpsmaster.mixin.interfaces.IGuiGraphics;
import top.fpsmaster.text.GlobalTextFilter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics implements IGuiGraphics {
    private static final Field FPSMASTER_SCISSOR_STACK_FIELD = fpsmaster$scissorStackField();

    @Accessor("guiRenderState")
    @Override
    public abstract GuiRenderState fpsmasterGuiRenderState();

    @Override
    @Nullable
    public ScreenRectangle fpsmasterScissorArea() {
        try {
            Object scissorStack = FPSMASTER_SCISSOR_STACK_FIELD.get(this);
            return (ScreenRectangle) fpsmaster$scissorStackPeekMethod(scissorStack).invoke(scissorStack);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IllegalStateException &&
                    "Scissor stack underflow".equals(cause.getMessage())) {
                return null;
            }
            throw new IllegalStateException("Failed to read GuiGraphics scissor area", exception);
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
        return text == null ? null : GlobalTextFilter.filter(text);
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

//?} else {

/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.fpsmaster.text.GlobalTextFilter;

// 1.20.1: GlobalTextFilter only. The IGuiGraphics accessor (guiRenderState/scissor) is a 1.21.5+ CEF
// render-bridge concern; the 1.20.1 browser path is immediate-mode and does not need it.
@Mixin(GuiGraphics.class)
public abstract class MixinGuiGraphics {
    @ModifyVariable(
            method = {
                    "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I",
                    "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private String fpsmaster$filterString(String text) {
        return text == null ? null : GlobalTextFilter.filter(text);
    }

    @ModifyVariable(
            method = {
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I",
                    "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
                    "drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Component fpsmaster$filterComponent(Component component) {
        return GlobalTextFilter.filter(component);
    }

    @ModifyVariable(
            method = "drawWordWrap(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/FormattedText;IIII)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private FormattedText fpsmaster$filterFormattedText(FormattedText text) {
        return GlobalTextFilter.filter(text);
    }
}*/

//?}
