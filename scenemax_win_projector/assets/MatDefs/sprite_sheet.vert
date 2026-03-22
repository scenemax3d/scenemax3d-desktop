uniform mat4 g_WorldViewProjectionMatrix;
uniform float m_SizeX;
uniform float m_SizeY;
uniform float m_Position;

in vec3 inPosition;
in vec2 inTexCoord;

out vec2 texCoord;

void main(){

    float t = m_Position;
    float tPointerY = 1.0 - ((floor(m_Position / m_SizeX)) / m_SizeY) - 1.0 / m_SizeY;
    float tPointerYOffset = (floor(t / m_SizeX)) / m_SizeY;
    float tPointerX = (t - (tPointerYOffset * m_SizeX * m_SizeY)) / m_SizeX;
    texCoord.x = inTexCoord.x / m_SizeX + tPointerX;
    texCoord.y = inTexCoord.y / m_SizeY + tPointerY;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}