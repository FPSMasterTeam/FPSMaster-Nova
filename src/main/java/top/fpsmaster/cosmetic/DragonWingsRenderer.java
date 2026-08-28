package top.fpsmaster.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.20 {
import org.joml.Matrix4f;
import org.joml.Vector4f;
//?} else {
/*import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
*///?}

public final class DragonWingsRenderer {
    private static final float UNIT = 1.0F / 16.0F;
    /**
     * 翼膜两面之间的半间距（模型单位，1.0 = 1 格）。
     *
     * <p>Edge 的原始模型把翼膜写成 {@code addBox("skin", -10, 0, 0.5, 10, 0, 10)}——高度 0 的盒子，
     * 上下两面完全共面；它靠 {@code glEnable(GL_CULL_FACE)} + 按镜像侧翻 {@code glCullFace}
     * 保证每次只有一面通过剔除。这边六个档的渲染类型剔除都是关的（&lt;26 是
     * {@code entityCutoutNoCull}；26.x 改名成 {@code RenderTypes.entityCutout}，它的
     * {@code ENTITY_CUTOUT} pipeline 带 {@code withCull(false)}），两面就都会光栅化。
     *
     * <p>26.x 这里<strong>不能</strong>用 {@code entitySolid}：{@code ENTITY_SOLID} 的构建链没有
     * {@code withCull(false)}，而 {@code RenderPipeline.Builder.build} 的兜底是
     * {@code cull.orElse(true)}——剔除会打开。{@code side == 1} 的镜像翅膀（{@code mirror = -1}
     * 让 {@code w} 变负）整体绕向翻转，翼骨盒子的近面会被剔掉、只剩远面；顺带 {@code ENTITY_SOLID}
     * 还缺 {@code ALPHA_CUTOUT}，自定义贴图的透明区会画成实心。
     *
     * <p>另有一处已知的分代不一致（不影响剔除，暂未拉平），按两条渲染路径分别记账：
     * <ul>
     *   <li><b>世界渲染</b>（{@code MixinWingsLayer}）：{@code <1.21.5} 与 {@code 1.21.5..1.21.10}
     *       走双参重载显式传 {@code false}，{@code affectsOutline} 关；1.21.11 与 26.x 走单参
     *       重载，等于 {@code true}（原版默认）。差别只在发光实体的描边后处理里，翅膀跟不跟着
     *       被描边。要拉平就是给后两档各补一个 {@code false}——
     *       {@code entityCutoutNoCull(Identifier, boolean)}（1.21.11）和
     *       {@code entityCutout(Identifier, boolean)}（26.2）都在。</li>
     *   <li><b>GUI 预览</b>：{@code <1.21.5} 走 {@code NativeCosmeticsScreen} 的双参 {@code false}，
     *       {@code 1.21.5..1.21.11} 与 26.x 走 {@code WingPreviewRenderer} 的单参重载（{@code true}）。
     *       这条<strong>不需要</strong>拉平，理由不在 PIP 而在缓冲：{@code affectsOutline} 只在几何
     *       走 {@code OutlineBufferSource}（世界渲染的发光实体通道）时才被读，两条预览路径拿到的
     *       都是普通 {@code BufferSource}——{@code <1.21.5} 直接画进
     *       {@code mc.renderBuffers().bufferSource()}（根本不走 PIP），1.21.5+ 的 PIP 持有的也是
     *       {@code MultiBufferSource.BufferSource}，26.x 的 PIP 更是连
     *       {@code PreparedFrame.executeOutline()} 都不在调用链里。所以这条差异在预览路径上
     *       没有可观测后果。</li>
     * </ul>
     *
     * <p>两个 quad 顶点顺序相差 3 位，MC 把 quad 拆成 (0,1,2)+(0,2,3) 两个三角形，于是两面沿
     * <em>相反</em>的对角线切分，共面处插值出来的深度逐像素不同 → z-fighting。又因为
     * {@code animation} 每帧都在变，胜出的一面逐帧重排，肉眼看就是翅膀一直在闪。
     *
     * <p>1/10 个纹理像素，看不出来，但远高于深度缓冲的分辨率。
     */
    private static final float SKIN_SPLIT = UNIT * 0.1F;

    private DragonWingsRenderer() {
    }

    public static void render(PoseStack.Pose pose, VertexConsumer buffer, int packedLight) {
        float animation = (System.currentTimeMillis() % 1000L) / 1000.0F * (float) Math.PI * 2.0F;
        int color = 0xFFFFFFFF;
        for (int side = 0; side < 2; side++) {
            float mirror = side == 0 ? 1.0F : -1.0F;
            //? if >=1.20 {
            Matrix4f wing = new Matrix4f()
                    .translate(-2.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ((float) Math.toRadians(20.0F) * mirror)
                    .rotateY((float) Math.toRadians(20.0F) * mirror + (float) Math.sin(animation) * 0.4F * mirror)
                    .rotateX((float) Math.toRadians(-80.0F) - (float) Math.cos(animation) * 0.2F);
            Matrix4f tip = new Matrix4f(wing)
                    .translate(-10.0F * UNIT * mirror, 0.0F, 0.0F)
                    .rotateZ(-((float) Math.sin(animation + 2.0F) + 0.5F) * 0.75F * mirror);
            //?} else {
            /*Matrix4f wing = Matrix4f.createTranslateMatrix(-2.0F * UNIT * mirror, 0.0F, 0.0F);
            wing.multiply(Vector3f.ZP.rotationDegrees(20.0F * mirror));
            wing.multiply(Vector3f.YP.rotation(0.34906585F * mirror + (float) Math.sin(animation) * 0.4F * mirror));
            wing.multiply(Vector3f.XP.rotation(-1.3962634F - (float) Math.cos(animation) * 0.2F));
            Matrix4f tip = wing.copy();
            tip.multiplyWithTranslation(-10.0F * UNIT * mirror, 0.0F, 0.0F);
            tip.multiply(Vector3f.ZP.rotation(-((float) Math.sin(animation + 2.0F) + 0.5F) * 0.75F * mirror));
            *///?}
            bone(buffer, pose, wing, -10.0F * UNIT * mirror, -UNIT, -UNIT, 10.0F * UNIT * mirror, 2 * UNIT, 2 * UNIT, 0, 0, 10F / 30F, 2F / 30F, color, packedLight);
            membrane(buffer, pose, wing, -10.0F * UNIT * mirror, 0, 0.5F * UNIT, 10.0F * UNIT * mirror, 10 * UNIT, 0, 0, 8F / 30F, 10F / 30F, 18F / 30F, color, packedLight);
            bone(buffer, pose, tip, -10.0F * UNIT * mirror, -0.5F * UNIT, -0.5F * UNIT, 10.0F * UNIT * mirror, UNIT, UNIT, 0, 5F / 30F, 10F / 30F, 6F / 30F, color, packedLight);
            membrane(buffer, pose, tip, -10.0F * UNIT * mirror, 0, 0.5F * UNIT, 10.0F * UNIT * mirror, 10 * UNIT, 0, 0, 18F / 30F, 10F / 30F, 28F / 30F, color, packedLight);
        }
    }

    private static void bone(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float w, float h, float d, float u0, float v0, float u1, float v1, int color, int light) {
        float x2 = x + w, y2 = y + h, z2 = z + d;
        quad(b, p, m, x, y, z2, x2, y, z2, x2, y2, z2, x, y2, z2, u0, v0, u1, v1, 0, 0, 1, color, light);
        quad(b, p, m, x2, y, z, x, y, z, x, y2, z, x2, y2, z, u0, v0, u1, v1, 0, 0, -1, color, light);
        quad(b, p, m, x, y, z, x, y, z2, x, y2, z2, x, y2, z, u0, v0, u1, v1, -1, 0, 0, color, light);
        quad(b, p, m, x2, y, z2, x2, y, z, x2, y2, z, x2, y2, z2, u0, v0, u1, v1, 1, 0, 0, color, light);
        quad(b, p, m, x, y, z, x2, y, z, x2, y, z2, x, y, z2, u0, v0, u1, v1, 0, -1, 0, color, light);
        quad(b, p, m, x, y2, z2, x2, y2, z2, x2, y2, z, x, y2, z, u0, v0, u1, v1, 0, 1, 0, color, light);
    }

    private static void membrane(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float w, float length, float d, float u0, float v0, float u1, float v1, int color, int light) {
        float x2 = x + w, z2 = z + length + d;
        // 各自沿法线方向让开半个间距，别让两面落在同一平面上。见 SKIN_SPLIT。
        float yDown = y - SKIN_SPLIT, yUp = y + SKIN_SPLIT;
        quad(b, p, m, x, yDown, z, x2, yDown, z, x2, yDown, z2, x, yDown, z2, u0, v0, u1, v1, 0, -1, 0, color, light);
        quad(b, p, m, x, yUp, z2, x2, yUp, z2, x2, yUp, z, x, yUp, z, u0, v1, u1, v0, 0, 1, 0, color, light);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u0, float v0, float u1, float v1, float nx, float ny, float nz, int color, int light) {
        vertex(b, p, m, x1, y1, z1, u0, v1, nx, ny, nz, color, light);
        vertex(b, p, m, x2, y2, z2, u1, v1, nx, ny, nz, color, light);
        vertex(b, p, m, x3, y3, z3, u1, v0, nx, ny, nz, color, light);
        vertex(b, p, m, x4, y4, z4, u0, v0, nx, ny, nz, color, light);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose p, Matrix4f m, float x, float y, float z, float u, float v, float nx, float ny, float nz, int color, int light) {
        Vector4f position = new Vector4f(x, y, z, 1.0F);
        //? if >=1.20 {
        m.transform(position);
        //?} else {
        /*position.transform(m);
        *///?}
        //? if >=1.21 {
        b.addVertex(p, position.x(), position.y(), position.z()).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        //?} else {
        /*b.vertex(p.pose(), position.x(), position.y(), position.z()).color(color).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
        *///?}
    }
}
