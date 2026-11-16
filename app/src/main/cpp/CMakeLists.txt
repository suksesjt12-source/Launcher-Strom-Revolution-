cmake_minimum_required(VERSION 3.22.1)
project(storm_engine LANGUAGES CXX)

add_library(storm_engine SHARED storm_engine.cpp)

find_library(android-lib android)
find_library(log-lib log)
find_library(vulkan-lib vulkan)

target_compile_features(storm_engine PRIVATE cxx_std_17)
target_compile_options(storm_engine PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(storm_engine PRIVATE ${android-lib} ${log-lib} ${vulkan-lib})
