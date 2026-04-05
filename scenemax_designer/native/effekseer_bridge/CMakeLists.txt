cmake_minimum_required(VERSION 3.15)
project(scenemax_effekseer_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED EFFEKSEER_ROOT)
  set(EFFEKSEER_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../third_party/Effekseer")
endif()

if(NOT EXISTS "${EFFEKSEER_ROOT}/CMakeLists.txt")
  message(FATAL_ERROR "EFFEKSEER_ROOT must point to the official Effekseer checkout.")
endif()

file(TO_CMAKE_PATH "${EFFEKSEER_ROOT}" EFFEKSEER_ROOT_CMAKE)

if(NOT DEFINED ENV{JAVA_HOME})
  message(FATAL_ERROR "JAVA_HOME must be set to a JDK installation before configuring scenemax_effekseer_jni.")
endif()

set(JAVA_HOME_DIR "$ENV{JAVA_HOME}")
set(JAVA_INCLUDE_DIR "${JAVA_HOME_DIR}/include")
if(WIN32)
  set(JAVA_INCLUDE_PLATFORM_DIR "${JAVA_HOME_DIR}/include/win32")
elseif(APPLE)
  set(JAVA_INCLUDE_PLATFORM_DIR "${JAVA_HOME_DIR}/include/darwin")
else()
  set(JAVA_INCLUDE_PLATFORM_DIR "${JAVA_HOME_DIR}/include/linux")
endif()

if(NOT EXISTS "${JAVA_INCLUDE_DIR}/jni.h")
  message(FATAL_ERROR "Could not find jni.h under ${JAVA_INCLUDE_DIR}. JAVA_HOME must point to a JDK.")
endif()

if(NOT EXISTS "${JAVA_INCLUDE_PLATFORM_DIR}")
  message(FATAL_ERROR "Could not find JNI platform headers under ${JAVA_INCLUDE_PLATFORM_DIR}.")
endif()

add_subdirectory("${EFFEKSEER_ROOT}" "${CMAKE_BINARY_DIR}/effekseer-build")

add_library(
  scenemax_effekseer_jni
  SHARED
  scenemax_effekseer_jni.cpp
)

if(MSVC)
  set_property(TARGET scenemax_effekseer_jni PROPERTY
    MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

if(WIN32)
  set(SCENEMAX_NATIVE_PLATFORM_DIR "windows-x86_64")
elseif(APPLE)
  if(CMAKE_OSX_ARCHITECTURES MATCHES "arm64")
    set(SCENEMAX_NATIVE_PLATFORM_DIR "macos-aarch64")
  else()
    set(SCENEMAX_NATIVE_PLATFORM_DIR "macos-x86_64")
  endif()
else()
  set(SCENEMAX_NATIVE_PLATFORM_DIR "linux-x86_64")
endif()

set(NATIVE_OUTPUT_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../scenemax_effekseer_runtime/assets/native/${SCENEMAX_NATIVE_PLATFORM_DIR}")
set_target_properties(scenemax_effekseer_jni PROPERTIES
  RUNTIME_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}"
  LIBRARY_OUTPUT_DIRECTORY "${NATIVE_OUTPUT_DIR}")

target_include_directories(
  scenemax_effekseer_jni
  PRIVATE
    "${JAVA_INCLUDE_DIR}"
    "${JAVA_INCLUDE_PLATFORM_DIR}"
    "${EFFEKSEER_ROOT_CMAKE}/Dev/Cpp/Effekseer"
    "${EFFEKSEER_ROOT_CMAKE}/Dev/Cpp/EffekseerRendererGL"
)

target_link_libraries(
  scenemax_effekseer_jni
  PRIVATE
    Effekseer
    EffekseerRendererGL
)

if(WIN32)
  target_compile_definitions(scenemax_effekseer_jni PRIVATE NOMINMAX WIN32_LEAN_AND_MEAN)
endif()
