cmake_minimum_required(VERSION 3.22)
project(offhand_llama CXX)

# llama.cpp is vendored (git clone, tag b4658) at third_party/llama.cpp —
# not committed; see README. ATTRIBUTION.md records the tag and license.
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")

if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "third_party/llama.cpp missing — clone it first (tag b4658), see README")
endif()

set(BUILD_SHARED_LIBS OFF)
set(LLAMA_CURL OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_COMMON OFF)
set(GGML_OPENMP OFF)
set(GGML_NATIVE OFF)

add_subdirectory(${LLAMA_DIR} llama-build)

add_library(offhand_llama SHARED offhand_llama.cpp)
target_compile_features(offhand_llama PRIVATE cxx_std_17)
target_link_libraries(offhand_llama PRIVATE llama android log)
