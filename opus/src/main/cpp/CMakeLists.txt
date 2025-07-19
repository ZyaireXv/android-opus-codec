# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.4.1)
project(easyopus)
# Creates and names a library, sets it as either STATIC or SHARED, and provides the relative paths to its source code.
add_library(
        easyopus
        SHARED
        codec/CodecOpus.cpp
        utils/SamplesConverter.cpp
        easyopus.cpp
)

include_directories(${PROJECT_SOURCE_DIR}/include)

#add other prebuilt libraries
add_library(opus SHARED IMPORTED)
add_library(opusenc SHARED IMPORTED)

set_target_properties(opus PROPERTIES IMPORTED_LOCATION ${PROJECT_SOURCE_DIR}/lib/${ANDROID_ABI}/libopus.so)
set_target_properties(opusenc PROPERTIES IMPORTED_LOCATION ${PROJECT_SOURCE_DIR}/lib/${ANDROID_ABI}/libopusenc.so)

# Find NDK log library
find_library(
        log-lib
        log
)

# Link libraries and set 16KB alignment for Android 15 compatibility
target_link_libraries(
        easyopus
        opus
        opusenc
        ${log-lib}
)

# 为 easyopus 添加 16KB 对齐
set_target_properties(easyopus PROPERTIES
        LINK_FLAGS "-Wl,-z,max-page-size=16384"
)

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

message(STATUS "Building easyopus with 16KB alignment for Android 15 compatibility")
