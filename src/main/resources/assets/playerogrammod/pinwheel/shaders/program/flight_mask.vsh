layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 inflated = Position + normalize(Normal) * 0.02;
    gl_Position = ProjMat * ModelViewMat * vec4(inflated, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
