# Bibliothèque chargée via LD_PRELOAD dans le processus de patch pour relever la limite
# de mémoire d'ART (dalvik.vm.heapsize), comme ReVanced Manager.
cmake_minimum_required(VERSION 3.22.1)
project("prop_override")
add_library(${CMAKE_PROJECT_NAME} SHARED prop_override.cpp)
target_link_libraries(${CMAKE_PROJECT_NAME} android log)
