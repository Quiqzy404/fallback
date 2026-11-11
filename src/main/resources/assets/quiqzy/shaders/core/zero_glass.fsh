#version 330

// Milchglas: unscharfes Spielbild aus Sampler0, darueber der getoente Verlauf.
// Shape: x = Radius, z = Gesamt-Deckkraft (0..1), w = Verlaufswinkel (Radiant)

uniform sampler2D Sampler0;

in vec4 color1;
in vec4 color2;
in vec2 local;
flat in vec2 halfSize;
flat in vec4 shape;

out vec4 fragColor;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float noise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

void main() {
    float d = roundedBox(local, halfSize, shape.x);
    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.0001);
    float coverage = clamp(0.5 - d / aa, 0.0, 1.0);
    if (coverage <= 0.0) discard;

    vec3 backdrop = texture(Sampler0, gl_FragCoord.xy / vec2(textureSize(Sampler0, 0))).rgb;

    vec2 dir = vec2(cos(shape.w), sin(shape.w));
    float extent = abs(dir.x) * halfSize.x + abs(dir.y) * halfSize.y;
    float t = clamp(dot(local, dir) / (2.0 * extent) + 0.5, 0.0, 1.0);
    vec4 tint = mix(color1, color2, t);

    vec3 color = mix(backdrop, tint.rgb, tint.a);
    color += (noise(gl_FragCoord.xy) - 0.5) * 1.5 / 255.0;
    fragColor = vec4(color, coverage * shape.z);
}
