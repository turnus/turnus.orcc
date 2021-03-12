/* 
 * TURNUS - www.turnus.co
 * 
 * Copyright (C) 2010-2016 EPFL SCI STI MM
 *
 * This file is part of TURNUS.
 *
 * TURNUS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TURNUS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TURNUS.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7
 * 
 * If you modify this Program, or any covered work, by linking or combining it
 * with Eclipse (or a modified version of Eclipse or an Eclipse plugin or 
 * an Eclipse library), containing parts covered by the terms of the 
 * Eclipse Public License (EPL), the licensors of this Program grant you 
 * additional permission to convey the resulting work.  Corresponding Source 
 * for a non-source form of such a combination shall include the source code 
 * for the parts of Eclipse libraries used as well as that of the  covered work.
 * 
 */
package turnus.orcc.profiler.dynamic.execution.backend.templates

import java.util.Map
import net.sf.orcc.df.Actor
import net.sf.orcc.df.Network
import net.sf.orcc.util.FilesManager
import java.io.File
import java.util.HashSet
import net.sf.orcc.ir.Var

/**
 * CPP Network main printer
 * 
 * @author Endri Bezati
 */
class CppNetwork extends ExprAndTypePrinter {

	protected Network network
	
	protected String traceFile
	
	protected String traceFileInfo

	new(Network network, Map<String, Object> options) {
		this.network = network
		if (options.containsKey("execution_data_path")){
			traceFile = options.get("execution_data_path") as String + File.separator + "executiondata.etracez"
			traceFileInfo = options.get("execution_data_path") + File.separator + "executiondata.info"
		}
		
	}

	def printMain(String targetFolder) {
		val content = compileMain
		FilesManager.writeFile(content, targetFolder, "main.cpp")
	}

	def compileMain() '''
		// -- CPP Lib Headers
		#include <iostream>
		#include <fstream>
		#include "get_opt.h"
		#include "actor.h"
		#include "fifo.h"
		#include "Tracer.h"
		
		// -- Actors Headers
		«FOR actor : network.children.filter(typeof(Actor))»
			#include "«actor.name».h"
		«ENDFOR»
		
		long firingId;
		
		«IF network.hasAttribute("network_shared_variables")»
			// -- Shared Variables
			«FOR v : network.getAttribute("network_shared_variables").objectValue as HashSet<Var>»
				«v.type.doSwitch» «v.name»«FOR dim : v.type.dimensions»[«dim»]«ENDFOR»;
			«ENDFOR»
		«ENDIF»
		
		
		int main(int argc, char *argv[]){
			
			Tracer *t = new Tracer("«traceFile»");
			
			// Actors
			«FOR actor : network.children.filter(typeof(Actor))»
				«actor.name» *act_«actor.name» = new «actor.name»(«FOR variable : actor.parameters SEPARATOR ", "»«variable.initialValue.doSwitch»«ENDFOR»);
			«ENDFOR»
			
			// FIFO Queues
			«FOR actor : network.children.filter(typeof(Actor))»
				«FOR edges : actor.outgoingPortMap.values»
					Fifo<«edges.get(0).sourcePort.type.doSwitch», «edges.get(0).getAttribute("nbReaders").objectValue»> *fifo_«edges.get(0).getAttribute("idNoBcast").objectValue» = new Fifo<«edges.get(0).sourcePort.type.doSwitch», «edges.get(0).getAttribute("nbReaders").objectValue»>«IF edges.get(0).size !== null»(«edges.get(0).size»)«ENDIF»;
				«ENDFOR»
			«ENDFOR»
			
			// Connections
			«FOR e : network.connections»
				act_«(e.source as Actor).name»->port_«e.sourcePort.name» = fifo_«e.getAttribute("idNoBcast").objectValue»;
				act_«(e.target as Actor).name»->port_«e.targetPort.name» = fifo_«e.getAttribute("idNoBcast").objectValue»;
			«ENDFOR»
			
			// Profiler
			«FOR actor : network.children.filter(typeof(Actor))»
					act_«actor.name»->set_tracer(t);
			«ENDFOR»
			
			// -- Get Input Arguments
			GetOpt options = GetOpt(argc, argv);
			options.getOptions();
			
			firingId = 0;
			
			// -- Initialize Actors
			«FOR actor : network.children.filter(typeof(Actor))»
				act_«actor.name»->initialize();
			«ENDFOR»
		
			// -- Run 
			EStatus status = None;
			do{
				status = None;
				«FOR actor : network.children.filter(typeof(Actor))»
					act_«actor.name»->action_selection(status);
				«ENDFOR»
			}while (status != None);
		
		
			«FOR actor : network.children.filter(typeof(Actor))»
				//delete act_«actor.name»;
			«ENDFOR»
			
			«FOR actor : network.children.filter(typeof(Actor))»
				«FOR edges : actor.outgoingPortMap.values»
					//delete fifo_«edges.get(0).getAttribute("idNoBcast").objectValue»;
				«ENDFOR»
			«ENDFOR»
			
			std::ofstream trace_profiling_info;
			trace_profiling_info.open("«traceFileInfo»");
			trace_profiling_info << "firings=" << firingId << "\n";
			trace_profiling_info.close();
			delete t;
		
			return 0;
		
			//EOF
		}
	'''

	def compileParameters() '''«FOR param : network.parameters SEPARATOR ", "»«param.type.doSwitch» «FOR dim : param.
		type.dimensions»*«ENDFOR»«param.name»«ENDFOR»'''

	def compileCmakeLists() '''
			cmake_minimum_required (VERSION 2.8)
		
			project («network.simpleName»)
		
			find_package(Threads REQUIRED)
		
			if(MSVC)
			set(CMAKE_CXX_FLAGS_DEBUG "/D_DEBUG /MTd /ZI /Ob0 /Od /RTC1")
			set(CMAKE_CXX_FLAGS_RELEASE "/MT /O2 /Ob2 /D NDEBUG")
			endif()
		
			if(CMAKE_COMPILER_IS_GNUCXX)
			set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O0 -g")
			set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3")
			endif()
			
			# Use this flag if unsigned / signed conversions produces errors
			# set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fpermissive")
		
			set(EMBEDDEDCPP_INCLUDE_DIR ./lib/include)
			subdirs(./lib)
		
			include_directories(${EMBEDDEDCPP_INCLUDE_DIR})
		
			add_executable ( «network.simpleName»
			src/main.cpp
			src/«network.simpleName».h
			«FOR instance : network.children.filter(typeof(Actor))»
				src/«instance.name».h
			«ENDFOR»
			)
		
			set(libraries EmbeddedCPP)
			
			
			set(libraries ${libraries} ${CMAKE_THREAD_LIBS_INIT})
			target_link_libraries(«network.simpleName» ${libraries})
	'''

	def printCMakeLists(String targetFolder) {
		val content = compileCmakeLists
		FilesManager.writeFile(content, targetFolder, "CMakeLists.txt")
	}

}
