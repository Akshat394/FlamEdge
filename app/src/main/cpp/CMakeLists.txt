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

find_package(OpenCV COMPONENTS core imgproc)
if(OpenCV_FOUND)
    target_compile_definitions(edge_native PRIVATE HAS_OPENCV=1)
else()
    message(WARNING "OpenCV not found. Building with NO_OPENCV stub; native processing will be disabled.")
    target_compile_definitions(edge_native PRIVATE NO_OPENCV=1)
endif()

if(OpenCV_FOUND)
    target_include_directories(edge_native
        PRIVATE
            ${OpenCV_INCLUDE_DIRS}
    )
endif()

target_link_libraries(edge_native
    ${OpenCV_LIBS}
    log
    jnigraphics
)

