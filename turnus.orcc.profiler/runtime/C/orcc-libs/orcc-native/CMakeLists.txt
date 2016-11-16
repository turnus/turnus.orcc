
# Orcc library files
set(orcc_native_sources
    src/access_file.c
    src/source.c
    src/writer.c
    src/compare.c
    src/compareyuv.c
    src/native.c
    src/native_util.c
)

set(orcc_native_headers
    include/native.h
    include/native_util.h
)

if(THREADS_FOUND)
    list(APPEND orcc_native_sources src/thread.c)
    list(APPEND orcc_native_headers include/thread.h)
endif()

# Helps CMake to find SDL libraries (Windows only)
if(WIN32)
    # Detect current architecture. Depending on CMAKE_LIBRARY_ARCHITECTURE value,
    # CMake will search in lib/x64 or lib/x86 sub-folders to find SDL.lib
    if(CMAKE_SIZEOF_VOID_P EQUAL 8)
        set(CMAKE_LIBRARY_ARCHITECTURE "x64")
    else()
        set(CMAKE_LIBRARY_ARCHITECTURE "x86")
    endif()
    message(STATUS "Architecture: ${CMAKE_LIBRARY_ARCHITECTURE}")

    # Configure additional paths to search for libraries
    set(CMAKE_PREFIX_PATH
        ${CMAKE_PREFIX_PATH}
        ${CMAKE_CURRENT_SOURCE_DIR}/../windows/SDL-*
    )
endif()

# Try to find SDL on the user system
find_package(SDL QUIET)

if(SDL_FOUND)
    list(APPEND extra_includes ${SDL_INCLUDE_DIR})
    list(APPEND extra_libraries ${SDL_LIBRARY})
    list(APPEND orcc_native_sources
        src/display_sdl.c
        src/audio_sdl.c
        src/framerate_sdl.c
    )
else()
    list(APPEND orcc_native_sources
        src/nodisplay.c
        src/framerate.c
    )
endif()

include_directories(${extra_includes})
add_definitions(${extra_definitions})
# Compile libraries
add_library(orcc-native STATIC ${orcc_native_sources} ${orcc_native_headers})
# Do the linking
target_link_libraries(orcc-native orcc-runtime ${extra_libraries})

install(TARGETS orcc-native
    ARCHIVE DESTINATION lib
)

set(extra_definitions ${extra_definitions} PARENT_SCOPE)
set(extra_includes ${extra_includes} PARENT_SCOPE)
set(extra_libraries ${extra_libraries} PARENT_SCOPE)
