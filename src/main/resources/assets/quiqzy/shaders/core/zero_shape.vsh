#version 330

// Kopie der Vanilla-Bloecke (kein moj_import: Pipelines werden schon beim Start kompiliert).
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec4 Color2;
in vec2 Local;
in vec2 HalfSize;
in vec4 Shape;
in vec4 Extra;

out vec4 color1;
out vec4 color2;
out vec2 local;
flat out vec2 halfSize;
flat out vec4 shape;
flat out vec4 extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    color1 = Color;
    color2 = Color2;
    local = Local;
    halfSize = HalfSize;
    shape = Shape;
    extra = Extra;
}
