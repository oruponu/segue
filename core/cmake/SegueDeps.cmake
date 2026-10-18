# Exposes the Segue Core dependencies (built by core/scripts/build_deps.sh) as CMake targets.
include_guard(GLOBAL)

if(NOT ANDROID)
  message(FATAL_ERROR "Segue Core currently supports Android (NDK) builds only")
endif()

get_filename_component(SEGUE_DEPS_DIR
  "${CMAKE_CURRENT_LIST_DIR}/../deps/android/${ANDROID_ABI}" ABSOLUTE)

if(NOT EXISTS "${SEGUE_DEPS_DIR}/lib/libessentia.a")
  message(FATAL_ERROR
    "Dependencies not found: ${SEGUE_DEPS_DIR}\n"
    "Run core/scripts/build_deps.sh android on Linux first (on Windows, use WSL)")
endif()

add_library(segue_onnxruntime SHARED IMPORTED GLOBAL)
set_target_properties(segue_onnxruntime PROPERTIES
  IMPORTED_LOCATION "${SEGUE_DEPS_DIR}/lib/libonnxruntime.so"
  INTERFACE_INCLUDE_DIRECTORIES "${SEGUE_DEPS_DIR}/include/onnxruntime")

add_library(segue_deps INTERFACE)
target_include_directories(segue_deps INTERFACE "${SEGUE_DEPS_DIR}/include")
target_link_libraries(segue_deps INTERFACE
  "${SEGUE_DEPS_DIR}/lib/libessentia.a"
  "${SEGUE_DEPS_DIR}/lib/libavformat.a"
  "${SEGUE_DEPS_DIR}/lib/libavcodec.a"
  "${SEGUE_DEPS_DIR}/lib/libswresample.a"
  "${SEGUE_DEPS_DIR}/lib/libavutil.a"
  "${SEGUE_DEPS_DIR}/lib/libsamplerate.a"
  "${SEGUE_DEPS_DIR}/lib/libfftw3f.a"
  "${SEGUE_DEPS_DIR}/lib/libyaml-cpp.a"
  segue_onnxruntime
  log
  z)
