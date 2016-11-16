/*
 * Copyright (c) 2012, IETR/INSA of Rennes
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the IETR/INSA of Rennes nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
package turnus.orcc.profiler.dynamic.numa.backend.templates

import java.util.Map
import net.sf.orcc.backends.CommonPrinter
import net.sf.orcc.df.Actor
import net.sf.orcc.df.Network
import net.sf.orcc.util.FilesManager

import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_FOLDER

/**
 * Generate CMakeList.txt content
 * 
 * @author Antoine Lorence
 * @author Manuel Selva
 */
class CMakePrinter extends CommonPrinter {

	protected var Network network
	protected String linkNativeLibFolder;

	new(Network network) {
		this.network = network;
		this.linkNativeLibFolder = null;
	}

	new(Network network, Map<String, Object> options) {
		this.network = network;
		this.setOptions(options);
	}

	def setNetwork(Network network) {
		this.network = network
	}

	override setOptions(Map<String, Object> options) {
		super.setOptions(options)

		if(options.containsKey(LINK_NATIVE_LIBRARY_FOLDER.longName())) {
			linkNativeLibFolder = options.get(LINK_NATIVE_LIBRARY_FOLDER.longName()) as String;
		}
	}

	def rootCMakeContent() '''
		# Generated from «network.simpleName»

		cmake_minimum_required (VERSION 2.6)

		project («network.simpleName»)

		# Configure ouput folder for generated binary
		set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/bin)

		# Definitions configured and used in subdirectories
		set(extra_definitions)
		set(extra_includes)
		set(extra_libraries)

		if(CMAKE_COMPILER_IS_GNUCC)
			set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -DLINUX -D_GNU_SOURCE -mcmodel=large")
		endif()

		«IF linkNativeLibFolder != null && !linkNativeLibFolder.isEmpty()»
			# Native lib
			set(external_definitions)
			set(external_include_paths)
			set(external_library_paths)
			set(external_libraries)
			
			# All external vars should be set by the CMakeLists.txt inside the following folder.
			add_subdirectory(«linkNativeLibFolder» «linkNativeLibFolder»)
			
			if(external_definitions)
				list(APPEND extra_definitions ${external_definitions})
			endif()

			if(external_include_paths)
				list(APPEND extra_includes ${external_include_paths})
			endif()
			
			if(external_libraries)
				list(APPEND extra_libraries ${external_libraries})
			endif()
			
			if(external_library_paths)
				link_directories(${external_library_paths})
			endif()
			
		«ENDIF»
		# Runtime libraries inclusion
		include_directories(
			libs/numap/include
			${PROJECT_BINARY_DIR}/libs/orcc-libs # to find orcc_config.h
			libs/orcc-libs/orcc-native/include
			libs/orcc-libs/orcc-runtime/include
		)

		«addLibrariesSubdirs»
	'''

	/**
	 * Goal of this method is to allow text produced to be extended
	 * for specific usages (other backends)
	 */
	def protected addLibrariesSubdirs() '''
		# Compile required libs
		add_subdirectory(libs/numap)
		add_subdirectory(libs/orcc-libs)
		
		# Compile application
		add_subdirectory(src)
	'''

	def srcCMakeContent() '''
		# Generated from «network.simpleName»

		set(filenames
			«network.simpleName».c
			«FOR child : network.children.actorInstances.filter[!getActor.native]»
				«child.label».c
			«ENDFOR»
			«FOR child : network.children.filter(typeof(Actor)).filter[!native]»
				«child.label».c
			«ENDFOR»
		)

		include_directories(${extra_includes})
		add_definitions(${extra_definitions})
		add_executable(«network.simpleName» ${filenames})

		# Build library without any external library required
		target_link_libraries(«network.simpleName» orcc-native orcc-runtime ${extra_libraries})
	'''
	
	def printRootCMake(String targetFolder) {
		val content = rootCMakeContent
		FilesManager.writeFile(content, targetFolder, "CMakeLists.txt")
	}
	
	def printSrcCMake(String targetFolder) {
		val content = srcCMakeContent
		FilesManager.writeFile(content, targetFolder, "CMakeLists.txt")
	}	
}
