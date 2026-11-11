#version 330

// Dual-Kawase-Blur: Verkleinerungs-Schritt.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord + vec2(-o.x, -o.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(-o.x, o.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x, o.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
