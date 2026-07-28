#ifdef GL_ES
precision mediump float;
#endif

uniform vec4 m_FogColor;
uniform float m_FogDensity;
uniform float m_FogNearDistance;
uniform float m_FogFarDistance;
uniform vec4 m_RainColor;
uniform float m_RainIntensity;
uniform float m_RainSpeed;
uniform float m_RainAngle;
uniform float m_OverlayOpacity;
uniform vec4 m_SnowColor;
uniform float m_SnowIntensity;
uniform float m_SnowSpeed;
uniform float m_SnowFlakeSize;
uniform float m_WindDirection;
uniform float m_WindStrength;
uniform float m_WindGustiness;
uniform vec4 m_SkyTint;
uniform float m_SkyBrightness;
uniform float m_SkyHorizonBlend;
uniform vec4 m_AmbientColor;
uniform float m_AmbientIntensity;
uniform vec4 m_LightColor;
uniform float m_LightIntensity;
uniform float m_LightPitch;
uniform float m_LightYaw;
uniform float m_Time;

varying vec2 vUv;

const bool ENABLE_FOG = true;
const bool ENABLE_RAIN = false;
const bool ENABLE_SNOW = true;
const bool ENABLE_WIND = true;
const bool ENABLE_SKY = true;
const bool ENABLE_AMBIENT = true;
const bool ENABLE_LIGHT = false;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float rainStripe(vec2 uv, float time) {
    float angle = radians(m_RainAngle + (ENABLE_WIND ? m_WindDirection * 0.35 : 0.0));
    mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
    float windShift = ENABLE_WIND ? sin(time * (1.5 + m_WindGustiness * 2.5)) * m_WindStrength * 1.5 : 0.0;
    vec2 p = rot * ((uv * vec2(22.0, 14.0)) + vec2(windShift, time * m_RainSpeed * 7.5));
    vec2 cell = floor(p);
    float rnd = hash(cell);
    float streak = smoothstep(0.86, 0.98, fract(p.y + rnd));
    float lane = smoothstep(0.18, 0.02, abs(fract(p.x) - 0.5));
    return streak * lane;
}

float snowFlake(vec2 uv, float time) {
    float windAngle = radians(ENABLE_WIND ? m_WindDirection : 0.0);
    vec2 windVec = vec2(cos(windAngle), sin(windAngle)) * (ENABLE_WIND ? m_WindStrength * 0.4 : 0.0);
    vec2 p = uv * vec2(12.0, 9.0);
    p += vec2(time * windVec.x, time * (m_SnowSpeed * 1.8 + windVec.y));
    vec2 cell = floor(p);
    vec2 local = fract(p) - 0.5;
    float rnd = hash(cell);
    float gust = ENABLE_WIND ? sin(time * (2.0 + m_WindGustiness * 3.0) + rnd * 6.28318) * m_WindStrength * 0.25 : 0.0;
    local.x += gust;
    float size = mix(0.07, 0.22, clamp(m_SnowFlakeSize, 0.0, 1.5));
    float d = length(local + vec2(rnd - 0.5, sin(time + rnd * 8.0) * 0.03));
    return smoothstep(size, size * 0.35, d);
}

void main() {
    vec3 color = ENABLE_SKY ? (m_SkyTint.rgb * m_SkyBrightness * mix(0.65, 1.15, pow(1.0 - vUv.y, m_SkyHorizonBlend + 0.2))) : vec3(0.0);
    float alpha = 0.0;

    if (ENABLE_FOG) {
        float depthLike = mix(m_FogNearDistance, m_FogFarDistance, vUv.y);
        float fogFactor = smoothstep(m_FogNearDistance, m_FogFarDistance, depthLike);
        fogFactor = clamp(fogFactor * max(m_FogDensity, 0.01), 0.0, 1.0);
        color += m_FogColor.rgb * fogFactor;
        alpha = max(alpha, fogFactor * 0.85 * m_FogColor.a);
    }

    if (ENABLE_RAIN) {
        float streaks = rainStripe(vUv, m_Time);
        streaks += rainStripe(vUv + vec2(0.19, 0.11), m_Time * 1.1) * 0.7;
        streaks += rainStripe(vUv + vec2(-0.13, 0.27), m_Time * 0.92) * 0.5;
        streaks *= clamp(m_RainIntensity, 0.0, 1.5);
        color += m_RainColor.rgb * streaks;
        alpha = max(alpha, streaks * max(m_OverlayOpacity, 0.02) * m_RainColor.a);
    }

    if (ENABLE_SNOW) {
        float flakes = snowFlake(vUv, m_Time);
        flakes += snowFlake(vUv + vec2(0.21, 0.17), m_Time * 0.82) * 0.7;
        flakes += snowFlake(vUv + vec2(-0.11, 0.31), m_Time * 1.14) * 0.55;
        flakes *= clamp(m_SnowIntensity, 0.0, 1.5);
        color += m_SnowColor.rgb * flakes;
        alpha = max(alpha, flakes * 0.7 * m_SnowColor.a);
    }

    if (ENABLE_AMBIENT) {
        color = mix(color, color + m_AmbientColor.rgb * m_AmbientIntensity * 0.35, 0.6);
    }

    if (ENABLE_LIGHT) {
        float sunArc = 0.5 + 0.5 * sin(radians(m_LightPitch) + vUv.x * 2.3 + vUv.y * 1.7);
        color += m_LightColor.rgb * m_LightIntensity * sunArc * 0.18;
    }

    gl_FragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
