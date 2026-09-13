#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef _WIN32
#define SCENEMAX_EFFEKSEER_API __declspec(dllexport)
#else
#define SCENEMAX_EFFEKSEER_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct SceneMaxEffekseerVector3 {
  float x;
  float y;
  float z;
} SceneMaxEffekseerVector3;

typedef struct SceneMaxEffekseerInstanceDesc {
  uint64_t id;
  uint64_t effect_id;
  uint64_t play_generation;
  SceneMaxEffekseerVector3 position;
  float transform[16];
  float playback_speed;
  bool looped;
  float dynamic_inputs[4];
} SceneMaxEffekseerInstanceDesc;

typedef struct SceneMaxEffekseerVulkanContext {
  void* physical_device;
  void* device;
  void* queue;
  uint32_t queue_family_index;
  uint32_t color_format;
  uint32_t depth_format;
  uint32_t frames_in_flight;
  int32_t sprite_count;
} SceneMaxEffekseerVulkanContext;

typedef struct SceneMaxEffekseerVulkanRenderTarget {
  void* color_image_view;
  void* depth_image_view;
  uint32_t width;
  uint32_t height;
  uint32_t viewport_x;
  uint32_t viewport_y;
  uint32_t viewport_width;
  uint32_t viewport_height;
  uint32_t clear_color;
  uint32_t final_shader_read;
} SceneMaxEffekseerVulkanRenderTarget;

typedef struct SceneMaxEffekseerStats {
  uint32_t loaded_effect_count;
  uint32_t tracked_handle_count;
  uint32_t active_handle_count;
  int32_t total_instance_count;
  int32_t draw_call_count;
  int32_t draw_vertex_count;
  uint32_t render_width;
  uint32_t render_height;
  uint32_t viewport_width;
  uint32_t viewport_height;
  uint32_t retired_render_pass_count;
  uint64_t render_call_count;
  uint64_t play_call_count;
  uint64_t load_call_count;
  bool last_render_pass_ok;
  bool last_begin_rendering_ok;
} SceneMaxEffekseerStats;

typedef struct SceneMaxEffekseerRenderer SceneMaxEffekseerRenderer;

SCENEMAX_EFFEKSEER_API SceneMaxEffekseerRenderer*
scenemax_effekseer_create_vulkan(const SceneMaxEffekseerVulkanContext* context);

SCENEMAX_EFFEKSEER_API void
scenemax_effekseer_destroy(SceneMaxEffekseerRenderer* renderer);

SCENEMAX_EFFEKSEER_API bool
scenemax_effekseer_load_effect(SceneMaxEffekseerRenderer* renderer,
                               uint64_t id,
                               const uint8_t* path_utf8,
                               size_t path_len);

SCENEMAX_EFFEKSEER_API uint64_t
scenemax_effekseer_play(SceneMaxEffekseerRenderer* renderer,
                        uint64_t effect_id,
                        const SceneMaxEffekseerInstanceDesc* instance);

SCENEMAX_EFFEKSEER_API void
scenemax_effekseer_render_vulkan(SceneMaxEffekseerRenderer* renderer,
                                 void* command_buffer,
                                 const SceneMaxEffekseerVulkanRenderTarget* target,
                                 const float* projection_column_major,
                                 const float* camera_view_column_major,
                                 const float* camera_position,
                                 float delta_seconds,
                                 const SceneMaxEffekseerInstanceDesc* instances,
                                 size_t instance_count);

SCENEMAX_EFFEKSEER_API void
scenemax_effekseer_render_vulkan_submit(SceneMaxEffekseerRenderer* renderer,
                                        const SceneMaxEffekseerVulkanRenderTarget* target,
                                        const float* projection_column_major,
                                        const float* camera_view_column_major,
                                        const float* camera_position,
                                        float delta_seconds,
                                        const SceneMaxEffekseerInstanceDesc* instances,
                                        size_t instance_count);

SCENEMAX_EFFEKSEER_API const char*
scenemax_effekseer_status(SceneMaxEffekseerRenderer* renderer);

SCENEMAX_EFFEKSEER_API bool
scenemax_effekseer_get_stats(SceneMaxEffekseerRenderer* renderer,
                             SceneMaxEffekseerStats* out_stats);

#ifdef __cplusplus
}
#endif
