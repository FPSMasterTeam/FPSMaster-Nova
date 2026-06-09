package top.fpsmaster.mixin.interfaces;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.jetbrains.annotations.Nullable;

public interface IGuiGraphics {
    GuiRenderState fpsmasterGuiRenderState();

    @Nullable
    ScreenRectangle fpsmasterScissorArea();
}
