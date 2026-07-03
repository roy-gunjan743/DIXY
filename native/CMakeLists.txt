cmake_minimum_required(VERSION 3.20)

project(virtual_deadlock_agent)

set(CMAKE_CXX_STANDARD 17)

# JAVA HOME
set(JAVA_HOME "C:/Users/Manoj PC/.jdks/jbr-21.0.11")

include_directories(
    "${JAVA_HOME}/include"
    "${JAVA_HOME}/include/win32"
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
)

add_library(
    virtual_deadlock_agent
    SHARED
    src/agent.cpp
)