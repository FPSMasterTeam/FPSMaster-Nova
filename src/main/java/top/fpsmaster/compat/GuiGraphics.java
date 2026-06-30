package top.fpsmaster.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * 1.19.2 {@code GuiGraphics} shim. Minecraft added {@code net.minecraft.client.gui.GuiGraphics} in
 * 1.20; on 1.19.2 GUI drawing is done via static {@link GuiComponent} helpers + {@link Font} taking a
 * {@link PoseStack}. This class mirrors the subset of the 1.20.1 GuiGraphics API the codebase uses and
 * delegates to the 1.19.2 PoseStack-based calls, so HUD/UI render code compiles unchanged via a
 * Stonecutter import swap (only the import line differs per version). Excluded from non-1.19.2 source
 * sets (it references 1.19.2-only signatures).
 *
 * <p>Extends GuiComponent to reach the protected {@code fillGradient}. Item rendering needs special
 * care: 1.19.2's {@code renderGuiItem} ignores the current PoseStack, so we offset by the pose's
 * translation to honor HUD component transforms.
 */
public class GuiGraphics extends GuiComponent {
    private final PoseStack poseStack;

    public GuiGraphics(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public PoseStack pose() {
        return poseStack;
    }

    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    // --- rectangles ---
    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(poseStack, x1, y1, x2, y2, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int z, int color) {
        GuiComponent.fill(poseStack, x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        super.fillGradient(poseStack, x1, y1, x2, y2, colorFrom, colorTo);
    }

    // --- text ---
    public int drawString(Font font, String text, int x, int y, int color) {
        return font.draw(poseStack, text, x, y, color);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return font.draw(poseStack, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        font.drawShadow(poseStack, text, x - font.width(text) / 2f, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        font.drawShadow(poseStack, text, x - font.width(text) / 2f, y, color);
    }

    // --- textures ---
    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        net.minecraft.client.renderer.texture.AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tex.getId());
        GuiComponent.blit(poseStack, x, y, u, v, width, height);
    }

    public void blit(ResourceLocation texture, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
        GuiComponent.blit(poseStack, x, y, u, v, width, height, texWidth, texHeight);
    }

    // --- items (1.19.2 renderGuiItem ignores the PoseStack; offset by its translation) ---
    public void renderItem(ItemStack stack, int x, int y) {
        Matrix4f m = poseStack.last().pose();
        Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x + (int) m.m30(), y + (int) m.m31());
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        Matrix4f m = poseStack.last().pose();
        Minecraft.getInstance().getItemRenderer().renderAndDecorateFakeItem(stack, x + (int) m.m30(), y + (int) m.m31());
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        Matrix4f m = poseStack.last().pose();
        Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x + (int) m.m30(), y + (int) m.m31());
    }

    // --- scissor / flush ---
    public void enableScissor(int x1, int y1, int x2, int y2) {
        GuiComponent.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        GuiComponent.disableScissor();
    }

    public void flush() {
        // No batched buffer source to flush in the 1.19.2 immediate-mode path.
    }
}
