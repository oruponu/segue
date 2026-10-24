# Legacy API (libessentia_bridge.so) loaded by the Flutter app through dart:ffi.
# Remove together with the Flutter app.
cmake_minimum_required(VERSION 3.18)
project(essentia_bridge LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(${CMAKE_CURRENT_LIST_DIR}/../cmake/SegueDeps.cmake)

add_library(essentia_bridge SHARED
  src/essentia_bridge.cpp
  src/audio_decode.cpp
  src/style_classifier.cpp
  src/spectrum_analyzer.cpp)
target_include_directories(essentia_bridge PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(essentia_bridge PRIVATE segue_deps)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
  set_target_properties(essentia_bridge PROPERTIES LINK_FLAGS "-s")
endif()
