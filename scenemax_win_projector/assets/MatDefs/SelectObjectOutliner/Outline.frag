uniform sampler2D m_Texture;
uniform sampler2D m_OutlineDepthTexture;
uniform sampler2D m_DepthTexture;
varying vec2 texCoord;

uniform vec2 m_Resolution;
uniform vec4 m_OutlineColor;
uniform float m_OutlineWidth;

void main() {
	vec4 depth = texture2D(m_OutlineDepthTexture, texCoord);
	vec4 depth1 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(m_OutlineWidth,m_OutlineWidth))/m_Resolution);
	vec4 depth2 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(m_OutlineWidth,-m_OutlineWidth))/m_Resolution);
	vec4 depth3 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(-m_OutlineWidth,m_OutlineWidth))/m_Resolution);
	vec4 depth4 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(-m_OutlineWidth,-m_OutlineWidth))/m_Resolution);
	vec4 depth5 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(0.,m_OutlineWidth))/m_Resolution);
	vec4 depth6 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(0.,-m_OutlineWidth))/m_Resolution);
	vec4 depth7 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(m_OutlineWidth,0.))/m_Resolution);
	vec4 depth8 = texture2D(m_OutlineDepthTexture, ((texCoord*m_Resolution)+vec2(-m_OutlineWidth,0.))/m_Resolution);
	vec4 color = texture2D(m_Texture, texCoord);
	//如果是背景
	if(depth==vec4(0.) && (depth1 != depth || depth2 != depth || depth3 != depth || depth4 != depth||depth5 != depth || depth6 != depth || depth7 != depth || depth8 != depth)){
		gl_FragColor = m_OutlineColor;
	}else{
		gl_FragColor = color;
	}
	//debug
	//gl_FragColor = vec4(0.,(1.-ratio),0.,1.);
}