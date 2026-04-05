#ifdef GL_ES
precision mediump float;
#endif

uniform vec4 m_Color;
uniform float m_AlphaDiscardThreshold;
uniform float m_BlackThreshold;
uniform float m_BrightnessPower;
uniform float m_EdgeSoftness;

varying vec2 texCoord;

#ifdef HAS_COLORMAP
uniform sampler2D m_ColorMap;
#endif

void main() {
    vec4 tex = vec4(1.0);
#ifdef HAS_COLORMAP
    tex = texture2D(m_ColorMap, texCoord);
#endif

    vec4 color = tex * m_Color;
    float brightness = max(max(tex.r, tex.g), tex.b);
    float keyedAlpha = smoothstep(m_BlackThreshold, min(1.0, m_BlackThreshold + max(0.0001, m_EdgeSoftness)), brightness);
    keyedAlpha = pow(keyedAlpha, max(0.0001, m_BrightnessPower));
    vec3 keyedRgb = max(tex.rgb - vec3(m_BlackThreshold), vec3(0.0));
    keyedRgb /= max(0.0001, 1.0 - m_BlackThreshold);
    color.rgb = keyedRgb * m_Color.rgb;
    color.a = keyedAlpha * m_Color.a;

    if (color.a <= m_AlphaDiscardThreshold) {
        discard;
    }

    gl_FragColor = color;
}
