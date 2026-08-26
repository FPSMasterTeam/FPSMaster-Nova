#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Framebuffer row 0 is the bottom of the screen, but the GUI blit that composites the panel backdrop
// samples v=0 as the top. Storing the blurred frame already flipped keeps the composite a plain blit.
void main() {
    fragColor = texture(InSampler, vec2(texCoord.x, 1.0 - texCoord.y));
}
