/*
 * Copyright (c) 2012, IETR/INSA of Rennes
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
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
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF YUSE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * 
 * Modified by Junaid Jameel Ahmad (EPFL)
 */
package turnus.orcc.profiler.dynamic.numa.backend.templates

import java.util.HashMap
import java.util.List
import java.util.Map
import net.sf.orcc.OrccRuntimeException
import net.sf.orcc.backends.c.CTemplate
import net.sf.orcc.backends.ir.BlockFor
import net.sf.orcc.backends.ir.InstTernary
import net.sf.orcc.df.Action
import net.sf.orcc.df.Actor
import net.sf.orcc.df.Connection
import net.sf.orcc.df.DfFactory
import net.sf.orcc.df.Instance
import net.sf.orcc.df.Pattern
import net.sf.orcc.df.Port
import net.sf.orcc.df.State
import net.sf.orcc.df.Transition
import net.sf.orcc.ir.Arg
import net.sf.orcc.ir.ArgByRef
import net.sf.orcc.ir.ArgByVal
import net.sf.orcc.ir.BlockBasic
import net.sf.orcc.ir.BlockIf
import net.sf.orcc.ir.BlockWhile
import net.sf.orcc.ir.ExprVar
import net.sf.orcc.ir.Expression
import net.sf.orcc.ir.InstAssign
import net.sf.orcc.ir.InstCall
import net.sf.orcc.ir.InstLoad
import net.sf.orcc.ir.InstReturn
import net.sf.orcc.ir.InstStore
import net.sf.orcc.ir.Param
import net.sf.orcc.ir.Procedure
import net.sf.orcc.ir.TypeList
import net.sf.orcc.ir.Var
import net.sf.orcc.util.Attributable
import net.sf.orcc.util.FilesManager
import net.sf.orcc.util.OrccLogger
import net.sf.orcc.util.util.EcoreHelper

import static net.sf.orcc.backends.BackendsConstants.*
import static net.sf.orcc.util.OrccAttributes.*
import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_FOLDER
import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_HEADERS
import static turnus.orcc.profiler.ProfilerOptions.NUMA_PROFILING_MODE
import java.util.HashSet

/**
 * Generate and print instance source file for C backend.
 *  
 * @author Antoine Lorence
 * @author Manuel Selva
 * 
 */
class InstancePrinter extends CTemplate {

	protected var Instance instance
	protected var Actor actor
	protected var Attributable attributable
	protected var Map<Port, Connection> incomingPortMap
	protected var Map<Port, List<Connection>> outgoingPortMap

	protected var String entityName
	
	var boolean checkArrayInbounds = false
	
	var boolean newSchedul = false
	
	var boolean isActionAligned = false
	
	var boolean debugActor = false
	var boolean debugAction = false
	
	var boolean numaProfiler = true
	var String numaProfileMode = "read"
	var boolean numaProfilerExit = false
	var Action numaProfilerExitAction = null
	var String numaProfilerExitCond = null	

	var String linkNativeLibFolder
	var String linkNativeLibHeaders
	
	var String executionDataPath = null

	protected val Pattern inputPattern = DfFactory::eINSTANCE.createPattern
	protected val Map<State, Pattern> transitionPattern = new HashMap<State, Pattern>

	protected var Action currentAction;

	new(Map<String, Object> options) {
		setOptions(options)
	}
	
	new(Instance instance, Map<String, Object> options) {
		setInstance(instance)
		setOptions(options)
	}

	new(Actor instance, Map<String, Object> options) {
		setActor(instance)
		setOptions(options)
	}
	
	def setInstance(Instance instance) {
		if (!instance.isActor) {
			throw new OrccRuntimeException("Instance " + entityName + " is not an Actor's instance")
		}

		this.instance = instance
		this.entityName = instance.name
		this.actor = instance.getActor
		this.attributable = instance
		this.incomingPortMap = instance.incomingPortMap
		this.outgoingPortMap = instance.outgoingPortMap

		setDebug
		buildInputPattern
		buildTransitionPattern
		initializeNumaProfilerOptions
	}

	def setActor(Actor actor) {
		this.entityName = actor.name
		this.actor = actor
		this.attributable = actor
		this.incomingPortMap = actor.incomingPortMap
		this.outgoingPortMap = actor.outgoingPortMap

		setDebug
		buildInputPattern
		buildTransitionPattern
		initializeNumaProfilerOptions
	}

	override setOptions(Map<String, Object> options) {
		super.setOptions(options)
		if (options.containsKey(CHECK_ARRAY_INBOUNDS)) {
			checkArrayInbounds = options.get(CHECK_ARRAY_INBOUNDS) as Boolean
		}

		if (options.containsKey(NEW_SCHEDULER)) {
			newSchedul = options.get(NEW_SCHEDULER) as Boolean
		}
		if(options.containsKey("execution_data_path")){
			executionDataPath = options.get("execution_data_path") as String
		}
		if(options.containsKey(NUMA_PROFILING_MODE.longName())){
			numaProfileMode = options.get(NUMA_PROFILING_MODE.longName()) as String
		}
		
		if(options.containsKey(LINK_NATIVE_LIBRARY_FOLDER.longName())) {
			linkNativeLibFolder = options.get(LINK_NATIVE_LIBRARY_FOLDER.longName()) as String;
		}		
		if(options.containsKey(LINK_NATIVE_LIBRARY_HEADERS.longName())) {
			linkNativeLibHeaders = options.get(LINK_NATIVE_LIBRARY_HEADERS.longName()) as String;
		}
	}

	def getInstanceContent(Instance instance) {
		setInstance(instance)
		fileContent
	}

	def getActorContent(Actor actor) {
		setActor(actor)
		fileContent
	}
	
	def printInstance(String targetFolder) {
		val content = fileContent
		FilesManager.writeFile(content, targetFolder, entityName + ".c")
	}
	
	def initializeNumaProfilerOptions() {
		if(numaProfiler && actor.actions.filter[hasAttribute("numaProfilerExit")].length > 0) {
			numaProfilerExit = true
			// Using only the very first action with @numaProfilerExit tag.			
			numaProfilerExitAction = actor.actions.filter[hasAttribute("numaProfilerExit")].get(0)			
			numaProfilerExitCond = numaProfilerExitAction.getAttribute("numaProfilerExit")?.getValueAsString("condition")
			if(numaProfilerExitCond === null || numaProfilerExitCond == "")
				numaProfilerExitCond = "1"
		}
		else
		{
			numaProfilerExit = false			
			numaProfilerExitAction = null
			numaProfilerExitCond === null
		}
	}	
	
	def protected getFileContent() {
	'''
		// Source file is "«actor.file»"
		
		#include <stdio.h>
		#include <stdlib.h>
		«printAdditionalIncludes»
		«IF checkArrayInbounds»
			#include <assert.h>
		«ENDIF»
		#include "orcc_config.h"

		#include "types.h"
		#include "fifo.h"
		#include "util.h"
		#include "scheduler.h"
		#include "dataflow.h"
		#include "cycle.h"
		«IF numaProfiler && numaProfilerExit»
		// NUMA profiler headers
		#include "numap.h"
		
		«ENDIF»
		«IF linkNativeLibHeaders !== null && !linkNativeLibHeaders.isEmpty()»
		«printNativeLibHeaders(linkNativeLibHeaders)»
		«ELSEIF linkNativeLibFolder !== null»
		«printNativeLibHeaders("HevcNativeShmSSE.h")»

		«ENDIF»
		#define SIZE «fifoSize»
		«IF instance !== null»
			«instance.printAttributes»
		«ELSE»
			«actor.printAttributes»
		«ENDIF»

		////////////////////////////////////////////////////////////////////////////////
		// Instance
		extern actor_t «entityName»;

		«IF actor.hasAttribute("actor_shared_variables")»
			////////////////////////////////////////////////////////////////////////////////
			// Shared Variables
			«FOR v : actor.getAttribute("actor_shared_variables").objectValue as HashSet<Var>»
				extern «v.type.doSwitch» «v.name»«FOR dim : v.type.dimensions»[«dim»]«ENDFOR»;
			«ENDFOR»
			
		«ENDIF»
		«IF !actor.inputs.nullOrEmpty»
			////////////////////////////////////////////////////////////////////////////////
			// Input FIFOs
			«FOR port : actor.inputs»
				«if (incomingPortMap.get(port) !== null) "extern "»fifo_«port.type.doSwitch»_t *«port.fullName»;
			«ENDFOR»

			////////////////////////////////////////////////////////////////////////////////
			// Input Fifo control variables
			«FOR port : actor.inputs»
				static unsigned int index_«port.name»;
				static unsigned int numTokens_«port.name»;
				#define SIZE_«port.name» «incomingPortMap.get(port).sizeOrDefaultSize»
				#define tokens_«port.name» «port.fullName»->contents
				
				«if (incomingPortMap.get(port) !== null) "extern "»connection_t connection_«entityName»_«port.name»;
				#define rate_«port.name» connection_«entityName»_«port.name».rate
				
			«ENDFOR»
			////////////////////////////////////////////////////////////////////////////////
			// Predecessors
			«FOR port : actor.inputs»
				«IF incomingPortMap.get(port) !== null»
					extern actor_t «incomingPortMap.get(port).source.label»;
				«ENDIF»
			«ENDFOR»

		«ENDIF»
		«IF !actor.outputs.filter[! native].nullOrEmpty»
			////////////////////////////////////////////////////////////////////////////////
			// Output FIFOs
			«FOR port : actor.outputs.filter[! native]»
				extern fifo_«port.type.doSwitch»_t *«port.fullName»;
			«ENDFOR»

			////////////////////////////////////////////////////////////////////////////////
			// Output Fifo control variables
			«FOR port : actor.outputs.filter[! native]»
				static unsigned int index_«port.name»;
				#define NUM_READERS_«port.name» «outgoingPortMap.get(port).size»
				#define SIZE_«port.name» «outgoingPortMap.get(port).get(0).sizeOrDefaultSize»
				#define tokens_«port.name» «port.fullName»->contents

			«ENDFOR»
			////////////////////////////////////////////////////////////////////////////////
			// Successors
			«FOR port : actor.outputs»
				«FOR successor : outgoingPortMap.get(port)»
					extern actor_t «successor.target.label»;
				«ENDFOR»
			«ENDFOR»

		«ENDIF»
		«IF (instance !== null && !instance.arguments.nullOrEmpty) || !actor.parameters.nullOrEmpty»
			////////////////////////////////////////////////////////////////////////////////
			// Parameter values of the instance
			«IF instance !== null»
				«FOR arg : instance.arguments»
					«IF arg.value.exprList»
						static «IF (arg.value.type as TypeList).innermostType.uint»unsigned «ENDIF»int «arg.variable.name»«arg.value.type.dimensionsExpr.printArrayIndexes» = «arg.value.doSwitch»;
					«ELSE»
						#define «arg.variable.name» «arg.value.doSwitch»
					«ENDIF»
				«ENDFOR»
			«ELSE»
				«FOR variable : actor.parameters»
					«variable.declare»
				«ENDFOR»
			«ENDIF»

		«ENDIF»
		«IF numaProfiler && numaProfilerExit»
			////////////////////////////////////////////////////////////////////////////////
			// Data structures for numaProfiler
			#define ExecDataPath "«executionDataPath»"
			extern struct numap_sampling_measure measures[MAX_THREAD_NB];
			extern int nb_threads;
			extern orcc_thread_t threads[MAX_THREAD_NB];
			uint64_t threads_stack_start[MAX_THREAD_NB];
			uint64_t threads_stack_end[MAX_THREAD_NB];
		«ENDIF»
		«IF !actor.stateVars.nullOrEmpty»
			////////////////////////////////////////////////////////////////////////////////
			// State variables of the actor
			«FOR variable : actor.stateVars.filter[!hasAttribute("shared")]»
				«variable.declare»
			«ENDFOR»

		«ENDIF»
		«IF actor.hasFsm»
			////////////////////////////////////////////////////////////////////////////////
			// Initial FSM state of the actor
			enum states {
				«FOR state : actor.fsm.states SEPARATOR ","»
					my_state_«state.name»
				«ENDFOR»
			};

			static char *stateNames[] = {
				«FOR state : actor.fsm.states SEPARATOR ","»
					"«state.name»"
				«ENDFOR»
			};

			static enum states _FSM_state;

		«ENDIF»
		
		«additionalDeclarations»
		
		«IF numaProfiler && numaProfilerExit»
			////////////////////////////////////////////////////////////////////////////////
			// NUMA Profiler utility functions
			
			int print_stack_info (char* file_name) {
				FILE *f = fopen(file_name, "a");
				int tid;
				
				if(f != NULL) {
					for(tid = 0; tid < nb_threads; tid++) {		
						fprintf(f, "Stack Info: tid = %d, start = %"PRIx64", end = %"PRIx64"\n", 
							tid, threads_stack_start[tid], threads_stack_end[tid]);
					}
					fclose(f);
					
					return 1;
				}
				else
				{
					fprintf(stderr, "Unable to open file to write stack info.\n");
					return -1;
				}
				
			}

			«IF numaProfileMode.equals("write")»
			int print_write_samples (struct numap_sampling_measure *measure, int tid, char* file_name) {
				FILE *f;
				int i, j;
				struct perf_event_mmap_page *metadata_page;
				struct perf_event_header *header;
				uint64_t head;
				uint64_t consumed;
			
				char *actor_found_name;
				char *action_found_name;
				uint64_t action_found_start;
				uint64_t action_found_end;
				char *fifo_found_src_name;
				char *fifo_found_dst_name;
				int action_found;
			
				f = fopen(file_name, "a");
			
				fprintf(f, "// New Thread: tid = %d\n", tid);
				metadata_page = measure->metadata_pages_per_tid[tid];
				head = metadata_page -> data_head;
				rmb();
				header = (struct perf_event_header *)((char *)metadata_page + measure->page_size);
				consumed = 0;

				// Parse all samples
				while (consumed < head) {
					if (header->size == 0) {
						fprintf(stderr, "Error: invalid header size = 0\n");
						return -1;
					}
					if (header -> type == PERF_RECORD_SAMPLE) {
						struct write_sample *sample = (struct write_sample *)((char *)(header) + 8);
						fprintf(f,  "ip = %" PRIx64
									", @ = %" PRIx64
									", src_level = %s\n",
									sample->ip, sample->addr, 
									get_data_src_level(sample->data_src));
					}
					consumed += header->size;
					header = (struct perf_event_header *)((char *)header + header -> size);
				}
				fprintf(f, "// End Thread: tid = %d\n", tid);
				fclose(f);
				
				return 1;
			}
			«ELSE»			
			int print_read_samples (struct numap_sampling_measure *measure, int tid, char* file_name) {
				FILE *f;
				int i, j;
				struct perf_event_mmap_page *metadata_page;
				struct perf_event_header *header;
				uint64_t head;
				uint64_t consumed;
			
				char *actor_found_name;
				char *action_found_name;
				uint64_t action_found_start;
				uint64_t action_found_end;
				char *fifo_found_src_name;
				char *fifo_found_dst_name;
				int action_found;
			
				f = fopen(file_name, "a");
			
				fprintf(f, "// New Thread: tid = %d\n", tid);
				metadata_page = measure->metadata_pages_per_tid[tid];
				head = metadata_page -> data_head;
				rmb();
				header = (struct perf_event_header *)((char *)metadata_page + measure->page_size);
				consumed = 0;

				// Parse all samples
				while (consumed < head) {
					if (header->size == 0) {
						fprintf(stderr, "Error: invalid header size = 0\n");
						return -1;
					}
					if (header -> type == PERF_RECORD_SAMPLE) {
						struct read_sample *sample = (struct read_sample *)((char *)(header) + 8);
						fprintf(f,  "ip = %" PRIx64
									", @ = %" PRIx64
									", weight = %" PRIu64
									", src_level = %s\n",
									sample->ip, sample->addr, sample->weight,
									get_data_src_level(sample->data_src));
					}
					consumed += header->size;
					header = (struct perf_event_header *)((char *)header + header -> size);
				}
				fprintf(f, "// End Thread: tid = %d\n", tid);
				fclose(f);
				
				return 1;
			}
			«ENDIF»
			
		«ENDIF»		
		////////////////////////////////////////////////////////////////////////////////
		// Token functions
		«FOR port : actor.inputs»
			«port.readTokensFunctions»
		«ENDFOR»

		«FOR port : actor.outputs.notNative»
			«port.writeTokensFunctions»
		«ENDFOR»

		////////////////////////////////////////////////////////////////////////////////
		// Functions/procedures
		«FOR proc : actor.procs»
			«proc.declare»
		«ENDFOR»

		«FOR proc : actor.procs.notNativeProcs»
			«proc.print»
		«ENDFOR»

		////////////////////////////////////////////////////////////////////////////////
		// Actions
		«FOR action : actor.actions»
			«action.print()»
		«ENDFOR»

		////////////////////////////////////////////////////////////////////////////////
		// Initializes
		«printInitialize»

		////////////////////////////////////////////////////////////////////////////////
		// Action scheduler
		«printActorScheduler»
	'''
}
	def protected printActorScheduler() '''
		«IF actor.hasFsm»
			«printFsm»
		«ELSE»
			void «entityName»_scheduler(schedinfo_t *si) {
				int i = 0;
				si->ports = 0;

				«printCallTokensFunctions»

				«actor.actionsOutsideFsm.printActionSchedulingLoop»

			finished:
				«FOR port : actor.inputs»
					read_end_«port.name»();
				«ENDFOR»
				«FOR port : actor.outputs.notNative»
					write_end_«port.name»();
				«ENDFOR»
				«IF actor.inputs.nullOrEmpty && actor.outputs.nullOrEmpty »
					// no read_end/write_end here!
					return;
				«ENDIF»
			}
		«ENDIF»
	'''

	//========================================
	//                  FSM
	//========================================
	def protected printFsm() '''
		«IF ! actor.actionsOutsideFsm.empty»
			void «entityName»_outside_FSM_scheduler(schedinfo_t *si) {
				int i = 0;
				«actor.actionsOutsideFsm.printActionSchedulingLoop»
			finished:
				// no read_end/write_end here!
				return;
			}
		«ENDIF»

		void «entityName»_scheduler(schedinfo_t *si) {
			int i = 0;

			«printCallTokensFunctions»

			// jump to FSM state
			switch (_FSM_state) {
			«FOR state : actor.fsm.states»
				case my_state_«state.name»:
					goto l_«state.name»;
			«ENDFOR»
			default:
				printf("unknown state in «entityName».c : %s\n", stateNames[_FSM_state]);
				exit(1);
			}

			// FSM transitions
			«FOR state : actor.fsm.states»
		«state.printStateLabel»
			«ENDFOR»
		finished:
			«FOR port : actor.inputs»
				read_end_«port.name»();
			«ENDFOR»
			«FOR port : actor.outputs.notNative»
				write_end_«port.name»();
			«ENDFOR»
			«IF actor.inputs.nullOrEmpty && actor.outputs.nullOrEmpty »
				// compiler needs to have something after the 'finished' label
				i = i;
			«ENDIF»
		}
	'''

	def protected printStateLabel(State state) '''
	l_«state.name»:
		«IF ! actor.actionsOutsideFsm.empty»
			«entityName»_outside_FSM_scheduler(si);
			i += si->num_firings;
		«ENDIF»
		«IF state.outgoing.empty»
			printf("Stuck in state "«state.name»" in «entityName»\n");
			exit(1);
		«ELSE»
			«state.printStateTransitions»
		«ENDIF»
	'''

	def protected printAlignmentConditions(Action action) '''
		{
			int isAligned = 1;
			«FOR port : action.inputPattern.ports»
				«IF port.hasAttribute(action.name + "_" + ALIGNABLE) && !port.hasAttribute(ALIGNED_ALWAYS)»
					isAligned &= ((index_«port.name» % SIZE_«port.name») < ((index_«port.name» + «action.inputPattern.getNumTokens(port)») % SIZE_«port.name»));
				«ENDIF»
			«ENDFOR»
			«FOR port : action.outputPattern.ports»
				«IF port.hasAttribute(action.name + "_" + ALIGNABLE) && !port.hasAttribute(ALIGNED_ALWAYS)»
					isAligned &= ((index_«port.name» % SIZE_«port.name») < ((index_«port.name» + «action.outputPattern.getNumTokens(port)») % SIZE_«port.name»));
				«ENDIF»
			«ENDFOR»
	'''

	def protected printStateTransition(State state, Transition trans) {
		val output = '''
			«val outputSchedulable = trans.action.hasAttribute(OUTPUT_SCHEDULABLE) || actor.hasAttribute(OUTPUT_SCHEDULABLE)»
			if («trans.action.inputPattern.checkInputPattern»«IF outputSchedulable»«trans.action.outputPattern.checkOutputPattern»«ENDIF»«entityName»_«trans.action.scheduler.name»()) {
				«IF !trans.action.outputPattern.empty && !outputSchedulable»
					«trans.action.outputPattern.printOutputPattern»
						_FSM_state = my_state_«state.name»;
						si->num_firings = i;
						si->reason = full;
						goto finished;
					}
				«ENDIF»
				«IF trans.action.hasAttribute(ALIGNED_ALWAYS)»
					«entityName»_«trans.action.body.name»_aligned();
				«ELSEIF trans.action.hasAttribute(ALIGNABLE)»
					«trans.action.printAlignmentConditions»
						if (isAligned) {
							«entityName»_«trans.action.body.name»_aligned();
						} else {
							«entityName»_«trans.action.body.name»();
						}
					}
				«ELSE»
					«entityName»_«trans.action.body.name»();
				«ENDIF»
				i++;
				goto l_«trans.target.name»;
		'''
		return output
	}

	def protected printStateTransitions(State state) '''
		«FOR trans : state.outgoing.map[it as Transition] SEPARATOR " else "»
		«printStateTransition(state, trans)»
		}«ENDFOR» else {
			«transitionPattern.get(state).printTransitionPattern»
			_FSM_state = my_state_«state.name»;
			goto finished;
		}
	'''

	def protected printTransitionPattern(Pattern pattern)  {
		'''
		«IF newSchedul»
			«FOR port : pattern.ports»
				«printTransitionPatternPort(port, pattern)»
			«ENDFOR»
		«ENDIF»
		si->num_firings = i;
		si->reason = starved;
		'''
	}

	def private printTransitionPatternPort(Port port, Pattern pattern) '''
		if (numTokens_«port.name» - index_«port.name» < «pattern.getNumTokens(port)») {
			if( ! «entityName».sched->round_robin || i > 0) {
				«IF incomingPortMap.containsKey(port)»
					sched_add_schedulable(«entityName».sched, &«incomingPortMap.get(port).source.label»);
				«ENDIF»
			}
		}
	'''

	def protected printCallTokensFunctions() '''
		«FOR port : actor.inputs»
			read_«port.name»();
		«ENDFOR»
		«FOR port : actor.outputs.notNative»
			write_«port.name»();
		«ENDFOR»
	'''

	def protected printInitialize() '''
		«FOR init : actor.initializes»
			«init.print()»
		«ENDFOR»

		void «entityName»_initialize(schedinfo_t *si) {
			int i = 0;
			«additionalInitializes»
			«FOR port : actor.outputs.notNative»
				write_«port.name»();
			«ENDFOR»
			«IF actor.hasFsm»
				/* Set initial state to current FSM state */
				_FSM_state = my_state_«actor.fsm.initialState.name»;
			«ENDIF»
			«FOR initialize : actor.initializes»
				if(«entityName»_«initialize.scheduler.name»()) {
					«entityName»_«initialize.name»();
				}
			«ENDFOR»
		finished:
			«FOR port : actor.outputs.notNative»
				write_end_«port.name»();
			«ENDFOR»
			return;
		}
	'''

	def protected checkConnectivy() {
		for(port : actor.inputs.filter[incomingPortMap.get(it) === null]) {
			OrccLogger::noticeln("["+entityName+"] Input port "+port.name+" not connected.")
		}
		for(port : actor.outputs.filter[outgoingPortMap.get(it).nullOrEmpty]) {
			OrccLogger::noticeln("["+entityName+"] Output port "+port.name+" not connected.")
		}
	}

	def protected printActionSchedulingLoop(List<Action> actions) '''
		while (1) {
			«actions.printActionsScheduling»
		}
	'''

	def protected printActionScheduling(Action action) {
		val output = '''
			«val outputSchedulable = action.hasAttribute(OUTPUT_SCHEDULABLE) || actor.hasAttribute(OUTPUT_SCHEDULABLE)»
			if («action.inputPattern.checkInputPattern»«IF outputSchedulable»«action.outputPattern.checkOutputPattern»«ENDIF»«entityName»_«action.scheduler.name»()) {
				«IF !action.outputPattern.empty && !outputSchedulable»
					«action.outputPattern.printOutputPattern»
						si->num_firings = i;
						si->reason = full;
						goto finished;
					}
				«ENDIF»
				«IF action.hasAttribute(ALIGNED_ALWAYS)»
					«entityName»_«action.body.name»_aligned();
				«ELSEIF action.hasAttribute(ALIGNABLE)»
					«action.printAlignmentConditions»
						if (isAligned) {
							«entityName»_«action.body.name»_aligned();
						} else {
							«entityName»_«action.body.name»();
						}
					}
				«ELSE»
					«entityName»_«action.body.name»();
				«ENDIF»
				i++;
		'''
		return output
	}

	def protected printActionsScheduling(Iterable<Action> actions) '''
		«FOR action : actions SEPARATOR " else "»
			«action.printActionScheduling»
			}«ENDFOR» else {
			«inputPattern.printTransitionPattern»
			goto finished;
		}
	'''

	def protected printOutputPattern(Pattern pattern) '''
		int stop = 0;
		«FOR port : pattern.ports»
			«var i = -1»
			«FOR connection : outgoingPortMap.get(port)»
				if («pattern.getNumTokens(port)» > SIZE_«port.name» - index_«port.name» + «port.fullName»->read_inds[«i = i + 1»]) {
					stop = 1;
					«IF newSchedul»
						if( ! «entityName».sched->round_robin || i > 0) {
							sched_add_schedulable(«entityName».sched, &«connection.target.label»);
						}
					«ENDIF»
				}
			«ENDFOR»
		«ENDFOR»
		if (stop != 0) {
	'''

	def protected checkInputPattern(Pattern pattern)
		'''«FOR port : pattern.ports»numTokens_«port.name» - index_«port.name» >= «pattern.getNumTokens(port)» && «ENDFOR»'''

	def protected checkOutputPattern(Pattern pattern)
		'''«FOR port : pattern.ports»«var i = -1»«FOR connection : outgoingPortMap.get(port)»!(«pattern.getNumTokens(port)» > SIZE_«port.name» - index_«port.name» + «port.fullName»->read_inds[«i = i + 1»]) && «ENDFOR»«ENDFOR»'''

	def protected writeTokensFunctions(Port port) '''
		static void write_«port.name»() {
			index_«port.name» = «port.fullName»->write_ind;
		}

		static void write_end_«port.name»() {
			«port.fullName»->write_ind = index_«port.name»;
		}
	'''

	def protected readTokensFunctions(Port port) '''
		static void read_«port.name»() {
			«IF incomingPortMap.containsKey(port)»
				index_«port.name» = «port.fullName»->read_inds[«port.readerId»];
				numTokens_«port.name» = index_«port.name» + fifo_«port.type.doSwitch»_get_num_tokens(«port.fullName», «port.readerId»);
			«ELSE»
				/* Input port «port.fullName» not connected */
				index_«port.name» = 0;
				numTokens_«port.name» = 0;
			«ENDIF»
		}

		static void read_end_«port.name»() {
			«IF incomingPortMap.containsKey(port)»
				«port.fullName»->read_inds[«port.readerId»] = index_«port.name»;
			«ELSE»
				/* Input port «port.fullName» not connected */
			«ENDIF»
		}
	'''
	
	def private printCore(Action action, boolean isAligned) '''
		static void «entityName»_«action.body.name»«IF isAligned»_aligned«ENDIF»() {
			«FOR variable : action.body.locals»
				«variable.declare»;
			«ENDFOR»
			«IF numaProfilerExit && numaProfilerExitAction.identityEquals(action)»
			
			// Declare variables at the start of the @numaProfilerExit action as its C not C++ ...
			int i;
			int res;
			size_t stack_size;
			void *stack_addr;
			pthread_attr_t attr;
			
			«ENDIF»
			«IF debugActor || debugAction»
				printf("-- «entityName»: «entityName»_«action.name»«IF isAligned» (aligned)«ENDIF»\n");
			«ENDIF»
			«IF debugAction»
				«debugTokens(action.inputPattern)»
			«ENDIF»
			«beforeActionBody»
			«FOR block : action.body.blocks»
				«block.doSwitch»
			«ENDFOR»
			«afterActionBody»
			«IF debugAction»
				«debugTokens(action.outputPattern)»
			«ENDIF»

			// Update ports indexes
			«FOR port : action.inputPattern.ports»
				index_«port.name» += «action.inputPattern.getNumTokens(port)»;
				«IF action.inputPattern.getNumTokens(port) >= MIN_REPEAT_RWEND»
					read_end_«port.name»();
				«ENDIF»
			«ENDFOR»
			«FOR port : action.outputPattern.ports»
				index_«port.name» += «action.outputPattern.getNumTokens(port)»;
				«IF action.outputPattern.getNumTokens(port) >= MIN_REPEAT_RWEND»
					write_end_«port.name»();
				«ENDIF»
			«ENDFOR»
			«IF numaProfilerExit && numaProfilerExitAction.identityEquals(action)»

				«performNumaProfilerExitJob»
			«ENDIF»
		}
	'''

	// These 2 methods are used by HMPP backend to print code before the
	// first line after after the last of an action body
	def protected afterActionBody() ''''''
	def protected beforeActionBody() ''''''
	
	// This method can be override by other backends to print additional initializations 
	def protected additionalInitializes()''''''
	// This method can be override by other backends to print additional declarations 
	def protected additionalDeclarations() ''''''
	// This method can be override by other backends to print additional includes
	def protected printAdditionalIncludes() ''''''
	
	def private debugTokens(Pattern pattern) '''
		«FOR port : pattern.ports»
			{
				int i;
				printf("--- «port.name»: ");
				for (i = 0; i < «pattern.getNumTokens(port)»; i++) {
					printf("%«port.type.printfFormat» ", tokens_«port.name»[(index_«port.name» + i) % SIZE_«port.name»]);
				}
				printf("\n");
			}
		«ENDFOR»
	'''

	def protected print(Action action) {
		currentAction = action
		isActionAligned = false
		debugAction = action.hasAttribute(DIRECTIVE_DEBUG)
		'''
		«action.scheduler.print»
		
		«IF !action.hasAttribute(ALIGNED_ALWAYS)»
			«printCore(action, false)»
		«ENDIF»
		«IF isActionAligned = action.hasAttribute(ALIGNABLE)»
			«printCore(action, true)»
		«ENDIF»
		'''
	}

	def protected print(Procedure proc) {
		val isOptimizable = proc.hasAttribute(DIRECTIVE_OPTIMIZE_C);
		val optCond = proc.getAttribute(DIRECTIVE_OPTIMIZE_C)?.getValueAsString("condition")
		val optName = proc.getAttribute(DIRECTIVE_OPTIMIZE_C)?.getValueAsString("name")
		'''
		static «proc.returnType.doSwitch» «entityName»_«proc.name»(«proc.parameters.join(", ")[declare]») {
«««		static «proc.returnType.doSwitch» «proc.name»(«proc.parameters.join(", ")[declare]») {
			«IF isOptimizable»
				#if «optCond»
				«optName»(«proc.parameters.join(", ")[variable.name]»);
				#else
			«ENDIF»
			«FOR variable : proc.locals»
				«variable.declare»;
			«ENDFOR»

			«FOR block : proc.blocks»
				«block.doSwitch»
			«ENDFOR»
			«IF isOptimizable»
				#endif // «optCond»
			«ENDIF»
		}
		'''
	}
	
	def protected declare(Procedure proc){
		val modifier = if(proc.native) "extern" else "static"
		'''
			«IF proc.name != "print"»
				«modifier» «proc.returnType.doSwitch» «entityName»_«proc.name»(«proc.parameters.join(", ")[declare]»);
			«ENDIF»
		'''
	}

	override protected declare(Var variable) {
		if(variable.global && variable.initialized && !variable.assignable && !variable.type.list) {
			'''#define «variable.name» «variable.initialValue.doSwitch»'''
		} else {
			val const = if(!variable.assignable && variable.global) "const "
			val global = if(variable.global) "static "
			val type = variable.type
			val dims = variable.type.dimensionsExpr.printArrayIndexes
			val init = if(variable.initialized) " = " + variable.initialValue.doSwitch
			val end = if(variable.global) ";"
			
			'''«global»«const»«type.doSwitch» «variable.name»«dims»«init»«end»'''
		}
	}
	
	def protected declare(Param param) {
		val variable = param.variable
		'''«variable.type.doSwitch» «variable.name»«variable.type.dimensionsExpr.printArrayIndexes»'''
	}

	def private getReaderId(Port port) {
		if(incomingPortMap.containsKey(port)) {
			String::valueOf(incomingPortMap.get(port).<Integer>getValueAsObject("fifoId"))
		} else {
			"-1"
		}
	}

	def protected fullName(Port port)
		'''fifo_«entityName»_«port.name»'''

	def protected sizeOrDefaultSize(Connection conn) {
		if(conn === null || conn.size === null) "SIZE"
		else conn.size
	}
		
	def protected performNumaProfilerExitJob()	''' 
		«IF numaProfiler»
			if(«numaProfilerExitCond») {
				// Put numa memory profiling exit code here.
				«IF numaProfileMode.equals("write")»
					// Stop memory write sampling and save results to a file.
					if(numap_write_support()) {
						for (i = 0; i < nb_threads; i++) {
							res = numap_sampling_write_stop(&measures[i]);
							if(res < 0) {
								fprintf(stderr, "numap_sampling_write_stop error : %s\n", numap_error_message(res));
								exit(-1);
							} else {
								fprintf(stdout, "numap_sampling_write_stop OK\n");
							}
						}
						for (i = 0; i < nb_threads; i++) {
							print_write_samples(&measures[i], i, "«executionDataPath»/mem-write-samples");
						}
					}
					else {
						fprintf(stdout, "numap_sample_write not supported on this architecture.\n");
						exit(-1);
					}
				«ELSE»
					// Stop memory read sampling and save results to a file.
					for (i = 0; i < nb_threads; i++) {
						res = numap_sampling_read_stop(&measures[i]);
						if(res < 0) {
							fprintf(stderr, "numap_sampling_read_stop error : %s\n", numap_error_message(res));
							exit(-1);
						} else {
							fprintf(stdout, "numap_sampling_read_stop OK\n");
						}
					}
					for (i = 0; i < nb_threads; i++) {
						print_read_samples(&measures[i], i, "«executionDataPath»/mem-read-samples");
					}
				«ENDIF»


				// Get threads stack informations
				for (i = 0; i < nb_threads; i++) {
					res = pthread_getattr_np(threads[i], &attr);
					if (res != 0) {
						fprintf(stderr, "pthread_getattr_np error\n");
						exit(-1);
					}
					res = pthread_attr_getstack(&attr, &stack_addr, &stack_size);
					if (res != 0) {
						fprintf(stderr, "pthread_attr_getstack error\n");
					 	exit(-1);
					}
					threads_stack_start[i] =  (uint64_t) stack_addr;
					threads_stack_end[i] =  (uint64_t) ((char *) stack_addr + stack_size);
				}
				
				print_stack_info("«executionDataPath»/mem-«numaProfileMode»-threads-stacks");

				exit(0); // Exiting the program after stats calculations & reporting are finished.
			} // «numaProfilerExitCond»
		«ENDIF»
	'''

	//========================================
	//               Blocks
	//========================================
	override caseBlockIf(BlockIf block)'''
		if («block.condition.doSwitch») {
			«FOR thenBlock : block.thenBlocks»
				«thenBlock.doSwitch»
			«ENDFOR»
		}«IF block.elseRequired» else {
			«FOR elseBlock : block.elseBlocks»
				«elseBlock.doSwitch»
			«ENDFOR»
		}
		«ENDIF»
	'''

	override caseBlockWhile(BlockWhile blockWhile) {
		if(!isActionAligned || !blockWhile.hasAttribute(REMOVABLE_COPY)){
			'''
			while («blockWhile.condition.doSwitch») {
				«FOR block : blockWhile.blocks»
					«block.doSwitch»
				«ENDFOR»
			}
			'''
		}
	}

	override caseBlockBasic(BlockBasic block) '''
		«FOR instr : block.instructions»
			«instr.doSwitch»
		«ENDFOR»
	'''

	override caseBlockFor(BlockFor block) '''
		for («block.init.join(", ")['''«toExpression»''']» ; «block.condition.doSwitch» ; «block.step.join(", ")['''«toExpression»''']») {
			«FOR contentBlock : block.blocks»
				«contentBlock.doSwitch»
			«ENDFOR»
		}
	'''

	//========================================
	//            Instructions
	//========================================
	override caseInstAssign(InstAssign inst) '''
		«inst.target.variable.name» = «inst.value.doSwitch»;
	'''

	/**
	 * Print extra code for array inbounds checking (ex: C assert) at each usage (load/store)
	 * If exprList is empty, return an empty string.
	 */
	private def checkArrayInbounds(List<Expression> exprList, List<Integer> dims) '''
		«IF !exprList.empty»
			«FOR i : 0 .. exprList.size - 1»
				assert((«exprList.get(i).doSwitch») < «dims.get(i)»);
			«ENDFOR»
		«ENDIF»
	'''

	override caseInstLoad(InstLoad load) {
		val target = load.target.variable
		val source = load.source.variable
		val port = source.getPort
		'''
			«IF port !== null» ««« Loading data from input FIFO
				«IF (isActionAligned && port.hasAttribute(currentAction.name + "_" + ALIGNABLE)) || port.hasAttribute(ALIGNED_ALWAYS)»
					«target.name» = tokens_«port.name»[(index_«port.name» % SIZE_«port.name») + («load.indexes.head.doSwitch»)];
				«ELSE»
					«target.name» = tokens_«port.name»[(index_«port.name» + («load.indexes.head.doSwitch»)) % SIZE_«port.name»];
				«ENDIF»
			«ELSE»
				««« Loading data from classical variable
				«IF checkArrayInbounds»
					«load.indexes.checkArrayInbounds(source.type.dimensions)»
				«ENDIF»
				«target.name» = «source.name»«load.indexes.printArrayIndexes»;
			«ENDIF»
		'''
	}

	override caseInstStore(InstStore store) {
		val target = store.target.variable
		val port = target.port
		'''
			«IF port !== null» ««« Storing data to output FIFO
				«IF port.native»
					printf("«port.name» = %i\n", «store.value.doSwitch»);
				«ELSEIF (isActionAligned && port.hasAttribute(currentAction.name + "_" + ALIGNABLE)) || port.hasAttribute(ALIGNED_ALWAYS)»
					tokens_«port.name»[(index_«port.name» % SIZE_«port.name») + («store.indexes.head.doSwitch»)] = «store.value.doSwitch»;
				«ELSE»
					tokens_«port.name»[(index_«port.name» + («store.indexes.head.doSwitch»)) % SIZE_«port.name»] = «store.value.doSwitch»;
				«ENDIF»
			«ELSE»
				««« Storing data to classical variable
				«IF checkArrayInbounds»
					«store.indexes.checkArrayInbounds(target.type.dimensions)»
				«ENDIF»
				«target.name»«store.indexes.printArrayIndexes» = «store.value.doSwitch»;
			«ENDIF»
		'''
	}

	override caseInstCall(InstCall call) '''
		«IF call.print»
			printf(«call.arguments.printfArgs.join(", ")»);
		«ELSE»
			«IF call.target !== null»«call.target.variable.name» = «ENDIF»«IF !call.procedure.isNative»«entityName»_«ENDIF»«call.procedure.name»(«call.arguments.join(", ")[print]»);
		«ENDIF»
	'''

	override caseInstReturn(InstReturn ret) '''
		«IF ret.value !== null»
			return «ret.value.doSwitch»;
		«ENDIF»
	'''

	override caseInstTernary(InstTernary inst) '''
		«inst.target.variable.name» = «inst.conditionValue.doSwitch» ? «inst.trueValue.doSwitch» : «inst.falseValue.doSwitch»;
	'''
	
	override caseExprVar(ExprVar expr) {
		val port = expr.copyOf
		if(port !== null && isActionAligned){
			// If the argument is just a local copy of input/output tokens
			// use directly the FIFO when the tokens are aligned
			'''&tokens_«port.name»[index_«port.name» % SIZE_«port.name»]'''
		} else {
			expr.use.variable.name
		}
	}

	//========================================
	//            Helper methods
	//========================================
	
	/**
	 * Returns the port object corresponding to the given variable.
	 * 
	 * @param variable
	 *            a variable
	 * @return the corresponding port, or <code>null</code>
	 */
	def protected getPort(Var variable) {
		if(currentAction === null) {
			null
		} else if (currentAction?.inputPattern.contains(variable)) {
			currentAction.inputPattern.getPort(variable)
		} else if(currentAction?.outputPattern.contains(variable)) {
			currentAction.outputPattern.getPort(variable)
		} else if(currentAction?.peekPattern.contains(variable)) {
			currentAction.peekPattern.getPort(variable)
		} else {
			null
		}
	}
	
	/**
	 * Returns the port object in case the corresponding expression is 
	 * just a straight copy of the tokens.
	 * 
	 * @param expr
	 *            an expression
	 * @return the corresponding port, or <code>null</code>
	 */
	def private copyOf(ExprVar expr) {
		val action = EcoreHelper.getContainerOfType(expr, Action)
		val variable = expr.use.variable
		if(action === null || !expr.type.list || !variable.hasAttribute(COPY_OF_TOKENS)) {
			return null
		}
		return variable.getValueAsEObject(COPY_OF_TOKENS) as Port
	}

	def private print(Arg arg) {
		if(arg.byRef) {
			"&" + (arg as ArgByRef).use.variable.name + (arg as ArgByRef).indexes.printArrayIndexes
		} else {
			(arg as ArgByVal).value.doSwitch
		}
	}

	def private setDebug() {
		debugActor = actor.hasAttribute(DIRECTIVE_DEBUG)
	}

	//========================================
	//   Old template data initialization
	//========================================
	def private buildInputPattern() {
		inputPattern.clear
		for (action : actor.actionsOutsideFsm) {
			val actionPattern = action.inputPattern
			for (port : actionPattern.ports) {
				var numTokens = Math::max(inputPattern.getNumTokens(port), actionPattern.getNumTokens(port))
				inputPattern.setNumTokens(port, numTokens)
			}
		}
	}

	def private buildTransitionPattern() {
		val fsm = actor.getFsm()
		transitionPattern.clear

		if (fsm !== null) {
			for (state : fsm.getStates()) {
				val pattern = DfFactory::eINSTANCE.createPattern()

				for (edge : state.getOutgoing()) {
					val actionPattern = (edge as Transition).getAction.getInputPattern()

					for (Port port : actionPattern.getPorts()) {
						var numTokens = Math::max(pattern.getNumTokens(port), actionPattern.getNumTokens(port))
						pattern.setNumTokens(port, numTokens)
					}
				}
				transitionPattern.put(state, pattern)
			}
		}
	}
}
