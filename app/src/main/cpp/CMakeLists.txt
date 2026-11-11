cmake_minimum_required(VERSION 3.22.1)
project(cuepipe)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.9.5
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper_cpp)

add_library(cuepipe SHARED whisper_jni.c)
target_include_directories(cuepipe PRIVATE ${whisper_cpp_SOURCE_DIR}/include)
find_library(log-lib log)
target_link_libraries(cuepipe whisper ${log-lib})
set_target_properties(cuepipe PROPERTIES LINKER_LANGUAGE CXX)
target_compile_features(cuepipe PRIVATE c_std_11)
