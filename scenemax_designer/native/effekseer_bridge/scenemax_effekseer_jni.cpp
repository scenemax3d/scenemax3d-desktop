#include <jni.h>

#include <Effekseer.h>
#include <EffekseerRendererGL.h>
#include <EffekseerRendererGL/EffekseerRendererGL.GLExtension.h>

#include <memory>
#include <sstream>
#include <string>
#include <utility>
#include <vector>
#include <cstring>

#ifndef GL_READ_FRAMEBUFFER
#define GL_READ_FRAMEBUFFER 0x8CA8
#endif

#ifndef GL_DRAW_FRAMEBUFFER
#define GL_DRAW_FRAMEBUFFER 0x8CA9
#endif

#ifndef GL_READ_FRAMEBUFFER_BINDING
#define GL_READ_FRAMEBUFFER_BINDING 0x8CAA
#endif

#ifndef GL_DRAW_FRAMEBUFFER_BINDING
#define GL_DRAW_FRAMEBUFFER_BINDING 0x8CA6
#endif

#ifndef GL_SHADING_LANGUAGE_VERSION
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C
#endif

#ifndef GL_ACTIVE_TEXTURE
#define GL_ACTIVE_TEXTURE 0x84E0
#endif

#ifndef GL_FRAMEBUFFER_COMPLETE
#define GL_FRAMEBUFFER_COMPLETE 0x8CD5
#endif

namespace {

using GlBlitFramebufferProc = void (APIENTRY*)(GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLint, GLbitfield, GLenum);
using GlCheckFramebufferStatusProc = GLenum (APIENTRY*)(GLenum);

struct PreviewContext {
    int32_t squareMaxCount = 0;
    Effekseer::Backend::GraphicsDeviceRef graphicsDevice;
    EffekseerRendererGL::RendererRef renderer;
    Effekseer::ManagerRef manager;
    Effekseer::EffectRef effect;
    std::u16string effectPath;
    Effekseer::Handle handle = -1;
    bool loop = true;
    float playbackSpeed = 1.0f;
    float elapsedSeconds = 0.0f;
    bool renderFailed = false;
    int32_t renderFailureCount = 0;
    Effekseer::Vector3D viewerPosition = {0.0f, 0.0f, 10.0f};
    Effekseer::Vector3D targetLocation = {0.0f, 0.0f, 0.0f};
    int32_t lastDrawCalls = 0;
    int32_t lastDrawVertices = 0;
    EffekseerRendererGL::OpenGLDeviceType deviceType = EffekseerRendererGL::OpenGLDeviceType::OpenGL3;
    bool helperInitialized = false;
    GLuint offscreenFramebuffer = 0;
    GLuint offscreenColorTexture = 0;
    int32_t offscreenWidth = 0;
    int32_t offscreenHeight = 0;
    int32_t lastRequestedWidth = 0;
    int32_t lastRequestedHeight = 0;
    int32_t stableSizeFrames = 0;
    std::string lastOffscreenStatus = "not-created";
    std::string glVersion;
    std::string glShadingLanguageVersion;
    std::string glRenderer;
    std::string glVendor;
    void* createGlContext = nullptr;
    void* loadGlContext = nullptr;
    void* renderGlContext = nullptr;
    void* lastObservedRenderContext = nullptr;
    uint32_t createThreadId = 0;
    uint32_t loadThreadId = 0;
    uint32_t renderThreadId = 0;
    int32_t stableRenderContextFrames = 0;
    bool useQuadCompositeFallback = false;
    bool preferDefaultFramebufferComposite = false;
    bool compositeEnabled = false;
};

GlBlitFramebufferProc g_glBlitFramebuffer = nullptr;
GlCheckFramebufferStatusProc g_glCheckFramebufferStatus = nullptr;

void destroyOffscreenFramebuffer(PreviewContext* ctx);
void captureLoadBinding(PreviewContext* ctx);

template <typename T>
jlong toHandle(std::unique_ptr<T> context) {
    return reinterpret_cast<jlong>(context.release());
}

template <typename T>
T* fromHandle(jlong handle) {
    return reinterpret_cast<T*>(handle);
}

Effekseer::Matrix44 matrixFromArray(JNIEnv* env, jfloatArray values) {
    Effekseer::Matrix44 result;
    if (values == nullptr || env->GetArrayLength(values) < 16) {
        result.Indentity();
        return result;
    }
    jfloat* raw = env->GetFloatArrayElements(values, nullptr);
    for (int row = 0; row < 4; row++) {
        for (int col = 0; col < 4; col++) {
            result.Values[row][col] = raw[col * 4 + row];
        }
    }
    env->ReleaseFloatArrayElements(values, raw, JNI_ABORT);
    return result;
}

Effekseer::Matrix43 matrix43FromArray(JNIEnv* env, jfloatArray values) {
    Effekseer::Matrix43 result;
    result.Indentity();
    if (values == nullptr || env->GetArrayLength(values) < 16) {
        return result;
    }
    jfloat* raw = env->GetFloatArrayElements(values, nullptr);
    for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 4; col++) {
            result.Value[row][col] = raw[col * 4 + row];
        }
    }
    env->ReleaseFloatArrayElements(values, raw, JNI_ABORT);
    return result;
}

struct SavedGlState {
    GLint framebuffer = 0;
    GLint readFramebuffer = 0;
    GLint drawFramebuffer = 0;
    GLint viewport[4] = {0, 0, 0, 0};
    GLint scissorBox[4] = {0, 0, 0, 0};
    GLint currentProgram = 0;
    GLint activeTexture = GL_TEXTURE0;
    GLint textureBinding2D = 0;
    GLint arrayBufferBinding = 0;
    GLint elementArrayBufferBinding = 0;
    GLint vertexArrayBinding = 0;
    GLint blendSrcRgb = GL_ONE;
    GLint blendDstRgb = GL_ZERO;
    GLint blendEquation = GL_FUNC_ADD;
    GLint depthFunc = GL_LESS;
    GLint cullFaceMode = GL_BACK;
    GLint frontFace = GL_CCW;
    GLboolean depthMask = GL_TRUE;
    GLboolean colorMask[4] = {GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE};
    GLboolean blendEnabled = GL_FALSE;
    GLboolean depthTestEnabled = GL_FALSE;
    GLboolean cullFaceEnabled = GL_FALSE;
    GLboolean scissorTestEnabled = GL_FALSE;
};

SavedGlState captureGlState() {
    SavedGlState state;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &state.framebuffer);
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &state.readFramebuffer);
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &state.drawFramebuffer);
    glGetIntegerv(GL_VIEWPORT, state.viewport);
    glGetIntegerv(GL_SCISSOR_BOX, state.scissorBox);
    glGetIntegerv(GL_CURRENT_PROGRAM, &state.currentProgram);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &state.activeTexture);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &state.textureBinding2D);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &state.arrayBufferBinding);
    glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, &state.elementArrayBufferBinding);
    glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &state.vertexArrayBinding);
    glGetIntegerv(GL_BLEND_SRC_RGB, &state.blendSrcRgb);
    glGetIntegerv(GL_BLEND_DST_RGB, &state.blendDstRgb);
    glGetIntegerv(GL_BLEND_EQUATION, &state.blendEquation);
    glGetIntegerv(GL_DEPTH_FUNC, &state.depthFunc);
    glGetIntegerv(GL_CULL_FACE_MODE, &state.cullFaceMode);
    glGetIntegerv(GL_FRONT_FACE, &state.frontFace);
    glGetBooleanv(GL_DEPTH_WRITEMASK, &state.depthMask);
    glGetBooleanv(GL_COLOR_WRITEMASK, state.colorMask);
    state.blendEnabled = glIsEnabled(GL_BLEND);
    state.depthTestEnabled = glIsEnabled(GL_DEPTH_TEST);
    state.cullFaceEnabled = glIsEnabled(GL_CULL_FACE);
    state.scissorTestEnabled = glIsEnabled(GL_SCISSOR_TEST);
    return state;
}

void restoreGlState(const SavedGlState& state) {
    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_READ_FRAMEBUFFER, state.readFramebuffer);
    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, state.framebuffer);
    glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);
    glScissor(state.scissorBox[0], state.scissorBox[1], state.scissorBox[2], state.scissorBox[3]);
    if (state.scissorTestEnabled) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
    if (state.cullFaceEnabled) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
    if (state.depthTestEnabled) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
    if (state.blendEnabled) glEnable(GL_BLEND); else glDisable(GL_BLEND);
    glDepthMask(state.depthMask);
    glColorMask(state.colorMask[0], state.colorMask[1], state.colorMask[2], state.colorMask[3]);
    glDepthFunc(state.depthFunc);
    glCullFace(state.cullFaceMode);
    glFrontFace(state.frontFace);
    EffekseerRendererGL::GLExt::glBlendEquation(state.blendEquation);
    glBlendFunc(state.blendSrcRgb, state.blendDstRgb);
    EffekseerRendererGL::GLExt::glUseProgram(state.currentProgram);
    EffekseerRendererGL::GLExt::glActiveTexture(state.activeTexture);
    EffekseerRendererGL::GLExt::glBindVertexArray(static_cast<GLuint>(state.vertexArrayBinding));
    EffekseerRendererGL::GLExt::glBindBuffer(GL_ARRAY_BUFFER, static_cast<GLuint>(state.arrayBufferBinding));
    EffekseerRendererGL::GLExt::glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, static_cast<GLuint>(state.elementArrayBufferBinding));
    glBindTexture(GL_TEXTURE_2D, state.textureBinding2D);
}

void setupModules(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
    ctx->manager->SetSpriteRenderer(ctx->renderer->CreateSpriteRenderer());
    ctx->manager->SetRibbonRenderer(ctx->renderer->CreateRibbonRenderer());
    ctx->manager->SetRingRenderer(ctx->renderer->CreateRingRenderer());
    ctx->manager->SetTrackRenderer(ctx->renderer->CreateTrackRenderer());
    ctx->manager->SetModelRenderer(ctx->renderer->CreateModelRenderer());
    ctx->manager->SetTextureLoader(ctx->renderer->CreateTextureLoader());
    ctx->manager->SetModelLoader(ctx->renderer->CreateModelLoader());
    ctx->manager->SetMaterialLoader(ctx->renderer->CreateMaterialLoader());
    ctx->manager->SetCurveLoader(Effekseer::MakeRefPtr<Effekseer::CurveLoader>());
}

bool initializeRendererObjects(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return false;
    }
    ctx->graphicsDevice = EffekseerRendererGL::CreateGraphicsDevice(ctx->deviceType);
    ctx->renderer = ctx->graphicsDevice != nullptr
            ? EffekseerRendererGL::Renderer::Create(ctx->graphicsDevice, ctx->squareMaxCount)
            : nullptr;
    ctx->manager = Effekseer::Manager::Create(ctx->squareMaxCount);
    if (ctx->renderer == nullptr || ctx->manager == nullptr) {
        return false;
    }
    setupModules(ctx);
    return true;
}

void clearRuntimeObjects(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
    ctx->handle = -1;
    ctx->effect.Reset();
    ctx->manager.Reset();
    ctx->renderer.Reset();
    ctx->graphicsDevice.Reset();
    destroyOffscreenFramebuffer(ctx);
    ctx->lastDrawCalls = 0;
    ctx->lastDrawVertices = 0;
    ctx->loadGlContext = nullptr;
    ctx->loadThreadId = 0;
    ctx->stableRenderContextFrames = 0;
    ctx->useQuadCompositeFallback = false;
    ctx->preferDefaultFramebufferComposite = true;
    ctx->lastRequestedWidth = 0;
    ctx->lastRequestedHeight = 0;
    ctx->stableSizeFrames = 0;
    ctx->lastOffscreenStatus = "not-created";
}

bool loadEffectOnCurrentContext(PreviewContext* ctx) {
    if (ctx == nullptr || ctx->effectPath.empty()) {
        return false;
    }
    if (ctx->renderer == nullptr || ctx->manager == nullptr) {
        if (!initializeRendererObjects(ctx)) {
            return false;
        }
    }
    ctx->effect.Reset();
    ctx->handle = -1;
    ctx->effect = Effekseer::Effect::Create(ctx->manager, reinterpret_cast<const char16_t*>(ctx->effectPath.c_str()));
    if (ctx->effect == nullptr) {
        return false;
    }
    ctx->handle = ctx->manager->Play(ctx->effect, 0.0f, 0.0f, 0.0f);
    if (ctx->handle < 0) {
        ctx->effect.Reset();
        return false;
    }
    ctx->manager->SetTargetLocation(ctx->handle, ctx->targetLocation);
    captureLoadBinding(ctx);
    return true;
}

void clearGlErrors() {
    while (glGetError() != GL_NO_ERROR) {
    }
}

void logGlError(const char* stage) {
    GLenum error = glGetError();
    if (error == GL_NO_ERROR) {
        return;
    }
    std::fprintf(stderr, "SceneMax Effekseer JNI GL error at %s: 0x%x\n",
                 stage,
                 static_cast<unsigned int>(error));
}

bool compositeOffscreenTexture(PreviewContext* ctx,
                               GLint targetFramebuffer,
                               GLint viewportX,
                               GLint viewportY,
                               int32_t viewportWidth,
                               int32_t viewportHeight) {
    if (ctx == nullptr || ctx->offscreenColorTexture == 0) {
        return false;
    }

    if (ctx->preferDefaultFramebufferComposite) {
        targetFramebuffer = 0;
    }

    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, targetFramebuffer);
    auto framebufferStatus = g_glCheckFramebufferStatus != nullptr
            ? g_glCheckFramebufferStatus(GL_FRAMEBUFFER)
            : GL_FRAMEBUFFER_COMPLETE;
    if (framebufferStatus != GL_FRAMEBUFFER_COMPLETE) {
        std::fprintf(stderr,
                     "SceneMax Effekseer JNI composite target incomplete: fbo=%d status=0x%x\n",
                     targetFramebuffer,
                     static_cast<unsigned int>(framebufferStatus));
        if (targetFramebuffer != 0) {
            EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, 0);
            framebufferStatus = g_glCheckFramebufferStatus != nullptr
                    ? g_glCheckFramebufferStatus(GL_FRAMEBUFFER)
                    : GL_FRAMEBUFFER_COMPLETE;
            std::fprintf(stderr,
                         "SceneMax Effekseer JNI composite fallback default framebuffer status: 0x%x\n",
                         static_cast<unsigned int>(framebufferStatus));
            if (framebufferStatus != GL_FRAMEBUFFER_COMPLETE) {
                return false;
            }
            targetFramebuffer = 0;
            ctx->preferDefaultFramebufferComposite = true;
        } else {
            return false;
        }
    }
    glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_DEPTH_TEST);
    glDepthMask(GL_FALSE);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    EffekseerRendererGL::GLExt::glUseProgram(0);
    glMatrixMode(GL_PROJECTION);
    glPushMatrix();
    glLoadIdentity();
    glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
    glMatrixMode(GL_MODELVIEW);
    glPushMatrix();
    glLoadIdentity();
    glMatrixMode(GL_TEXTURE);
    glPushMatrix();
    glLoadIdentity();
    EffekseerRendererGL::GLExt::glActiveTexture(GL_TEXTURE0);
    glEnable(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, ctx->offscreenColorTexture);
    glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    glBegin(GL_QUADS);
    glTexCoord2f(0.0f, 0.0f); glVertex2f(0.0f, 0.0f);
    glTexCoord2f(1.0f, 0.0f); glVertex2f(1.0f, 0.0f);
    glTexCoord2f(1.0f, 1.0f); glVertex2f(1.0f, 1.0f);
    glTexCoord2f(0.0f, 1.0f); glVertex2f(0.0f, 1.0f);
    glEnd();
    glBindTexture(GL_TEXTURE_2D, 0);
    glDisable(GL_TEXTURE_2D);
    glMatrixMode(GL_TEXTURE);
    glPopMatrix();
    glMatrixMode(GL_MODELVIEW);
    glPopMatrix();
    glMatrixMode(GL_PROJECTION);
    glPopMatrix();
    glMatrixMode(GL_MODELVIEW);
    glDepthMask(GL_TRUE);
    auto error = glGetError();
    if (error != GL_NO_ERROR) {
        std::fprintf(stderr, "SceneMax Effekseer JNI GL error at quad-composite: 0x%x\n",
                     static_cast<unsigned int>(error));
        return false;
    }
    return true;
}

void* resolveGlProcAddress(const char* name) {
#if _WIN32
    void* proc = reinterpret_cast<void*>(wglGetProcAddress(name));
    if (proc != nullptr && proc != reinterpret_cast<void*>(0x1) && proc != reinterpret_cast<void*>(0x2)
            && proc != reinterpret_cast<void*>(0x3) && proc != reinterpret_cast<void*>(-1)) {
        return proc;
    }
    HMODULE module = GetModuleHandleA("opengl32.dll");
    if (module == nullptr) {
        module = LoadLibraryA("opengl32.dll");
    }
    return module != nullptr ? reinterpret_cast<void*>(GetProcAddress(module, name)) : nullptr;
#else
    return nullptr;
#endif
}

bool initializeBridgeGlHelpers() {
    if (g_glBlitFramebuffer != nullptr && g_glCheckFramebufferStatus != nullptr) {
        return true;
    }
    g_glBlitFramebuffer = reinterpret_cast<GlBlitFramebufferProc>(resolveGlProcAddress("glBlitFramebuffer"));
    g_glCheckFramebufferStatus = reinterpret_cast<GlCheckFramebufferStatusProc>(resolveGlProcAddress("glCheckFramebufferStatus"));
    return g_glBlitFramebuffer != nullptr;
}

std::string readGlString(GLenum name) {
    const GLubyte* value = glGetString(name);
    return value != nullptr ? reinterpret_cast<const char*>(value) : "unavailable";
}

void captureGlContextInfo(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
    ctx->glVersion = readGlString(GL_VERSION);
    ctx->glShadingLanguageVersion = readGlString(GL_SHADING_LANGUAGE_VERSION);
    ctx->glRenderer = readGlString(GL_RENDERER);
    ctx->glVendor = readGlString(GL_VENDOR);
}

void captureCreateBinding(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
#if _WIN32
    ctx->createGlContext = wglGetCurrentContext();
    ctx->createThreadId = static_cast<uint32_t>(GetCurrentThreadId());
#endif
}

void captureLoadBinding(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
#if _WIN32
    ctx->loadGlContext = wglGetCurrentContext();
    ctx->loadThreadId = static_cast<uint32_t>(GetCurrentThreadId());
#endif
}

void captureRenderBinding(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
#if _WIN32
    ctx->renderGlContext = wglGetCurrentContext();
    ctx->renderThreadId = static_cast<uint32_t>(GetCurrentThreadId());
#endif
}

void destroyOffscreenFramebuffer(PreviewContext* ctx) {
    if (ctx == nullptr) {
        return;
    }
    if (ctx->offscreenColorTexture != 0) {
        glDeleteTextures(1, &ctx->offscreenColorTexture);
        ctx->offscreenColorTexture = 0;
    }
    if (ctx->offscreenFramebuffer != 0) {
        EffekseerRendererGL::GLExt::glDeleteFramebuffers(1, &ctx->offscreenFramebuffer);
        ctx->offscreenFramebuffer = 0;
    }
    ctx->offscreenWidth = 0;
    ctx->offscreenHeight = 0;
}

bool ensureOffscreenFramebuffer(PreviewContext* ctx, int32_t width, int32_t height) {
    if (ctx == nullptr || width <= 0 || height <= 0) {
        if (ctx != nullptr) {
            ctx->lastOffscreenStatus = "invalid-size";
        }
        return false;
    }

    if (ctx->offscreenFramebuffer != 0 && ctx->offscreenColorTexture != 0
            && ctx->offscreenWidth >= width && ctx->offscreenHeight >= height) {
        ctx->lastOffscreenStatus = "ready";
        return true;
    }

    int32_t backingWidth = ctx->offscreenWidth > 0 ? ctx->offscreenWidth : 1024;
    int32_t backingHeight = ctx->offscreenHeight > 0 ? ctx->offscreenHeight : 1024;
    while (backingWidth < width) {
        backingWidth *= 2;
    }
    while (backingHeight < height) {
        backingHeight *= 2;
    }

    GLint previousTexture = 0;
    GLint previousFramebuffer = 0;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &previousTexture);
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &previousFramebuffer);

    GLuint newColorTexture = 0;
    GLuint newFramebuffer = 0;

    glGenTextures(1, &newColorTexture);
    if (newColorTexture == 0) {
        ctx->lastOffscreenStatus = "glGenTextures returned 0";
        return false;
    }

    glBindTexture(GL_TEXTURE_2D, newColorTexture);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, backingWidth, backingHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    EffekseerRendererGL::GLExt::glGenFramebuffers(1, &newFramebuffer);
    if (newFramebuffer == 0) {
        glBindTexture(GL_TEXTURE_2D, previousTexture);
        glDeleteTextures(1, &newColorTexture);
        ctx->lastOffscreenStatus = "glGenFramebuffers returned 0";
        return false;
    }

    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, newFramebuffer);
    EffekseerRendererGL::GLExt::glFramebufferTexture2D(
        GL_FRAMEBUFFER,
        GL_COLOR_ATTACHMENT0,
        GL_TEXTURE_2D,
        newColorTexture,
        0);
    const GLenum drawBuffers[] = {GL_COLOR_ATTACHMENT0};
    EffekseerRendererGL::GLExt::glDrawBuffers(1, drawBuffers);

    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);
    glBindTexture(GL_TEXTURE_2D, previousTexture);

    auto error = glGetError();
    if (error != GL_NO_ERROR) {
        if (newFramebuffer != 0) {
            EffekseerRendererGL::GLExt::glDeleteFramebuffers(1, &newFramebuffer);
        }
        if (newColorTexture != 0) {
            glDeleteTextures(1, &newColorTexture);
        }
        std::ostringstream status;
        status << "gl error 0x" << std::hex << static_cast<unsigned int>(error);
        ctx->lastOffscreenStatus = status.str();
        return false;
    }

    destroyOffscreenFramebuffer(ctx);
    ctx->offscreenFramebuffer = newFramebuffer;
    ctx->offscreenColorTexture = newColorTexture;
    ctx->offscreenWidth = backingWidth;
    ctx->offscreenHeight = backingHeight;
    ctx->lastOffscreenStatus = "ready";
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeCreatePreviewContext
  (JNIEnv*, jclass, jint squareMaxCount) {
    auto context = std::make_unique<PreviewContext>();
    context->squareMaxCount = squareMaxCount;
    captureGlContextInfo(context.get());
    captureCreateBinding(context.get());
    context->deviceType = EffekseerRendererGL::OpenGLDeviceType::OpenGL2;
    context->helperInitialized = initializeBridgeGlHelpers();
    context->preferDefaultFramebufferComposite = true;
    return toHandle(std::move(context));
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeDestroyPreviewContext
  (JNIEnv*, jclass, jlong handle) {
    std::unique_ptr<PreviewContext> context(fromHandle<PreviewContext>(handle));
    destroyOffscreenFramebuffer(context.get());
}

JNIEXPORT jboolean JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeLoadEffect
  (JNIEnv* env, jclass, jlong handle, jstring effectPath, jstring) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr || effectPath == nullptr) {
        return JNI_FALSE;
    }

    const jchar* chars = env->GetStringChars(effectPath, nullptr);
    jsize len = env->GetStringLength(effectPath);
    std::u16string path(reinterpret_cast<const char16_t*>(chars), static_cast<size_t>(len));
    env->ReleaseStringChars(effectPath, chars);

    context->effectPath = std::move(path);
    context->renderFailed = false;
    context->renderFailureCount = 0;
    context->elapsedSeconds = 0.0f;
    context->lastDrawCalls = 0;
    context->lastDrawVertices = 0;
    context->effect.Reset();
    context->handle = -1;
    context->lastObservedRenderContext = nullptr;
    context->stableRenderContextFrames = 0;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeUnloadEffect
  (JNIEnv*, jclass, jlong handle) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr) {
        return;
    }
    if (context->manager != nullptr && context->handle >= 0) {
        context->manager->StopEffect(context->handle);
        context->handle = -1;
    }
    context->effectPath.clear();
    context->effect.Reset();
    context->elapsedSeconds = 0.0f;
    context->renderFailed = false;
    context->renderFailureCount = 0;
    context->lastDrawCalls = 0;
    context->lastDrawVertices = 0;
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetLooping
  (JNIEnv*, jclass, jlong handle, jboolean loop) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr) {
        context->loop = (loop == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetPlaybackSpeed
  (JNIEnv*, jclass, jlong handle, jfloat speed) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr) {
        context->playbackSpeed = speed;
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetTargetLocation
  (JNIEnv*, jclass, jlong handle, jfloat x, jfloat y, jfloat z) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr) {
        context->targetLocation = {x, y, z};
        if (context->manager != nullptr && context->handle >= 0) {
            context->manager->SetTargetLocation(context->handle, context->targetLocation);
        }
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetEffectLocation
  (JNIEnv*, jclass, jlong handle, jfloat x, jfloat y, jfloat z) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr && context->manager != nullptr && context->handle >= 0) {
        context->manager->SetLocation(context->handle, x, y, z);
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetDynamicInput
  (JNIEnv*, jclass, jlong handle, jint index, jfloat value) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr && context->manager != nullptr && context->handle >= 0) {
        context->manager->SetDynamicInput(context->handle, index, value);
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativePlayEffect
  (JNIEnv*, jclass, jlong handle) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr) {
        return;
    }
    if (context->effect == nullptr && !context->effectPath.empty()) {
        loadEffectOnCurrentContext(context);
    }
    if (context->manager == nullptr || context->effect == nullptr) {
        return;
    }
    if (context->handle >= 0) {
        context->manager->StopEffect(context->handle);
    }
    context->handle = context->manager->Play(context->effect, 0.0f, 0.0f, 0.0f);
    if (context->handle >= 0) {
        context->manager->SetTargetLocation(context->handle, context->targetLocation);
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeStopEffect
  (JNIEnv*, jclass, jlong handle) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr && context->manager != nullptr && context->handle >= 0) {
        context->manager->StopEffect(context->handle);
    }
}

JNIEXPORT jboolean JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeIsEffectPlaying
  (JNIEnv*, jclass, jlong handle) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr || context->manager == nullptr || context->handle < 0) {
        return JNI_FALSE;
    }
    return context->manager->Exists(context->handle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetEffectTransform
  (JNIEnv* env, jclass, jlong handle, jfloatArray worldTransform) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr && context->manager != nullptr && context->handle >= 0) {
        context->manager->SetBaseMatrix(context->handle, matrix43FromArray(env, worldTransform));
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetCompositeEnabled
  (JNIEnv*, jclass, jlong handle, jboolean enabled) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context != nullptr) {
        context->compositeEnabled = (enabled == JNI_TRUE);
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeSetCamera
  (JNIEnv* env, jclass, jlong handle, jfloatArray viewMatrix, jfloatArray projectionMatrix, jfloatArray cameraPosition) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr) {
        return;
    }
    if (context->renderer != nullptr) {
        context->renderer->SetCameraMatrix(matrixFromArray(env, viewMatrix));
        context->renderer->SetProjectionMatrix(matrixFromArray(env, projectionMatrix));
    }
    if (cameraPosition != nullptr && env->GetArrayLength(cameraPosition) >= 3) {
        jfloat* raw = env->GetFloatArrayElements(cameraPosition, nullptr);
        context->viewerPosition = {raw[0], raw[1], raw[2]};
        env->ReleaseFloatArrayElements(cameraPosition, raw, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeUpdate
  (JNIEnv*, jclass, jlong handle, jfloat deltaSeconds) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr || context->manager == nullptr) {
        return;
    }

    if (context->effect != nullptr && context->loop &&
        (context->handle < 0 || !context->manager->Exists(context->handle))) {
        context->handle = context->manager->Play(context->effect, 0.0f, 0.0f, 0.0f);
        if (context->handle >= 0) {
            context->manager->SetTargetLocation(context->handle, context->targetLocation);
        }
    }

    Effekseer::Manager::UpdateParameter updateParameter;
    updateParameter.DeltaFrame = deltaSeconds * 60.0f * context->playbackSpeed;
    context->manager->Update(updateParameter);
    context->elapsedSeconds += deltaSeconds * context->playbackSpeed;
}

JNIEXPORT void JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeRender
  (JNIEnv*, jclass, jlong handle, jint width, jint height) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr) {
        return;
    }
    if (width <= 0 || height <= 0) {
        context->lastOffscreenStatus = "render skipped: invalid size";
        return;
    }
    if (context->lastRequestedWidth != width || context->lastRequestedHeight != height) {
        context->lastRequestedWidth = width;
        context->lastRequestedHeight = height;
        context->stableSizeFrames = 1;
        context->lastOffscreenStatus = "size changed, waiting";
        return;
    }
    context->stableSizeFrames += 1;
    captureRenderBinding(context);
    if (context->renderGlContext != context->lastObservedRenderContext) {
        context->lastObservedRenderContext = context->renderGlContext;
        context->stableRenderContextFrames = 1;
    } else if (context->renderGlContext != nullptr) {
        context->stableRenderContextFrames += 1;
    }
    if (context->effect == nullptr && !context->effectPath.empty()) {
        if (context->stableRenderContextFrames < 2) {
            return;
        }
        if (!loadEffectOnCurrentContext(context)) {
            context->renderFailed = true;
            context->renderFailureCount += 1;
            return;
        }
    }
    if (context->effect != nullptr && context->loadGlContext != nullptr
            && context->renderGlContext != nullptr
            && context->loadGlContext != context->renderGlContext) {
        clearRuntimeObjects(context);
        context->renderFailed = true;
        context->renderFailureCount += 1;
        return;
    }
    if (context->renderer == nullptr || context->manager == nullptr || context->effect == nullptr) {
        return;
    }
    if (!context->helperInitialized) {
        context->renderFailed = true;
        context->renderFailureCount += 1;
        return;
    }
    const bool compositeEnabled = context->compositeEnabled;
    const bool renderDirectToDefaultFramebuffer = compositeEnabled && context->preferDefaultFramebufferComposite;
    if (!renderDirectToDefaultFramebuffer) {
        if (context->stableSizeFrames < 2) {
            context->lastOffscreenStatus = "waiting for stable size";
            return;
        }
        if (!ensureOffscreenFramebuffer(context, width, height)) {
            context->renderFailed = true;
            context->renderFailureCount += 1;
            destroyOffscreenFramebuffer(context);
            return;
        }
    }

    clearGlErrors();

#if _WIN32
    __try
    {
#endif
        SavedGlState savedState = captureGlState();

        const GLint effekseerFramebuffer = renderDirectToDefaultFramebuffer ? 0 : context->offscreenFramebuffer;
        const GLint renderViewportX = renderDirectToDefaultFramebuffer ? savedState.viewport[0] : 0;
        const GLint renderViewportY = renderDirectToDefaultFramebuffer ? savedState.viewport[1] : 0;
        const GLint renderViewportWidth = renderDirectToDefaultFramebuffer ? savedState.viewport[2] : width;
        const GLint renderViewportHeight = renderDirectToDefaultFramebuffer ? savedState.viewport[3] : height;
        EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, effekseerFramebuffer);
        logGlError(renderDirectToDefaultFramebuffer ? "bind-default-direct" : "bind-offscreen");
        glViewport(renderViewportX, renderViewportY, renderViewportWidth, renderViewportHeight);
        logGlError(renderDirectToDefaultFramebuffer ? "viewport-direct" : "viewport-offscreen");
        glDisable(GL_SCISSOR_TEST);
        logGlError("disable-scissor");
        if (!renderDirectToDefaultFramebuffer) {
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            logGlError("clear-color");
            glClear(GL_COLOR_BUFFER_BIT);
            logGlError("clear-color-buffer");
        }

        context->renderer->SetTime(context->elapsedSeconds);
        Effekseer::Manager::LayerParameter layerParameter;
        layerParameter.ViewerPosition = context->viewerPosition;
        context->manager->SetLayerParameter(0, layerParameter);
        context->renderer->ResetRenderState();
        logGlError("reset-render-state");
        context->renderer->BeginRendering();
        logGlError("begin-rendering");
        Effekseer::Manager::DrawParameter drawParameter;
        drawParameter.ZNear = 0.0f;
        drawParameter.ZFar = 1.0f;
        drawParameter.ViewProjectionMatrix = context->renderer->GetCameraProjectionMatrix();
        context->manager->Draw(drawParameter);
        logGlError("manager-draw");
        context->renderer->EndRendering();
        logGlError("end-rendering");
        context->lastDrawCalls = context->renderer->GetDrawCallCount();
        context->lastDrawVertices = context->renderer->GetDrawVertexCount();

        if (compositeEnabled && !renderDirectToDefaultFramebuffer) {
            compositeOffscreenTexture(context, savedState.framebuffer, savedState.viewport[0], savedState.viewport[1], savedState.viewport[2], savedState.viewport[3]);
        }
        restoreGlState(savedState);
        logGlError("restore-gl-state");

        context->renderFailed = false;
#if _WIN32
    }
    __except (EXCEPTION_EXECUTE_HANDLER)
    {
        context->renderFailed = true;
        context->renderFailureCount += 1;
        clearGlErrors();
        OutputDebugStringA("SceneMax Effekseer JNI: native render failed for this frame.\n");
    }
#endif
}

JNIEXPORT jboolean JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeReadbackFrame
  (JNIEnv* env, jclass, jlong handle, jobject targetBuffer, jint width, jint height) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr || targetBuffer == nullptr || context->offscreenFramebuffer == 0 || context->offscreenColorTexture == 0) {
        return JNI_FALSE;
    }

    auto* bytes = static_cast<uint8_t*>(env->GetDirectBufferAddress(targetBuffer));
    const auto capacity = env->GetDirectBufferCapacity(targetBuffer);
    const auto required = static_cast<jlong>(width) * static_cast<jlong>(height) * 4;
    if (bytes == nullptr || capacity < required || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    if (context->offscreenWidth < width || context->offscreenHeight < height) {
        return JNI_FALSE;
    }

    GLint previousFramebuffer = 0;
    GLint previousPackAlignment = 4;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &previousFramebuffer);
    glGetIntegerv(GL_PACK_ALIGNMENT, &previousPackAlignment);
    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, context->offscreenFramebuffer);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);

    std::vector<uint8_t> scratch(static_cast<size_t>(required));
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, scratch.data());
    auto error = glGetError();

    glPixelStorei(GL_PACK_ALIGNMENT, previousPackAlignment);
    EffekseerRendererGL::GLExt::glBindFramebuffer(GL_FRAMEBUFFER, previousFramebuffer);

    if (error != GL_NO_ERROR) {
        std::fprintf(stderr, "SceneMax Effekseer JNI GL error at readback-frame: 0x%x\n",
                     static_cast<unsigned int>(error));
        return JNI_FALSE;
    }

    const size_t rowBytes = static_cast<size_t>(width) * 4u;
    for (int y = 0; y < height; ++y) {
        const auto* src = scratch.data() + static_cast<size_t>(height - 1 - y) * rowBytes;
        auto* dst = bytes + static_cast<size_t>(y) * rowBytes;
        std::memcpy(dst, src, rowBytes);
    }

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL Java_com_scenemax_effekseer_runtime_EffekseerNativeBridge_nativeGetStatus
  (JNIEnv* env, jclass, jlong handle) {
    auto* context = fromHandle<PreviewContext>(handle);
    if (context == nullptr) {
        return env->NewStringUTF("native context missing");
    }

    std::ostringstream status;
    const char* deviceName = context->deviceType == EffekseerRendererGL::OpenGLDeviceType::OpenGL3
            ? "OpenGL3"
            : (context->deviceType == EffekseerRendererGL::OpenGLDeviceType::OpenGL2 ? "OpenGL2" : "Other");
    status << "effectLoaded=" << (context->effect != nullptr ? "true" : "false")
           << ", deviceType=" << deviceName
           << ", handle=" << context->handle
           << ", exists=" << ((context->manager != nullptr && context->handle >= 0) ? (context->manager->Exists(context->handle) ? "true" : "false") : "false")
           << ", helperInitialized=" << (context->helperInitialized ? "true" : "false")
           << ", renderFailed=" << (context->renderFailed ? "true" : "false")
           << ", renderFailureCount=" << context->renderFailureCount
           << ", drawCalls=" << context->lastDrawCalls
           << ", drawVertices=" << context->lastDrawVertices
           << ", time=" << context->elapsedSeconds
           << ", viewer=(" << context->viewerPosition.X << "," << context->viewerPosition.Y << "," << context->viewerPosition.Z << ")"
           << ", offscreen=" << context->offscreenWidth << "x" << context->offscreenHeight
           << ", offscreenStatus=" << context->lastOffscreenStatus
           << ", requestedSize=" << context->lastRequestedWidth << "x" << context->lastRequestedHeight
           << ", stableSizeFrames=" << context->stableSizeFrames
           << ", glVersion=" << context->glVersion
           << ", glsl=" << context->glShadingLanguageVersion
           << ", glRenderer=" << context->glRenderer
           << ", glVendor=" << context->glVendor
           << ", createCtx=" << context->createGlContext
           << ", loadCtx=" << context->loadGlContext
           << ", renderCtx=" << context->renderGlContext
           << ", createTid=" << context->createThreadId
           << ", loadTid=" << context->loadThreadId
           << ", renderTid=" << context->renderThreadId;
    return env->NewStringUTF(status.str().c_str());
}

} // extern "C"
