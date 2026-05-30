cmake_minimum_required(VERSION 3.14)
project(bizsupport_cpp VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --------------------------------------------------------------------------
# Build accelerators (opt-in: only activated when tools are installed)
# --------------------------------------------------------------------------
find_program(CCACHE_PROGRAM ccache)
if(CCACHE_PROGRAM)
    set_property(GLOBAL PROPERTY RULE_LAUNCH_COMPILE "${CCACHE_PROGRAM}")
    message(STATUS "ccache enabled: ${CCACHE_PROGRAM}")
endif()

# Prefer Ninja if available and no generator was specified explicitly
if(NOT CMAKE_GENERATOR)
    find_program(NINJA_PROGRAM ninja)
    if(NINJA_PROGRAM)
        message(STATUS "Hint: re-configure with -G Ninja for faster builds")
    endif()
endif()

# --------------------------------------------------------------------------
# Third-party headers (vendored in third_party/)
# --------------------------------------------------------------------------
set(THIRD_PARTY_DIR "${CMAKE_SOURCE_DIR}/third_party")
if(NOT EXISTS "${THIRD_PARTY_DIR}/httplib.h" OR
   NOT EXISTS "${THIRD_PARTY_DIR}/nlohmann/json.hpp")
    message(FATAL_ERROR
        "Vendored headers missing. Expected ${THIRD_PARTY_DIR}/httplib.h "
        "and ${THIRD_PARTY_DIR}/nlohmann/json.hpp")
endif()

# --------------------------------------------------------------------------
# System packages
# --------------------------------------------------------------------------
find_package(OpenSSL REQUIRED)
find_package(Threads REQUIRED)

# libpqxx -- try pkg-config first, then manual fallback
find_package(PkgConfig QUIET)
if(PkgConfig_FOUND)
    pkg_check_modules(PQXX QUIET libpqxx)
endif()

if(NOT PQXX_FOUND)
    message(STATUS "pkg-config did not find libpqxx, trying manual detection")

    find_library(PQXX_LIBRARY NAMES pqxx)
    find_path(PQXX_INCLUDE_DIR NAMES pqxx/pqxx PATH_SUFFIXES include)

    if(PQXX_LIBRARY AND PQXX_INCLUDE_DIR)
        set(PQXX_FOUND TRUE)
        set(PQXX_LIBRARIES ${PQXX_LIBRARY})
        set(PQXX_INCLUDE_DIRS ${PQXX_INCLUDE_DIR})
        message(STATUS "Found libpqxx manually: ${PQXX_LIBRARY}")
    else()
        message(FATAL_ERROR "Could not find libpqxx. Install libpqxx-dev.")
    endif()
endif()

find_library(PQ_LIBRARY NAMES pq)
if(NOT PQ_LIBRARY)
    message(FATAL_ERROR "Could not find libpq. Install libpq-dev.")
endif()

# --------------------------------------------------------------------------
# Application target
# --------------------------------------------------------------------------
file(GLOB_RECURSE SOURCES "src/*.cpp")

add_executable(bizsupport ${SOURCES})

target_include_directories(bizsupport PRIVATE
    ${THIRD_PARTY_DIR}
    ${PQXX_INCLUDE_DIRS}
    ${CMAKE_SOURCE_DIR}/src
)

target_link_libraries(bizsupport PRIVATE
    OpenSSL::SSL
    OpenSSL::Crypto
    ${PQXX_LIBRARIES}
    ${PQ_LIBRARY}
    Threads::Threads
)

# Precompiled header for the heaviest includes — biggest single win on
# incremental builds (httplib.h alone is ~9k lines of templates).
target_precompile_headers(bizsupport PRIVATE
    <httplib.h>
    <nlohmann/json.hpp>
    <pqxx/pqxx>
    <string>
    <vector>
    <optional>
    <memory>
    <unordered_map>
)
