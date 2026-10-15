cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

# Real backend: fetches pinned llama.cpp at configure time (needs network on first CI build).
# To keep local/light builds working, set -DLLAMA_REAL=OFF to compile the honest stub.
option(LLAMA_REAL "Build real llama.cpp backend" ON)
set(LLAMA_TAG "b11669" CACHE STRING "Pinned llama.cpp tag")

find_library(log-lib log)

if(LLAMA_REAL)
  include(FetchContent)
  FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
    GIT_TAG ${LLAMA_TAG}
    GIT_SHALLOW TRUE
  )
  # Keep the lib lean for mobile: no server, no examples, metal/cuda off (CPU + NNAPI path only).
  set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
  set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
  FetchContent_MakeAvailable(llama)

  add_library(llama_jni SHARED llama_jni.cpp)
  target_compile_definitions(llama_jni PRIVATE LLAMA_REAL_BACKEND=1)
  target_link_libraries(llama_jni llama common ${log-lib})
else()
  # Honest stub: init returns 0 so Kotlin reports engine-unavailable instead of faking text.
  add_library(llama_jni SHARED llama_jni.cpp)
endif()

target_link_libraries(llama_jni ${log-lib})
