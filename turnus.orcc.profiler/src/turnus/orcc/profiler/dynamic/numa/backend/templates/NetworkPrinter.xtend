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
 * about
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
 * 
 * Modified by Junaid Jameel Ahmad (EPFL)
 */
package turnus.orcc.profiler.dynamic.numa.backend.templates

import java.util.Map
import java.util.HashMap
import net.sf.orcc.backends.c.CTemplate
import net.sf.orcc.df.Connection
import net.sf.orcc.df.Entity
import net.sf.orcc.df.Network
import net.sf.orcc.df.Port
import net.sf.orcc.graph.Vertex
import net.sf.orcc.util.FilesManager

import static net.sf.orcc.backends.BackendsConstants.*
import net.sf.orcc.df.Actor
import java.util.List
import java.util.Arrays
import java.util.HashSet
import net.sf.orcc.ir.Var

/**
 * Generate and print network source file for C backend.
 *  
 * @author Antoine Lorence
 * @author Manuel Selva
 * 
 */
class NetworkPrinter extends CTemplate {
	
	protected var Network network;
	
	protected var boolean newSchedul = false
	
	protected var numaProfilerExitStatus = 0;
	var boolean numaProfiler = true
	
	var HashMap<Integer, List<String>> fifoMappingInfo = new HashMap<Integer, List<String>>

	new(Network network, Map<String, Object> options) {
		this.network = network
		this.numaProfilerExitStatus = 0
		
		setOptions(options)
	}

	override setOptions(Map<String, Object> options) {
		super.setOptions(options)
		if (options.containsKey(NEW_SCHEDULER)) {
			this.newSchedul = options.get(NEW_SCHEDULER) as Boolean
		}
	}
	def printNetwork(String targetFolder) {
		val content = getNetworkFileContent
		FilesManager.writeFile(content, targetFolder, network.simpleName+".c")
	}
	
	def getNumaProfilerExitStatus() {
		var numExitActions = 0
		if(this.numaProfiler) {
			for( child : this.network.children) {
				if(child instanceof Actor) {
					numExitActions = child.actions.filter[hasAttribute("numaProfilerExit")].length
					if(numExitActions >= 1) {
						this.numaProfilerExitStatus += numExitActions
					}
				}
			}
		}
		return this.numaProfilerExitStatus
	}

	def protected getNetworkFileContent() '''
		// Generated from "«network.name»"

		#include <locale.h>
		#include <stdio.h>
		#include <stdlib.h>
		«printAdditionalIncludes»

		#include "types.h"
		#include "fifo.h"
		#include "util.h"
		#include "dataflow.h"
		#include "serialize.h"
		#include "options.h"
		#include "scheduler.h"
		«IF numaProfiler»
		// Numa profiler declarations.
		int nb_threads;
		«ENDIF»

		#define SIZE «fifoSize»

		/////////////////////////////////////////////////
		// FIFO allocation
		«FOR child : network.children»
			«child.allocateFifos»
		«ENDFOR»

		/////////////////////////////////////////////////
		// FIFO pointer assignments
		«FOR child : network.children»
			«child.assignFifo»
		«ENDFOR»

		«additionalDeclarations»
		/////////////////////////////////////////////////
		// Actor functions
		«FOR child : network.children»
			extern void «child.label»_initialize(schedinfo_t *si);
			extern void «child.label»_scheduler(schedinfo_t *si);
		«ENDFOR»

		/////////////////////////////////////////////////
		// Declaration of the actors array
		«FOR child : network.children»
			actor_t «child.label» = {"«child.label»", «child.label»_initialize, «child.label»_scheduler, 0, 0, 0, 0, NULL, -1, «network.children.indexOf(child)», 0, 1, 0, 0, 0, NULL, 0, 0, "", 0, 0, 0};
		«ENDFOR»

		actor_t *actors[] = {
			«FOR child : network.children SEPARATOR ","»
				&«child.label»
			«ENDFOR»
		};

		/////////////////////////////////////////////////
		// Declaration of the connections array
		«FOR connection : network.connections»
			connection_t connection_«connection.target.label»_«connection.targetPort.name» = {&«connection.source.label», &«connection.target.label», 0, 0};
		«ENDFOR»

		connection_t *connections[] = {
			«FOR connection : network.connections SEPARATOR ","»
			    &connection_«connection.target.label»_«connection.targetPort.name»
			«ENDFOR»
		};

		/////////////////////////////////////////////////
		// Declaration of the network
		network_t network = {"«network.name»", actors, connections, «network.allActors.size», «network.connections.size»};
		
		«IF network.hasAttribute("network_shared_variables")»
			/////////////////////////////////////////////////
			// Shared Variables
			«FOR v : network.getAttribute("network_shared_variables").objectValue as HashSet<Var>»
				«v.type.doSwitch» «v.name»«FOR dim : v.type.dimensions»[«dim»]«ENDFOR»;
			«ENDFOR»

		«ENDIF»
		////////////////////////////////////////////////////////////////////////////////
		// Main
		int main(int argc, char *argv[]) {
			«beforeMain»
			
			options_t *opt = init_orcc(argc, argv);
			«IF numaProfiler»
			nb_threads = opt->nb_processors;
			«ENDIF»			
			set_scheduling_strategy(«IF !newSchedul»"RR"«ELSE»"DD"«ENDIF», opt);
			
			launcher(opt, &network);
			«afterMain»
			
			return compareErrors;
		}
	'''
	
	def protected assignFifo(Vertex vertex) '''
		«FOR connList : vertex.getAdapter(typeof(Entity)).outgoingPortMap.values»
			«printFifoAssign(connList.head.source.label, connList.head.sourcePort, connList.head.<Integer>getValueAsObject("idNoBcast"))»
			«fifoMappingInfoAssign(connList)»
			«FOR conn : connList»
				«printFifoAssign(conn.target.label, conn.targetPort, conn.<Integer>getValueAsObject("idNoBcast"))»
			«ENDFOR»
			
		«ENDFOR»
	'''
	
	def protected printFifoAssign(String name, Port port, int fifoIndex) '''
		fifo_«port.type.doSwitch»_t *fifo_«name»_«port.name» = &fifo_«fifoIndex»;
	'''

	def fifoMappingInfoAssign(List<Connection> connList) {
		var List<String> fifoInfo = Arrays.asList(connList.head.source.label, connList.head.sourcePort.label, connList.head.target.label, connList.head.targetPort.label);
		fifoMappingInfo.put(connList.head.<Integer>getValueAsObject("idNoBcast"), fifoInfo);
	}
	
	def public HashMap<Integer, List<String>> getFifoMappingInfo() {
		return fifoMappingInfo;
	}	

	def protected allocateFifos(Vertex vertex) '''
		«FOR connectionList : vertex.getAdapter(typeof(Entity)).outgoingPortMap.values»
			«allocateFifo(connectionList.get(0), connectionList.size)»
		«ENDFOR»
	'''
	
	def protected allocateFifo(Connection conn, int nbReaders) '''
		DECLARE_FIFO(«conn.sourcePort.type.doSwitch», «if (conn.size !== null) conn.size else "SIZE"», «conn.<Object>getValueAsObject("idNoBcast")», «nbReaders»)
	'''
	
	// This method can be override by other backends to print additional includes
	def protected printAdditionalIncludes() ''''''
	
	// This method can be override by other backends to print additional declarations 
	def protected additionalDeclarations() ''''''
	
	// This method can be override by other backends to print additional statements
	// when the program is terminating
	def protected additionalAtExitActions()''''''
	// This method can be override by other backends in case of calling additional 
	// functions before and after the Main function
	def protected afterMain() ''''''
	def protected beforeMain() '''
	'''
}
