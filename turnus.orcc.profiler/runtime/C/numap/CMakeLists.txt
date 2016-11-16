cmake_minimum_required(VERSION 2.6)

# numa library files
set(numap_sources
	src/numap.c
	src/numap_analyse.c
)

set(numap_headers
    include/numap.h
)

add_definitions(-fpic)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Compile libraries
add_library(numap STATIC ${numap_sources} ${numap_headers})
# Do the linking
target_link_libraries(numap numa pfm)

install(TARGETS numap
    ARCHIVE DESTINATION lib
)

get_directory_property(hasParent PARENT_DIRECTORY)
list(APPEND extra_definitions -fpic)
list(APPEND extra_includes ${CMAKE_CURRENT_SOURCE_DIR}/include)
list(APPEND extra_libraries numap numa pfm)
if(hasParent)
	set(extra_definitions ${extra_definitions} PARENT_SCOPE)
	set(extra_includes ${extra_includes} PARENT_SCOPE)
	set(extra_libraries ${extra_libraries} PARENT_SCOPE)
endif()	
