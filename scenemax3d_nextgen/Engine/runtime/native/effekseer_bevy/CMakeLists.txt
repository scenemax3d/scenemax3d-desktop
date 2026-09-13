cmake_minimum_required(VERSION 3.18)
project(scenemax_effekseer_bevy LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED EFFEKSEER_ROOT)
  set(EFFEKSEER_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/Effekseer")
endif()

if(NOT EXISTS "${EFFEKSEER_ROOT}/CMakeLists.txt")
  message(FATAL_ERROR "EFFEKSEER_ROOT must point to the official Effekseer checkout.")
endif()

file(TO_CMAKE_PATH "${EFFEKSEER_ROOT}" EFFEKSEER_ROOT_CMAKE)

if(NOT DEFINED VULKAN_HEADERS_ROOT)
  set(VULKAN_HEADERS_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/Vulkan-Headers")
endif()

set(BUILD_GL OFF CACHE BOOL "" FORCE)
set(BUILD_DX9 OFF CACHE BOOL "" FORCE)
set(BUILD_DX11 OFF CACHE BOOL "" FORCE)
set(BUILD_DX12 OFF CACHE BOOL "" FORCE)
set(BUILD_VULKAN ON CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(BUILD_VIEWER OFF CACHE BOOL "" FORCE)
set(BUILD_EDITOR OFF CACHE BOOL "" FORCE)
set(BUILD_TEST OFF CACHE BOOL "" FORCE)
set(NETWORK_ENABLED OFF CACHE BOOL "" FORCE)

if(EXISTS "${VULKAN_HEADERS_ROOT}/include/vulkan/vulkan.hpp")
  file(TO_CMAKE_PATH "${VULKAN_HEADERS_ROOT}/include" VULKAN_HEADERS_INCLUDE_CMAKE)
  set(Vulkan_INCLUDE_DIR "${VULKAN_HEADERS_INCLUDE_CMAKE}" CACHE PATH "" FORCE)
  set(Vulkan_INCLUDE_DIRS "${VULKAN_HEADERS_INCLUDE_CMAKE}" CACHE PATH "" FORCE)
endif()

if(MSVC AND EXISTS "C:/Windows/System32/vulkan-1.dll")
  set(SCENEMAX_VULKAN_DEF "${CMAKE_BINARY_DIR}/vulkan-1.def")
  set(SCENEMAX_VULKAN_IMPORT_LIB "${CMAKE_BINARY_DIR}/vulkan-1.lib")
  if(NOT CMAKE_DUMPBIN)
    find_program(SCENEMAX_DUMPBIN dumpbin.exe)
  else()
    set(SCENEMAX_DUMPBIN "${CMAKE_DUMPBIN}")
  endif()
  if(NOT SCENEMAX_DUMPBIN AND CMAKE_AR)
    get_filename_component(SCENEMAX_MSVC_BIN_DIR "${CMAKE_AR}" DIRECTORY)
    if(EXISTS "${SCENEMAX_MSVC_BIN_DIR}/dumpbin.exe")
      set(SCENEMAX_DUMPBIN "${SCENEMAX_MSVC_BIN_DIR}/dumpbin.exe")
    endif()
  endif()
  if(NOT SCENEMAX_DUMPBIN)
    message(FATAL_ERROR "dumpbin.exe is required to generate a Vulkan import library.")
  endif()
  execute_process(
    COMMAND "${SCENEMAX_DUMPBIN}" /exports "C:/Windows/System32/vulkan-1.dll"
    OUTPUT_VARIABLE SCENEMAX_VULKAN_EXPORTS
  )
  string(REGEX MATCHALL
    "[\r\n][ \t]*[0-9]+[ \t]+[0-9A-Fa-f]+[ \t]+[0-9A-Fa-f]+[ \t]+vk[A-Za-z0-9_]+"
    SCENEMAX_VULKAN_EXPORT_LINES
    "${SCENEMAX_VULKAN_EXPORTS}")
  if(NOT SCENEMAX_VULKAN_EXPORT_LINES)
    message(FATAL_ERROR "Failed to parse C:/Windows/System32/vulkan-1.dll exports.")
  endif()
  set(SCENEMAX_VULKAN_EXPORT_SYMBOLS "")
  foreach(SCENEMAX_VULKAN_EXPORT_LINE IN LISTS SCENEMAX_VULKAN_EXPORT_LINES)
    string(REGEX REPLACE ".*[ \t](vk[A-Za-z0-9_]+)$" "\\1"
      SCENEMAX_VULKAN_EXPORT_SYMBOL
      "${SCENEMAX_VULKAN_EXPORT_LINE}")
    list(APPEND SCENEMAX_VULKAN_EXPORT_SYMBOLS "${SCENEMAX_VULKAN_EXPORT_SYMBOL}")
  endforeach()
  list(REMOVE_DUPLICATES SCENEMAX_VULKAN_EXPORT_SYMBOLS)
  file(WRITE "${SCENEMAX_VULKAN_DEF}" "LIBRARY vulkan-1\nEXPORTS\n")
  foreach(SCENEMAX_VULKAN_EXPORT_SYMBOL IN LISTS SCENEMAX_VULKAN_EXPORT_SYMBOLS)
    file(APPEND "${SCENEMAX_VULKAN_DEF}" "  ${SCENEMAX_VULKAN_EXPORT_SYMBOL}\n")
  endforeach()
  execute_process(
    COMMAND "${CMAKE_AR}" /nologo /machine:x64 "/def:${SCENEMAX_VULKAN_DEF}" "/out:${SCENEMAX_VULKAN_IMPORT_LIB}"
    RESULT_VARIABLE SCENEMAX_VULKAN_LIB_RESULT
  )
  if(NOT SCENEMAX_VULKAN_LIB_RESULT EQUAL 0)
    message(FATAL_ERROR "Failed to generate Vulkan import library.")
  endif()
  set(Vulkan_LIBRARY "${SCENEMAX_VULKAN_IMPORT_LIB}" CACHE FILEPATH "" FORCE)
endif()

find_package(Vulkan REQUIRED)

add_subdirectory("${EFFEKSEER_ROOT}" "${CMAKE_BINARY_DIR}/effekseer-build")

if(MSVC AND TARGET LLGI)
  target_compile_options(LLGI PRIVATE /wd4996)
endif()

add_library(
  scenemax_effekseer_bevy
  SHARED
  scenemax_effekseer_bevy.cpp
  scenemax_effekseer_bevy.h
)

target_include_directories(
  scenemax_effekseer_bevy
  PRIVATE
    "${EFFEKSEER_ROOT_CMAKE}/Dev/Cpp/Effekseer"
    "${EFFEKSEER_ROOT_CMAKE}/Dev/Cpp/EffekseerRendererCommon"
    "${EFFEKSEER_ROOT_CMAKE}/Dev/Cpp/EffekseerRendererLLGI"
    "${EFFEKSEER_ROOT_CMAKE}/Dev/Cpp/EffekseerRendererVulkan"
)

target_link_libraries(
  scenemax_effekseer_bevy
  PRIVATE
    Effekseer
    EffekseerRendererVulkan
    LLGI
    glslang
    SPIRV
    glslang-default-resource-limits
    MachineIndependent
    GenericCodeGen
    OSDependent
    SPVRemapper
    Vulkan::Vulkan
)

if(MSVC)
  set_property(TARGET scenemax_effekseer_bevy PROPERTY
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
  target_compile_definitions(scenemax_effekseer_bevy PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
endif()

include(GNUInstallDirs)
install(TARGETS scenemax_effekseer_bevy
  RUNTIME DESTINATION bin
  LIBRARY DESTINATION lib
  ARCHIVE DESTINATION lib)
