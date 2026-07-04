#version 330 core

/* #moj_import <minecraft:dynamictransforms.glsl> */
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    // CEF delivers premultiplied BGRA. Swap R/B and keep the REAL (premultiplied) alpha so the
    // TRANSLUCENT_PREMULTIPLIED_ALPHA blend composites translucency correctly. A previous version
    // forced alpha up from colour coverage (max(a, step(0.001, coverage))), which turned every
    // coloured translucent pixel opaque — that is the "semi-transparent renders as solid" bug.
    texColor.rgb = texColor.bgr;
    if (texColor.a == 0.0) {
        discard;
    }
    fragColor = texColor * vertexColor * ColorModulator;
}
