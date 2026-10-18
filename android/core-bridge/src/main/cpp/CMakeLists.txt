cmake_minimum_required(VERSION 3.22)
project(segue_bridge LANGUAGES C CXX)

get_filename_component(SEGUE_CORE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../../core" ABSOLUTE)
add_subdirectory(${SEGUE_CORE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/segue_core)

# JNI used only to generate baseline data with the legacy implementation (the Flutter app's).
# Remove together with the Flutter app.
add_library(segue_legacy_jni SHARED legacy_baseline_jni.cpp)
target_link_libraries(segue_legacy_jni PRIVATE segue_legacy)
