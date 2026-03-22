
uniform sampler2D m_ColorMap;
uniform sampler2D m_Mask;
uniform sampler2D m_Overlay;

varying vec2 texCoord;

varying vec4 vertColor;

void main(){

    vec4 color = texture2D(m_ColorMap, texCoord);
    vec4 mask = texture2D(m_Mask, texCoord);
    vec4 overlay = texture2D(m_Overlay, texCoord);

    color = mix(color, overlay, 1.0 - mask.a);

    if (overlay.a < 0.5) {
        discard;
    }

    gl_FragColor = color;
}