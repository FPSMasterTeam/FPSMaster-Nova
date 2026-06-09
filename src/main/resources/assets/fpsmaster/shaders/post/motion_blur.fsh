#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionBlurConfig {
    float Factor;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 current = texture(InSampler, texCoord);
    vec4 previous = texture(PrevSampler, texCoord);
    fragColor = vec4(max(previous.rgb * vec3(Factor), current.rgb), 1.0);
}
