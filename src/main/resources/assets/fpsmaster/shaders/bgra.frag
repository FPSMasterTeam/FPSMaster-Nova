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
    texColor.rgb = texColor.bgr;
    float colorCoverage = max(max(texColor.r, texColor.g), texColor.b);
    if (texColor.a == 0.0 && colorCoverage == 0.0) {
        discard;
    }
    texColor.a = max(texColor.a, step(0.001, colorCoverage));
    fragColor = texColor * vertexColor * ColorModulator;
}
