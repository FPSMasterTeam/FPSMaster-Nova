package top.fpsmaster.ui;

import net.minecraft.client.gui.GuiGraphics;
import top.fpsmaster.config.ConfigManager;

import java.awt.Color;

public final class MainMenuBackgroundRenderer {
    private MainMenuBackgroundRenderer() {
    }

    public static boolean shouldUseVanillaPanorama() {
        String background = ConfigManager.INSTANCE.getBackground();
        return background == null
                || background.isBlank()
                || background.equals("panorama")
                || background.startsWith("panorama_");
    }

    public static void render(GuiGraphics guiGraphics, int width, int height, float partialTick) {
        String background = ConfigManager.INSTANCE.getBackground();
        if ("classic".equals(background)) {
            int color = resolveClassicColor();
            guiGraphics.fill(0, 0, width, height, color);
            return;
        }

        if ("shader".equals(background)) {
            renderShaderLikeBackground(guiGraphics, width, height);
            return;
        }

        if ("custom".equals(background)) {
            renderCustomPlaceholder(guiGraphics, width, height);
            return;
        }

        guiGraphics.fillGradient(0, 0, width, height, 0xFF20242E, 0xFF10131A);
    }

    private static int resolveClassicColor() {
        float hue = clamp(ConfigManager.INSTANCE.getClassicBackgroundHue());
        float saturation = clamp(ConfigManager.INSTANCE.getClassicBackgroundSaturation());
        float brightness = clamp(ConfigManager.INSTANCE.getClassicBackgroundBrightness());
        float alpha = clamp(ConfigManager.INSTANCE.getClassicBackgroundAlpha());
        String mode = ConfigManager.INSTANCE.getClassicBackgroundMode();
        long now = System.nanoTime();
        float dynamicHue = (float) ((now / 1_000_000_000.0 / 6.0) % 1.0);

        if ("WAVE".equalsIgnoreCase(mode)) {
            alpha *= 0.35f + 0.65f * (float) ((Math.sin(now / 450_000_000.0) + 1.0) * 0.5);
        } else if ("CHROMA".equalsIgnoreCase(mode)) {
            hue = (hue + dynamicHue) % 1.0f;
        } else if ("RAINBOW".equalsIgnoreCase(mode)) {
            hue = dynamicHue;
        }

        int rgb = Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        return ((int) (alpha * 255.0f) << 24) | rgb;
    }

    private static void renderShaderLikeBackground(GuiGraphics guiGraphics, int width, int height) {
        long now = System.nanoTime();
        float phase = (float) ((now / 1_000_000_000.0) % 8.0 / 8.0);
        int top = argb(255, 20 + (int) (20 * phase), 35, 78 + (int) (48 * phase));
        int bottom = argb(255, 8, 12 + (int) (34 * phase), 32 + (int) (60 * (1.0f - phase)));
        guiGraphics.fillGradient(0, 0, width, height, top, bottom);
        guiGraphics.fill(0, 0, width, height / 3, 0x24000000);
    }

    private static void renderCustomPlaceholder(GuiGraphics guiGraphics, int width, int height) {
        guiGraphics.fillGradient(0, 0, width, height, 0xFF151A20, 0xFF252C34);
        guiGraphics.fill(0, height / 2 - 1, width, height / 2 + 1, 0x22FFFFFF);
    }

    private static float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (clamp255(alpha) << 24) | (clamp255(red) << 16) | (clamp255(green) << 8) | clamp255(blue);
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
