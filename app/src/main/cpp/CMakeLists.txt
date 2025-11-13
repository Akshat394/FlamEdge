cmake_minimum_required(VERSION 3.10.2)

project(edge_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Allow users to pass OpenCV_DIR via environment or Gradle arguments
if(NOT DEFINED OpenCV_DIR AND DEFINED ENV{OpenCV_DIR})
    set(OpenCV_DIR $ENV{OpenCV_DIR})
endif()

add_library(edge_native SHARED
    native-lib.cpp
    processor.cpp
    processor.h
)

find_package(OpenCV REQUIRED COMPONENTS core imgproc)
if(NOT OpenCV_FOUND)
    message(FATAL_ERROR "OpenCV not found. Please set OpenCV_DIR to your OpenCV android sdk cmake directory, e.g.: <path>/OpenCV-android-sdk/sdk/native/jni/abi-arm64-v8a or similar")
endif()

target_include_directories(edge_native
    PRIVATE
        ${OpenCV_INCLUDE_DIRS}
)

target_link_libraries(edge_native
    ${OpenCV_LIBS}
    log
    jnigraphics
)

