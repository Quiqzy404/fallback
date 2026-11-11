#version 330

// ZERO Shape-Shader (Signed Distance Fields).
// Shape: x = Radius, y = Weichheit (Glow), z = Rahmenbreite, w = Verlaufswinkel (Radiant)
// Extra: x = Modus (0 Rechteck, 1 radialer Verlauf, 2 Bogen), y = Bogen-Start, z = Bogen-Laenge (Radiant), w = Ringbreite

in vec4 color1;
in vec4 color2;
in vec2 local;
flat in vec2 halfSize;
flat in vec4 shape;
flat in vec4 extra;

out vec4 fragColor;

const float PI = 3.14159265;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// Rauschen gegen Farbstufen in dunklen Verlaeufen
float noise(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

float edgeCoverage(float d) {
    // 1 Bildschirmpixel Kante, unabhaengig von GUI-Skalierung
    float aa = max(length(vec2(dFdx(d), dFdy(d))), 0.0001);
    return clamp(0.5 - d / aa, 0.0, 1.0);
}

void main() {
    float mode = extra.x;
    float t;
    float coverage;

    if (mode > 1.5) {
        // ---- Bogen / Ring mit runden Enden, Verlauf entlang des Bogens
        float width = extra.w;
        float ringR = min(halfSize.x, halfSize.y) - width * 0.5;
        float start = extra.y;
        float sweep = extra.z;
        float ang = atan(local.y, local.x);
        float rel = mod(ang - start, 2.0 * PI);
        float d;
        if (sweep >= 2.0 * PI - 0.001 || rel <= sweep) {
            d = abs(length(local) - ringR) - width * 0.5;
            t = sweep > 0.0 ? clamp(rel / sweep, 0.0, 1.0) : 0.0;
        } else {
            vec2 a = vec2(cos(start), sin(start)) * ringR;
            vec2 b = vec2(cos(start + sweep), sin(start + sweep)) * ringR;
            float da = length(local - a), db = length(local - b);
            d = min(da, db) - width * 0.5;
            t = da < db ? 0.0 : 1.0;
        }
        coverage = edgeCoverage(d);
    } else {
        float radius = shape.x;
        float soft = shape.y;
        float stroke = shape.z;

        float d = roundedBox(local, halfSize, radius);
        if (stroke > 0.0) d = abs(d + stroke * 0.5) - stroke * 0.5;

        if (soft > 0.0) {
            // weiches, lichtartiges Abklingen (gauss-aehnlich) statt linear
            float x = max(d + soft * 0.35, 0.0) / (soft * 0.85);
            coverage = exp(-x * x * 1.6);
            if (d > soft) coverage = 0.0;
        } else {
            coverage = edgeCoverage(d);
        }

        if (mode > 0.5) {
            // radialer Verlauf: Mitte = color1, Rand = color2
            t = clamp(length(local / max(halfSize, vec2(0.001))), 0.0, 1.0);
        } else {
            vec2 dir = vec2(cos(shape.w), sin(shape.w));
            float extent = abs(dir.x) * halfSize.x + abs(dir.y) * halfSize.y;
            t = clamp(dot(local, dir) / (2.0 * extent) + 0.5, 0.0, 1.0);
        }
    }
    if (coverage <= 0.0) discard;

    vec4 color = mix(color1, color2, t);
    color.rgb += (noise(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(color.rgb, color.a * coverage);
}
