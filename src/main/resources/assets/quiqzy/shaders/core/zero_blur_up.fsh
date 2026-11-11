#version 330

// Dual-Kawase-Blur: Vergroesserungs-Schritt.

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord + vec2(-o.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-o.x, o.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, o.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x, o.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(o.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -o.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-o.x, -o.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}
