cmake_minimum_required(VERSION 3.18)
project(essentia_bridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(essentia_bridge SHARED
    src/essentia_bridge.cpp
    src/audio_decode.cpp
    src/style_classifier.cpp
)

target_include_directories(essentia_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

set(LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libs/android/${ANDROID_ABI})

target_link_libraries(essentia_bridge PRIVATE
    ${LIBS_DIR}/libessentia.a
    ${LIBS_DIR}/libavcodec.a
    ${LIBS_DIR}/libavformat.a
    ${LIBS_DIR}/libavutil.a
    ${LIBS_DIR}/libswresample.a
    ${LIBS_DIR}/libfftw3f.a
    ${LIBS_DIR}/libyaml-cpp.a
)

if(ANDROID)
    target_link_libraries(essentia_bridge PRIVATE
        log
        z
    )
endif()

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set_target_properties(essentia_bridge PROPERTIES
        LINK_FLAGS "-s"
    )
endif()
