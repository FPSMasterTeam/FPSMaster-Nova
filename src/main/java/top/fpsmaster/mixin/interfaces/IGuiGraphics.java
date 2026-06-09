package top.fpsmaster.mixin.interfaces;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiRenderState;

public interface IGuiGraphics {
    GuiRenderState fpsmasterGuiRenderState();

    ScreenRectangle fpsmasterScissorArea();
}
