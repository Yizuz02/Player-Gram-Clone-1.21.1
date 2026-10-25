uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float VeilRenderTime;

in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    float progress = clamp(vertexColor.a, 0.0, 1.0);
    if (progress <= 0.001) {
        discard;
    }

    vec3 green = vec3(0.235, 1.0, 0.533);
    vec3 color = tex.rgb * vertexColor.rgb * ColorModulator.rgb;
    color = mix(overlayColor.rgb, color, overlayColor.a);

    float light = max(max(lightMapColor.r, lightMapColor.g), lightMapColor.b);
    color *= mix(0.55, 1.0, light);

    color = mix(color, green, 0.5);

    float pulse = 0.75 + 0.25 * sin(VeilRenderTime * 6.0 + texCoord0.y * 18.0);
    color += green * (1.0 - progress) * 0.9 * pulse;

    float alpha = tex.a * ColorModulator.a * progress;
    fragColor = vec4(color, alpha);
}
