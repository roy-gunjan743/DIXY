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

# CMAKE_SYSTEM_PROCESSOR reports "AMD64" (uppercase) on Windows, but Java's
# os.arch reports "amd64" (lowercase) — since jar resource lookups are
# case-sensitive even on Windows, normalize the arch segment to lowercase so
# NativeLoader.java's resourcePath actually matches this output folder.
string(TOLOWER "${CMAKE_SYSTEM_PROCESSOR}" VDD_ARCH)

set_target_properties(virtual_deadlock_agent PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY
    "${CMAKE_SOURCE_DIR}/../src/main/resources/native/${CMAKE_SYSTEM_NAME}-${VDD_ARCH}"
    RUNTIME_OUTPUT_DIRECTORY
    "${CMAKE_SOURCE_DIR}/../src/main/resources/native/${CMAKE_SYSTEM_NAME}-${VDD_ARCH}"
)

# Multi-config generators (Visual Studio) ignore the plain
# LIBRARY/RUNTIME_OUTPUT_DIRECTORY above and append a Debug/Release
# subfolder unless the per-configuration variants are set too — this
# collapses everything to the same flat folder NativeLoader.java expects,
# regardless of build config.
foreach(VDD_CONFIG IN ITEMS DEBUG RELEASE RELWITHDEBINFO MINSIZEREL)
    set_target_properties(virtual_deadlock_agent PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY_${VDD_CONFIG}
        "${CMAKE_SOURCE_DIR}/../src/main/resources/native/${CMAKE_SYSTEM_NAME}-${VDD_ARCH}"
        RUNTIME_OUTPUT_DIRECTORY_${VDD_CONFIG}
        "${CMAKE_SOURCE_DIR}/../src/main/resources/native/${CMAKE_SYSTEM_NAME}-${VDD_ARCH}"
    )
endforeach()