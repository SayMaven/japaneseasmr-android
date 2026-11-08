cmake_minimum_required(VERSION 3.22.1)
project("native-audio-engine")

set(CMAKE_CXX_STANDARD 17)

# Oboe prefab package from com.google.oboe:oboe
find_package(oboe REQUIRED CONFIG)

# Android system libraries
find_library(log-lib log)
find_library(media-lib mediandk)
find_library(android-lib android)

add_library(
    native-audio-engine
    SHARED
    native-lib.cpp
    AudioEngine.cpp
    AudioDecoder.cpp
    UsbAudioStreamer.cpp
)

target_include_directories(
    native-audio-engine
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    native-audio-engine
    oboe::oboe
    ${log-lib}
    ${media-lib}
    ${android-lib}
)

