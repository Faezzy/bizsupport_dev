cmake_minimum_required(VERSION 3.14)
project(bizsupport_cpp VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# --------------------------------------------------------------------------
# FetchContent: cpp-httplib and nlohmann/json
# --------------------------------------------------------------------------
include(FetchContent)

FetchContent_Declare(
    httplib
    GIT_REPOSITORY https://github.com/yhirose/cpp-httplib.git
    GIT_TAG        v0.15.3
)

FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
)

FetchContent_MakeAvailable(httplib nlohmann_json)

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
    ${PQXX_INCLUDE_DIRS}
    ${CMAKE_SOURCE_DIR}/src
)

target_link_libraries(bizsupport PRIVATE
    httplib::httplib
    nlohmann_json::nlohmann_json
    OpenSSL::SSL
    OpenSSL::Crypto
    ${PQXX_LIBRARIES}
    ${PQ_LIBRARY}
    Threads::Threads
)
