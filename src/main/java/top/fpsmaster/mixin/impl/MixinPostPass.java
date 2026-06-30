package top.fpsmaster.mixin.impl;

//? if >=1.21.5 {

import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.UniformValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.fpsmaster.module.impl.render.MotionBlur;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mixin(PostPass.class)
public class MixinPostPass {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Map<String, List<UniformValue>> fpsmaster$configureMotionBlur(Map<String, List<UniformValue>> uniforms) {
        List<UniformValue> values = uniforms.get("MotionBlurConfig");
        if (values == null || values.isEmpty()) {
            return uniforms;
        }

        Map<String, List<UniformValue>> modifiedUniforms = new LinkedHashMap<>(uniforms);
        List<UniformValue> modifiedValues = new ArrayList<>(values);
        modifiedValues.set(0, new UniformValue.FloatUniform(MotionBlur.factor()));
        modifiedUniforms.put("MotionBlurConfig", modifiedValues);
        return modifiedUniforms;
    }
}

//?}
