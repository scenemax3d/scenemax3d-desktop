#include "scenemax_effekseer_bevy.h"

#include <Effekseer.h>
#include <EffekseerRendererVulkan.h>

#include <algorithm>
#include <codecvt>
#include <cstdint>
#include <locale>
#include <memory>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace {

constexpr int32_t kDefaultSpriteCount = 8000;
constexpr uint32_t kDefaultFramesInFlight = 3;

std::u16string utf8ToUtf16(const uint8_t* data, size_t len) {
  if (data == nullptr || len == 0) {
    return {};
  }
  std::string source(reinterpret_cast<const char*>(data), len);
  std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t> convert;
  return convert.from_bytes(source);
}

Effekseer::Matrix43 matrixFromPosition(const SceneMaxEffekseerVector3& position) {
  Effekseer::Matrix43 matrix;
  matrix.Indentity();
  matrix.Value[3][0] = position.x;
  matrix.Value[3][1] = position.y;
  matrix.Value[3][2] = position.z;
  return matrix;
}

Effekseer::Matrix43 matrix43BasisFromColumnMajor(const float* values) {
  Effekseer::Matrix43 matrix;
  matrix.Indentity();
  if (values == nullptr) {
    return matrix;
  }

  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      matrix.Value[row][col] = values[col * 4 + row];
    }
  }
  matrix.Value[3][0] = 0.0f;
  matrix.Value[3][1] = 0.0f;
  matrix.Value[3][2] = 0.0f;
  return matrix;
}

Effekseer::Matrix44 matrix44FromColumnMajor(const float* values) {
  Effekseer::Matrix44 matrix;
  matrix.Indentity();
  if (values == nullptr) {
    return matrix;
  }

  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 4; ++col) {
      matrix.Values[row][col] = values[col * 4 + row];
    }
  }
  return matrix;
}

void setupModules(SceneMaxEffekseerRenderer* ctx);

} // namespace

struct ScopedVulkanRenderPass {
  VkDevice device = VK_NULL_HANDLE;
  VkFramebuffer framebuffer = VK_NULL_HANDLE;
  VkRenderPass renderPass = VK_NULL_HANDLE;
};

struct RetiredVulkanRenderPass {
  ScopedVulkanRenderPass pass;
  uint32_t framesRemaining = 0;
};

struct SceneMaxEffekseerRenderer {
  VkDevice device = VK_NULL_HANDLE;
  VkQueue queue = VK_NULL_HANDLE;
  VkFormat colorFormat = VK_FORMAT_UNDEFINED;
  VkFormat depthFormat = VK_FORMAT_UNDEFINED;
  uint32_t framesInFlight = kDefaultFramesInFlight;
  VkCommandPool transferCommandPool = VK_NULL_HANDLE;
  EffekseerRenderer::RendererRef renderer;
  Effekseer::Backend::GraphicsDeviceRef graphicsDevice;
  Effekseer::RefPtr<EffekseerRenderer::SingleFrameMemoryPool> memoryPool;
  Effekseer::RefPtr<EffekseerRenderer::CommandList> commandList;
  Effekseer::ManagerRef manager;
  std::unordered_map<uint64_t, Effekseer::EffectRef> effects;
  std::unordered_map<uint64_t, Effekseer::Handle> handles;
  std::unordered_map<uint64_t, uint64_t> playGenerations;
  std::vector<RetiredVulkanRenderPass> retiredRenderPasses;
  uint64_t renderCallCount = 0;
  uint64_t playCallCount = 0;
  uint64_t loadCallCount = 0;
  int32_t lastDrawCallCount = 0;
  int32_t lastDrawVertexCount = 0;
  uint32_t lastRenderWidth = 0;
  uint32_t lastRenderHeight = 0;
  uint32_t lastViewportWidth = 0;
  uint32_t lastViewportHeight = 0;
  bool lastRenderPassOk = false;
  bool lastBeginRenderingOk = false;
  std::string status = "created";
};

namespace {

void destroyScopedRenderPass(ScopedVulkanRenderPass& pass) {
  if (pass.device != VK_NULL_HANDLE && pass.framebuffer != VK_NULL_HANDLE) {
    vkDestroyFramebuffer(pass.device, pass.framebuffer, nullptr);
    pass.framebuffer = VK_NULL_HANDLE;
  }
  if (pass.device != VK_NULL_HANDLE && pass.renderPass != VK_NULL_HANDLE) {
    vkDestroyRenderPass(pass.device, pass.renderPass, nullptr);
    pass.renderPass = VK_NULL_HANDLE;
  }
}

void retireScopedRenderPass(SceneMaxEffekseerRenderer* ctx, ScopedVulkanRenderPass& pass) {
  if (ctx == nullptr || pass.renderPass == VK_NULL_HANDLE || pass.framebuffer == VK_NULL_HANDLE) {
    destroyScopedRenderPass(pass);
    return;
  }
  ctx->retiredRenderPasses.push_back({pass, ctx->framesInFlight + 2});
  pass = {};
}

void collectRetiredRenderPasses(SceneMaxEffekseerRenderer* ctx) {
  if (ctx == nullptr) {
    return;
  }

  auto writeIt = ctx->retiredRenderPasses.begin();
  for (auto readIt = ctx->retiredRenderPasses.begin(); readIt != ctx->retiredRenderPasses.end(); ++readIt) {
    if (readIt->framesRemaining > 0) {
      --readIt->framesRemaining;
    }
    if (readIt->framesRemaining == 0) {
      destroyScopedRenderPass(readIt->pass);
    } else {
      if (writeIt != readIt) {
        *writeIt = *readIt;
      }
      ++writeIt;
    }
  }
  ctx->retiredRenderPasses.erase(writeIt, ctx->retiredRenderPasses.end());
}

bool beginBevyRenderTarget(SceneMaxEffekseerRenderer* ctx,
                           VkCommandBuffer commandBuffer,
                           const SceneMaxEffekseerVulkanRenderTarget* target,
                           ScopedVulkanRenderPass& pass) {
  if (ctx == nullptr || target == nullptr || target->color_image_view == nullptr ||
      target->width == 0 || target->height == 0) {
    if (ctx != nullptr) {
      ctx->status = "missing Bevy Vulkan render target";
      ctx->lastRenderPassOk = false;
    }
    return false;
  }

  const bool hasDepth =
      target->depth_image_view != nullptr && ctx->depthFormat != VK_FORMAT_UNDEFINED;
  pass.device = ctx->device;

  VkAttachmentDescription attachments[2] = {};
  attachments[0].format = ctx->colorFormat;
  attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
  const bool clearColor = target->clear_color != 0;
  const bool finalShaderRead = target->final_shader_read != 0;
  attachments[0].loadOp = clearColor ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
  attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
  attachments[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
  attachments[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
  attachments[0].initialLayout =
      clearColor ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
  attachments[0].finalLayout =
      finalShaderRead ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

  VkAttachmentReference colorRef = {};
  colorRef.attachment = 0;
  colorRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

  VkAttachmentReference depthRef = {};
  if (hasDepth) {
    attachments[1].format = ctx->depthFormat;
    attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_LOAD;
    attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].initialLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    depthRef.attachment = 1;
    depthRef.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
  }

  VkSubpassDescription subpass = {};
  subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
  subpass.colorAttachmentCount = 1;
  subpass.pColorAttachments = &colorRef;
  subpass.pDepthStencilAttachment = hasDepth ? &depthRef : nullptr;

  VkRenderPassCreateInfo renderPassInfo = {};
  renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
  renderPassInfo.attachmentCount = hasDepth ? 2u : 1u;
  renderPassInfo.pAttachments = attachments;
  renderPassInfo.subpassCount = 1;
  renderPassInfo.pSubpasses = &subpass;
  VkResult result = vkCreateRenderPass(ctx->device, &renderPassInfo, nullptr, &pass.renderPass);
  if (result != VK_SUCCESS) {
    ctx->status = "failed to create Bevy Vulkan render pass";
    ctx->lastRenderPassOk = false;
    return false;
  }

  VkImageView views[2] = {
      reinterpret_cast<VkImageView>(target->color_image_view),
      reinterpret_cast<VkImageView>(target->depth_image_view),
  };
  VkFramebufferCreateInfo framebufferInfo = {};
  framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
  framebufferInfo.renderPass = pass.renderPass;
  framebufferInfo.attachmentCount = hasDepth ? 2u : 1u;
  framebufferInfo.pAttachments = views;
  framebufferInfo.width = target->width;
  framebufferInfo.height = target->height;
  framebufferInfo.layers = 1;
  result = vkCreateFramebuffer(ctx->device, &framebufferInfo, nullptr, &pass.framebuffer);
  if (result != VK_SUCCESS) {
    ctx->status = "failed to create Bevy Vulkan framebuffer";
    ctx->lastRenderPassOk = false;
    destroyScopedRenderPass(pass);
    return false;
  }

  VkRenderPassBeginInfo beginInfo = {};
  beginInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
  beginInfo.renderPass = pass.renderPass;
  beginInfo.framebuffer = pass.framebuffer;
  beginInfo.renderArea.offset = {0, 0};
  beginInfo.renderArea.extent = {target->width, target->height};
  VkClearValue clearValues[1] = {};
  if (clearColor) {
    clearValues[0].color = {{0.0f, 0.0f, 0.0f, 0.0f}};
    beginInfo.clearValueCount = 1;
    beginInfo.pClearValues = clearValues;
  }
  vkCmdBeginRenderPass(commandBuffer, &beginInfo, VK_SUBPASS_CONTENTS_INLINE);

  const float viewportX = static_cast<float>(target->viewport_x);
  const float viewportY = static_cast<float>(target->viewport_y);
  const float viewportWidth = static_cast<float>(target->viewport_width);
  const float viewportHeight = static_cast<float>(target->viewport_height);
  VkViewport viewport = {};
  viewport.x = viewportX;
  viewport.y = viewportY + viewportHeight;
  viewport.width = viewportWidth;
  viewport.height = -viewportHeight;
  viewport.minDepth = 0.0f;
  viewport.maxDepth = 1.0f;
  vkCmdSetViewport(commandBuffer, 0, 1, &viewport);

  VkRect2D scissor = {};
  scissor.offset = {static_cast<int32_t>(target->viewport_x), static_cast<int32_t>(target->viewport_y)};
  scissor.extent = {target->viewport_width, target->viewport_height};
  vkCmdSetScissor(commandBuffer, 0, 1, &scissor);

  ctx->lastRenderWidth = target->width;
  ctx->lastRenderHeight = target->height;
  ctx->lastViewportWidth = target->viewport_width;
  ctx->lastViewportHeight = target->viewport_height;
  ctx->lastRenderPassOk = true;
  return true;
}

void setupModules(SceneMaxEffekseerRenderer* ctx) {
  if (ctx == nullptr || ctx->manager == nullptr || ctx->renderer == nullptr) {
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

bool ensureTransferCommandPool(SceneMaxEffekseerRenderer* ctx,
                               const SceneMaxEffekseerVulkanContext* desc) {
  if (ctx == nullptr || desc == nullptr || ctx->device == VK_NULL_HANDLE) {
    return false;
  }

  VkCommandPoolCreateInfo poolInfo = {};
  poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
  poolInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT | VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
  poolInfo.queueFamilyIndex = desc->queue_family_index;
  VkResult result = vkCreateCommandPool(ctx->device, &poolInfo, nullptr, &ctx->transferCommandPool);
  if (result != VK_SUCCESS) {
    ctx->status = "failed to create Vulkan transfer command pool";
    return false;
  }
  return true;
}

void applyInstance(SceneMaxEffekseerRenderer* ctx,
                   Effekseer::Handle handle,
                   const SceneMaxEffekseerInstanceDesc& instance) {
  if (ctx == nullptr || ctx->manager == nullptr || handle < 0) {
    return;
  }
  ctx->manager->SetLocation(handle,
                            instance.position.x,
                            instance.position.y,
                            instance.position.z);
  ctx->manager->SetBaseMatrix(handle, matrix43BasisFromColumnMajor(instance.transform));
  ctx->manager->SetSpeed(handle, std::max(instance.playback_speed, 0.001f));
  for (int32_t i = 0; i < 4; ++i) {
    ctx->manager->SetDynamicInput(handle, i, instance.dynamic_inputs[i]);
  }
}

uint64_t playInstance(SceneMaxEffekseerRenderer* ctx,
                      uint64_t effectId,
                      const SceneMaxEffekseerInstanceDesc* instance) {
  if (ctx == nullptr || ctx->manager == nullptr || instance == nullptr) {
    return 0;
  }
  ctx->playCallCount++;
  auto effectIt = ctx->effects.find(effectId);
  if (effectIt == ctx->effects.end() || effectIt->second == nullptr) {
    ctx->status = "effect id is not loaded";
    return 0;
  }

  Effekseer::Manager::PlayParameter parameter;
  parameter.Effect = effectIt->second;
  parameter.Position = {0.0f, 0.0f, 0.0f};
  auto handle = ctx->manager->Play(parameter);
  if (handle < 0) {
    ctx->status = "Effekseer manager failed to play effect";
    return 0;
  }

  auto previous = ctx->handles.find(instance->id);
  if (previous != ctx->handles.end() && previous->second >= 0 && previous->second != handle) {
    ctx->manager->StopEffect(previous->second);
  }
  ctx->handles[instance->id] = handle;
  ctx->playGenerations[instance->id] = instance->play_generation;
  applyInstance(ctx, handle, *instance);
  ctx->status = "playing";
  return static_cast<uint64_t>(handle);
}

} // namespace

extern "C" {

SCENEMAX_EFFEKSEER_API SceneMaxEffekseerRenderer*
scenemax_effekseer_create_vulkan(const SceneMaxEffekseerVulkanContext* context) {
  if (context == nullptr || context->physical_device == nullptr || context->device == nullptr ||
      context->queue == nullptr) {
    return nullptr;
  }

  auto ctx = std::make_unique<SceneMaxEffekseerRenderer>();
  ctx->device = reinterpret_cast<VkDevice>(context->device);
  ctx->queue = reinterpret_cast<VkQueue>(context->queue);
  ctx->colorFormat = static_cast<VkFormat>(context->color_format);
  ctx->depthFormat = static_cast<VkFormat>(context->depth_format);
  ctx->framesInFlight =
      context->frames_in_flight > 0 ? context->frames_in_flight : kDefaultFramesInFlight;
  if (!ensureTransferCommandPool(ctx.get(), context)) {
    return nullptr;
  }

  EffekseerRendererVulkan::RenderPassInformation renderPassInfo;
  renderPassInfo.DoesPresentToScreen = false;
  renderPassInfo.RenderTextureCount = 1;
  renderPassInfo.RenderTextureFormats[0] = static_cast<VkFormat>(context->color_format);
  renderPassInfo.DepthFormat = static_cast<VkFormat>(context->depth_format);

  const int32_t spriteCount = context->sprite_count > 0 ? context->sprite_count : kDefaultSpriteCount;
  const int32_t framesInFlight = static_cast<int32_t>(
      context->frames_in_flight > 0 ? context->frames_in_flight : kDefaultFramesInFlight);

  ctx->renderer = EffekseerRendererVulkan::Create(
      reinterpret_cast<VkPhysicalDevice>(context->physical_device),
      reinterpret_cast<VkDevice>(context->device),
      reinterpret_cast<VkQueue>(context->queue),
      ctx->transferCommandPool,
      framesInFlight,
      renderPassInfo,
      spriteCount);
  ctx->manager = Effekseer::Manager::Create(spriteCount);

  if (ctx->renderer == nullptr || ctx->manager == nullptr) {
    return nullptr;
  }

  ctx->graphicsDevice = ctx->renderer->GetGraphicsDevice();
  ctx->memoryPool = EffekseerRenderer::CreateSingleFrameMemoryPool(ctx->graphicsDevice);
  ctx->commandList = EffekseerRenderer::CreateCommandList(ctx->graphicsDevice, ctx->memoryPool);
  if (ctx->memoryPool == nullptr || ctx->commandList == nullptr) {
    return nullptr;
  }

  setupModules(ctx.get());
  ctx->status = "ready";
  return ctx.release();
}

SCENEMAX_EFFEKSEER_API void scenemax_effekseer_destroy(SceneMaxEffekseerRenderer* renderer) {
  if (renderer == nullptr) {
    return;
  }
  if (renderer->device != VK_NULL_HANDLE) {
    vkDeviceWaitIdle(renderer->device);
  }
  for (auto& retired : renderer->retiredRenderPasses) {
    destroyScopedRenderPass(retired.pass);
  }
  renderer->retiredRenderPasses.clear();
  renderer->handles.clear();
  renderer->playGenerations.clear();
  renderer->effects.clear();
  renderer->commandList.Reset();
  renderer->memoryPool.Reset();
  renderer->manager.Reset();
  renderer->renderer.Reset();
  renderer->graphicsDevice.Reset();
  if (renderer->device != VK_NULL_HANDLE && renderer->transferCommandPool != VK_NULL_HANDLE) {
    vkDestroyCommandPool(renderer->device, renderer->transferCommandPool, nullptr);
    renderer->transferCommandPool = VK_NULL_HANDLE;
  }
  delete renderer;
}

SCENEMAX_EFFEKSEER_API bool scenemax_effekseer_load_effect(SceneMaxEffekseerRenderer* renderer,
                                                           uint64_t id,
                                                           const uint8_t* path_utf8,
                                                           size_t path_len) {
  if (renderer == nullptr || renderer->manager == nullptr || id == 0) {
    return false;
  }
  renderer->loadCallCount++;
  auto path = utf8ToUtf16(path_utf8, path_len);
  if (path.empty()) {
    renderer->status = "empty effect path";
    return false;
  }

  auto effect = Effekseer::Effect::Create(renderer->manager,
                                          reinterpret_cast<const char16_t*>(path.c_str()));
  if (effect == nullptr) {
    renderer->status = "failed to load effect";
    return false;
  }

  renderer->effects[id] = effect;
  renderer->status = "effect loaded";
  return true;
}

SCENEMAX_EFFEKSEER_API uint64_t
scenemax_effekseer_play(SceneMaxEffekseerRenderer* renderer,
                        uint64_t effect_id,
                        const SceneMaxEffekseerInstanceDesc* instance) {
  return playInstance(renderer, effect_id, instance);
}

SCENEMAX_EFFEKSEER_API void
scenemax_effekseer_render_vulkan(SceneMaxEffekseerRenderer* renderer,
                                 void* command_buffer,
                                 const SceneMaxEffekseerVulkanRenderTarget* target,
                                 const float* projection_column_major,
                                 const float* camera_view_column_major,
                                 const float* camera_position,
                                 float delta_seconds,
                                 const SceneMaxEffekseerInstanceDesc* instances,
                                 size_t instance_count) {
  if (renderer == nullptr || renderer->renderer == nullptr || renderer->manager == nullptr ||
      renderer->commandList == nullptr || command_buffer == nullptr) {
    return;
  }
  renderer->renderCallCount++;
  renderer->lastDrawCallCount = 0;
  renderer->lastDrawVertexCount = 0;
  renderer->lastRenderPassOk = false;
  renderer->lastBeginRenderingOk = false;
  collectRetiredRenderPasses(renderer);

  std::unordered_set<uint64_t> submittedIds;
  submittedIds.reserve(instance_count);

  for (size_t i = 0; i < instance_count; ++i) {
    const auto& instance = instances[i];
    submittedIds.insert(instance.id);
    auto handleIt = renderer->handles.find(instance.id);
    auto generationIt = renderer->playGenerations.find(instance.id);
    const bool hasHandle = handleIt != renderer->handles.end();
    const bool handleAlive = hasHandle && renderer->manager->Exists(handleIt->second);
    const bool generationChanged =
        generationIt == renderer->playGenerations.end() ||
        generationIt->second != instance.play_generation;

    if (instance.looped) {
      if (!handleAlive || generationChanged) {
        playInstance(renderer, instance.effect_id, &instance);
      } else {
        applyInstance(renderer, handleIt->second, instance);
      }
      continue;
    }

    if (hasHandle && !handleAlive && !generationChanged) {
      renderer->handles.erase(handleIt);
      continue;
    }

    if (generationChanged) {
      playInstance(renderer, instance.effect_id, &instance);
    } else if (handleAlive) {
      applyInstance(renderer, handleIt->second, instance);
    }
  }

  for (auto it = renderer->handles.begin(); it != renderer->handles.end();) {
    if (submittedIds.find(it->first) != submittedIds.end()) {
      ++it;
      continue;
    }
    if (it->second >= 0 && renderer->manager->Exists(it->second)) {
      renderer->manager->StopEffect(it->second);
    }
    renderer->playGenerations.erase(it->first);
    it = renderer->handles.erase(it);
  }

  Effekseer::Manager::UpdateParameter updateParameter;
  updateParameter.DeltaFrame = std::max(delta_seconds, 0.0f) * 60.0f;
  updateParameter.UpdateInterval = 1.0f;
  updateParameter.SyncUpdate = true;
  renderer->manager->Update(updateParameter);

  Effekseer::Manager::DrawParameter drawParameter;
  renderer->renderer->SetTime(static_cast<float>(renderer->renderCallCount) / 60.0f);
  renderer->renderer->SetProjectionMatrix(matrix44FromColumnMajor(projection_column_major));
  renderer->renderer->SetCameraMatrix(matrix44FromColumnMajor(camera_view_column_major));
  drawParameter.ZNear = 0.0f;
  drawParameter.ZFar = 1.0f;
  drawParameter.ViewProjectionMatrix = renderer->renderer->GetCameraProjectionMatrix();
  if (camera_position != nullptr) {
    drawParameter.CameraPosition = {camera_position[0], camera_position[1], camera_position[2]};
  }
  drawParameter.CameraFrontDirection = renderer->renderer->GetCameraFrontDirection();
  drawParameter.CameraCullingMask = 0x7fffffff;

  renderer->renderer->ResetDrawCallCount();
  renderer->renderer->ResetDrawVertexCount();
  renderer->memoryPool->NewFrame();
  auto nativeCommandBuffer = reinterpret_cast<VkCommandBuffer>(command_buffer);
  ScopedVulkanRenderPass renderPass;
  if (!beginBevyRenderTarget(renderer, nativeCommandBuffer, target, renderPass)) {
    return;
  }

  EffekseerRendererVulkan::BeginCommandList(renderer->commandList, nativeCommandBuffer);
  renderer->renderer->SetCommandList(renderer->commandList);
  renderer->lastBeginRenderingOk = renderer->renderer->BeginRendering();
  if (renderer->lastBeginRenderingOk) {
    renderer->manager->Draw(drawParameter);
    renderer->renderer->EndRendering();
  } else {
    renderer->status = "Effekseer renderer BeginRendering returned false";
  }
  renderer->lastDrawCallCount = renderer->renderer->GetDrawCallCount();
  renderer->lastDrawVertexCount = renderer->renderer->GetDrawVertexCount();
  renderer->renderer->SetCommandList(nullptr);
  EffekseerRendererVulkan::EndCommandList(renderer->commandList);
  vkCmdEndRenderPass(nativeCommandBuffer);
  retireScopedRenderPass(renderer, renderPass);
  if (renderer->lastBeginRenderingOk) {
    renderer->status = "rendered";
  }
}

SCENEMAX_EFFEKSEER_API void
scenemax_effekseer_render_vulkan_submit(SceneMaxEffekseerRenderer* renderer,
                                        const SceneMaxEffekseerVulkanRenderTarget* target,
                                        const float* projection_column_major,
                                        const float* camera_view_column_major,
                                        const float* camera_position,
                                        float delta_seconds,
                                        const SceneMaxEffekseerInstanceDesc* instances,
                                        size_t instance_count) {
  if (renderer == nullptr || renderer->device == VK_NULL_HANDLE ||
      renderer->queue == VK_NULL_HANDLE || renderer->transferCommandPool == VK_NULL_HANDLE) {
    return;
  }

  VkCommandBufferAllocateInfo allocateInfo = {};
  allocateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
  allocateInfo.commandPool = renderer->transferCommandPool;
  allocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
  allocateInfo.commandBufferCount = 1;
  VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
  VkResult result = vkAllocateCommandBuffers(renderer->device, &allocateInfo, &commandBuffer);
  if (result != VK_SUCCESS || commandBuffer == VK_NULL_HANDLE) {
    renderer->status = "failed to allocate native Vulkan command buffer";
    return;
  }

  VkCommandBufferBeginInfo beginInfo = {};
  beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
  beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
  result = vkBeginCommandBuffer(commandBuffer, &beginInfo);
  if (result != VK_SUCCESS) {
    renderer->status = "failed to begin native Vulkan command buffer";
    vkFreeCommandBuffers(renderer->device, renderer->transferCommandPool, 1, &commandBuffer);
    return;
  }

  scenemax_effekseer_render_vulkan(renderer,
                                   reinterpret_cast<void*>(commandBuffer),
                                   target,
                                   projection_column_major,
                                   camera_view_column_major,
                                   camera_position,
                                   delta_seconds,
                                   instances,
                                   instance_count);

  result = vkEndCommandBuffer(commandBuffer);
  if (result != VK_SUCCESS) {
    renderer->status = "failed to end native Vulkan command buffer";
    vkFreeCommandBuffers(renderer->device, renderer->transferCommandPool, 1, &commandBuffer);
    return;
  }

  VkFenceCreateInfo fenceInfo = {};
  fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
  VkFence fence = VK_NULL_HANDLE;
  result = vkCreateFence(renderer->device, &fenceInfo, nullptr, &fence);
  if (result != VK_SUCCESS || fence == VK_NULL_HANDLE) {
    renderer->status = "failed to create native Vulkan submit fence";
    vkFreeCommandBuffers(renderer->device, renderer->transferCommandPool, 1, &commandBuffer);
    return;
  }

  VkSubmitInfo submitInfo = {};
  submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
  submitInfo.commandBufferCount = 1;
  submitInfo.pCommandBuffers = &commandBuffer;
  result = vkQueueSubmit(renderer->queue, 1, &submitInfo, fence);
  if (result == VK_SUCCESS) {
    vkWaitForFences(renderer->device, 1, &fence, VK_TRUE, UINT64_MAX);
  } else {
    renderer->status = "failed to submit native Vulkan command buffer";
  }

  vkDestroyFence(renderer->device, fence, nullptr);
  vkFreeCommandBuffers(renderer->device, renderer->transferCommandPool, 1, &commandBuffer);
}

SCENEMAX_EFFEKSEER_API const char* scenemax_effekseer_status(SceneMaxEffekseerRenderer* renderer) {
  if (renderer == nullptr) {
    return "missing renderer";
  }
  return renderer->status.c_str();
}

SCENEMAX_EFFEKSEER_API bool
scenemax_effekseer_get_stats(SceneMaxEffekseerRenderer* renderer,
                             SceneMaxEffekseerStats* out_stats) {
  if (renderer == nullptr || out_stats == nullptr) {
    return false;
  }

  uint32_t activeHandles = 0;
  if (renderer->manager != nullptr) {
    for (const auto& entry : renderer->handles) {
      if (renderer->manager->Exists(entry.second)) {
        activeHandles++;
      }
    }
  }

  out_stats->loaded_effect_count = static_cast<uint32_t>(renderer->effects.size());
  out_stats->tracked_handle_count = static_cast<uint32_t>(renderer->handles.size());
  out_stats->active_handle_count = activeHandles;
  out_stats->total_instance_count =
      renderer->manager != nullptr ? renderer->manager->GetTotalInstanceCount() : 0;
  out_stats->draw_call_count = renderer->lastDrawCallCount;
  out_stats->draw_vertex_count = renderer->lastDrawVertexCount;
  out_stats->render_width = renderer->lastRenderWidth;
  out_stats->render_height = renderer->lastRenderHeight;
  out_stats->viewport_width = renderer->lastViewportWidth;
  out_stats->viewport_height = renderer->lastViewportHeight;
  out_stats->retired_render_pass_count =
      static_cast<uint32_t>(renderer->retiredRenderPasses.size());
  out_stats->render_call_count = renderer->renderCallCount;
  out_stats->play_call_count = renderer->playCallCount;
  out_stats->load_call_count = renderer->loadCallCount;
  out_stats->last_render_pass_ok = renderer->lastRenderPassOk;
  out_stats->last_begin_rendering_ok = renderer->lastBeginRenderingOk;
  return true;
}

} // extern "C"
