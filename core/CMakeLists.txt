cmake_minimum_required(VERSION 3.22)
project(segue_core LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(${CMAKE_CURRENT_LIST_DIR}/cmake/SegueDeps.cmake)

add_library(segue_core STATIC
  src/init.cpp
  src/decoder.cpp
  src/feature_accumulator.cpp
  src/spectrum.cpp
  src/visuals_file.cpp
  src/audio_api.cpp
  src/models.cpp
  src/analysis.cpp
  src/analyze_api.cpp)
target_include_directories(segue_core
  PUBLIC ${CMAKE_CURRENT_LIST_DIR}/include
  PRIVATE ${CMAKE_CURRENT_LIST_DIR}/src)
target_link_libraries(segue_core PUBLIC segue_deps)

# Legacy API (same sources as the Flutter app). Used only to generate the baseline data;
# remove together with the Flutter app.
add_library(segue_legacy STATIC
  legacy/src/essentia_bridge.cpp
  legacy/src/audio_decode.cpp
  legacy/src/style_classifier.cpp
  legacy/src/spectrum_analyzer.cpp)
target_include_directories(segue_legacy PUBLIC ${CMAKE_CURRENT_LIST_DIR}/legacy/src)
target_link_libraries(segue_legacy PUBLIC segue_deps)
