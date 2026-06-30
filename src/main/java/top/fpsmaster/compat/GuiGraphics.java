package top.fpsmaster.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Matrix4f;
import java.lang.reflect.Field;

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
        super.blit(poseStack, x, y, u, v, width, height);
    }

    public void blit(ResourceLocation texture, int x, int y, float u, float v, int width, int height, int texWidth, int texHeight) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
        GuiComponent.blit(poseStack, x, y, u, v, width, height, texWidth, texHeight);
    }

    public void blit(int x, int y, int z, int width, int height, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
        GuiComponent.blit(poseStack, x, y, z, width, height, sprite);
    }

    // --- items (1.19.2 renderGuiItem ignores the PoseStack; offset by its translation) ---
    private static Field M03;
    private static Field M13;

    private int transX() { return (int) translation(M03 == null ? (M03 = field("m03")) : M03); }
    private int transY() { return (int) translation(M13 == null ? (M13 = field("m13")) : M13); }

    private static Field field(String name) {
        try { Field f = Matrix4f.class.getDeclaredField(name); f.setAccessible(true); return f; } catch (Exception e) { return null; }
    }

    private float translation(Field f) {
        if (f == null) return 0f;
        try { return f.getFloat(poseStack.last().pose()); } catch (Exception e) { return 0f; }
    }

    public void renderItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x + transX(), y + transY());
    }

    public void renderFakeItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderAndDecorateFakeItem(stack, x + transX(), y + transY());
    }

    public void renderItemDecorations(Font font, ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x + transX(), y + transY());
    }

    // --- scissor / flush ---
    // enableScissor/disableScissor are static on GuiComponent (can't be instance methods here); call
    // sites use GuiComponent.enableScissor directly on 1.19.2 (see DirectionTextHudComponent).

    public void flush() {
        // No batched buffer source to flush in the 1.19.2 immediate-mode path.
    }
}
