#version 330

// Schrift aus einem Distance-Field-Atlas. Pro Bildschirmpixel werden mehrere Punkte
// abgetastet, damit duenne Striche (Querstrich im "e", Gedankenstrich) nicht verschwinden.

uniform sampler2D Sampler0;

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

float cover(vec2 uv, float width, float edge) {
    return smoothstep(edge - width, edge + width, texture(Sampler0, uv, -1.0).r);
}

void main() {
    vec2 dx = dFdx(texCoord);
    vec2 dy = dFdy(texCoord);
    float pixel = length(vec2(length(vec2(dFdx(texture(Sampler0, texCoord, -1.0).r), 0.0)),
            length(vec2(dFdy(texture(Sampler0, texCoord, -1.0).r), 0.0))));
    float width = clamp(pixel * 0.3, 0.01, 0.12);

    // Kleine Schrift minimal kraeftiger, grosse bleibt wie gezeichnet
    float edge = 0.5 - min(pixel * 0.16, 0.05);

    float coverage = cover(texCoord + dx * 0.125 + dy * 0.375, width, edge);
    coverage += cover(texCoord - dx * 0.375 + dy * 0.125, width, edge);
    coverage += cover(texCoord + dx * 0.375 - dy * 0.125, width, edge);
    coverage += cover(texCoord - dx * 0.125 - dy * 0.375, width, edge);
    coverage *= 0.25;

    if (coverage <= 0.0) discard;
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
