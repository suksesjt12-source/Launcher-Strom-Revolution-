#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;

layout(push_constant) uniform DrawPush {
    mat4 mvp;
    vec4 tint;
} drawData;

layout(location = 0) out vec3 vertexColor;

void main() {
    gl_Position = drawData.mvp * vec4(inPosition, 1.0);
    vec3 lightDirection = normalize(vec3(-0.35, 0.80, 0.45));
    float diffuse = max(dot(normalize(inNormal), lightDirection), 0.0);
    float lighting = 0.36 + diffuse * 0.64;
    vertexColor = drawData.tint.rgb * lighting;
}
