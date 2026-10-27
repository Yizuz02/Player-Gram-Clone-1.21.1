uniform sampler2D DiffuseSampler0;
uniform sampler2D MaskSampler;
uniform float VeilRenderTime;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 base = texture(DiffuseSampler0, texCoord).rgb;

    vec2 texel = 1.0 / vec2(textureSize(MaskSampler, 0));
    float center = texture(MaskSampler, texCoord).r;

    float edge = 0.0;
    edge += abs(center - texture(MaskSampler, texCoord + vec2(texel.x, 0.0)).r);
    edge += abs(center - texture(MaskSampler, texCoord - vec2(texel.x, 0.0)).r);
    edge += abs(center - texture(MaskSampler, texCoord + vec2(0.0, texel.y)).r);
    edge += abs(center - texture(MaskSampler, texCoord - vec2(0.0, texel.y)).r);
    edge = clamp(edge, 0.0, 1.0);

    float glow = 0.0;
    for (int i = 1; i <= 6; i++) {
        vec2 offset = texel * (float(i) * 2.0);
        glow += texture(MaskSampler, texCoord + vec2(offset.x, 0.0)).r;
        glow += texture(MaskSampler, texCoord - vec2(offset.x, 0.0)).r;
        glow += texture(MaskSampler, texCoord + vec2(0.0, offset.y)).r;
        glow += texture(MaskSampler, texCoord - vec2(0.0, offset.y)).r;
    }
    for (int i = 1; i <= 3; i++) {
        vec2 offset = texel * (float(i) * 3.0);
        glow += texture(MaskSampler, texCoord + vec2(offset.x, offset.y)).r;
        glow += texture(MaskSampler, texCoord + vec2(offset.x, -offset.y)).r;
        glow += texture(MaskSampler, texCoord + vec2(-offset.x, offset.y)).r;
        glow += texture(MaskSampler, texCoord + vec2(-offset.x, -offset.y)).r;
    }
    glow = clamp(glow / 36.0, 0.0, 1.0) * (1.0 - center);

    float pulse = 0.85 + 0.15 * sin(VeilRenderTime * 5.0);
    vec3 green = vec3(0.04, 1.0, 0.24);
    float outline = clamp(edge * 1.8 + glow * 1.15, 0.0, 1.0) * pulse;
    fragColor = vec4(base + green * outline * 1.55, 1.0);
}
