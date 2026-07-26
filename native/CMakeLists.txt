cmake_minimum_required(VERSION 3.20)

project(virtual_deadlock_agent)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

include_directories(
    ${JNI_INCLUDE_DIRS}
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
)

add_library(
    virtual_deadlock_agent
    SHARED
    src/agent.cpp
)

target_link_libraries(virtual_deadlock_agent PRIVATE ${JNI_LIBRARIES})
