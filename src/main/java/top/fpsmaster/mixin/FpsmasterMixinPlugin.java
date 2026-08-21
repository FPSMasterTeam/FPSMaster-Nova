package top.fpsmaster.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Skips Nova mixins that share exclusive injection points with ViaFabricPlus.
 * Plus and Plus visuals already own those client visuals when present.
 */
public class FpsmasterMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_LIVING_ENTITY = "MixinLivingEntity";
    private static final String VIAFABRICPLUS_MOD_ID = "viafabricplus";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!isExactMixin(mixinClassName, MIXIN_LIVING_ENTITY)) {
            return true;
        }
        // ViaFabricPlus visuals already redirects LivingEntity.tick -> Mth.abs.
        // Two @Redirects cannot share that invoke; Plus requires its own.
        return !FabricLoader.getInstance().isModLoaded(VIAFABRICPLUS_MOD_ID);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    static boolean isExactMixin(String mixinClassName, String simpleName) {
        return mixinClassName.equals(simpleName) || mixinClassName.endsWith("." + simpleName);
    }
}
