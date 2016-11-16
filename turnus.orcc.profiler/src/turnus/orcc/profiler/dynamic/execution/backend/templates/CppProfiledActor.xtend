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

import java.util.HashMap
import java.util.Map
import net.sf.orcc.df.Action
import net.sf.orcc.df.Actor
import net.sf.orcc.ir.BlockBasic
import net.sf.orcc.ir.BlockIf
import net.sf.orcc.ir.BlockWhile
import java.util.Set
import java.util.HashSet

/**
 * 
 * @author Endri Bezati
 *
 */
class CppProfiledActor extends CppActor {

	private int procedureFiring

	new(Actor actor, Map<String, Object> options) {
		super(actor, options)
		procedureFiring = 0;
	}

	override otherInclude() {
		'''
			#include <string>
			#include <stdlib.h>
			#include "Tracer.h"
			extern long firingId;
		'''

	}

	override startProcedure(String parent, String child) {
		'''
		'''
	}

	override startSchedulingProcedure(String parent, String child) {
		'''
		'''
	}

	override additionalPublicMembers() {
		'''
			void prof_reset_counters(){
			«FOR port : actor.inputs»
				prof_CONSUME_«port.name» = 0;
			«ENDFOR»
			«FOR port : actor.outputs»
				prof_PRODUCE_«port.name» = 0;
			«ENDFOR»
				
			«FOR variable : actor.stateVars SEPARATOR "\n"»prof_LOAD_«variable.name» = 0;«ENDFOR»
			«FOR variable : actor.stateVars SEPARATOR "\n"»prof_STORE_«variable.name» = 0;«ENDFOR»
			«FOR param : actor.parameters SEPARATOR "\n"»prof_LOAD_«param.name» = 0;«ENDFOR»
			
				prof_BINARY_BIT_AND = 0;
				prof_BINARY_BIT_OR = 0;
				prof_BINARY_BIT_XOR = 0;
				prof_BINARY_DIV = 0;
				prof_BINARY_DIV_INT = 0;
				prof_BINARY_EQ = 0;
				prof_BINARY_EXP = 0;
				prof_BINARY_GT = 0;
				prof_BINARY_GE = 0;
				prof_BINARY_LT = 0;
				prof_BINARY_LE = 0;
				prof_BINARY_LOGIC_OR = 0;
				prof_BINARY_LOGIC_AND = 0;
				prof_BINARY_MINUS = 0;
				prof_BINARY_PLUS = 0;
				prof_BINARY_MOD = 0;
				prof_BINARY_TIMES = 0;
				prof_BINARY_NE = 0;
				prof_BINARY_SHIFT_LEFT = 0;
				prof_BINARY_SHIFT_RIGHT = 0;
				prof_UNARY_BIT_NOT = 0;
				prof_UNARY_LOGIC_NOT = 0;
				prof_UNARY_MINUS = 0;
				prof_UNARY_NUM_ELTS = 0;
				prof_DATAHANDLING_STORE = 0;
				prof_DATAHANDLING_ASSIGN = 0;
				prof_DATAHANDLING_CALL = 0;
				prof_DATAHANDLING_LOAD = 0;
				prof_DATAHANDLING_LIST_LOAD = 0;
				prof_DATAHANDLING_LIST_STORE = 0;
				prof_FLOWCONTROL_IF = 0;
				prof_FLOWCONTROL_WHILE = 0;
			}
			
			void set_tracer(Tracer* t){
				act_«actor.name»_tracer = t;
			}
			
		'''
	}

	override additionalPrivateMembers() {
		'''
			Tracer* act_«actor.name»_tracer;
			// -- Input-Output Ports
			«FOR port : actor.inputs»
				int prof_CONSUME_«port.name»;
			«ENDFOR»
			«FOR port : actor.outputs»
				int prof_PRODUCE_«port.name»;
			«ENDFOR»
			
			// -- State variable counters
			«FOR variable : actor.stateVars SEPARATOR "\n"»int prof_LOAD_«variable.name»;«ENDFOR»
			«FOR variable : actor.stateVars SEPARATOR "\n"»int prof_STORE_«variable.name»;«ENDFOR»
			
			«FOR param : actor.parameters SEPARATOR "\n"»int prof_LOAD_«param.name»;«ENDFOR»
			
			// -- Op Counters
			int prof_BINARY_BIT_AND;
			int prof_BINARY_BIT_OR;
			int prof_BINARY_BIT_XOR;
			int prof_BINARY_DIV;
			int prof_BINARY_DIV_INT;
			int prof_BINARY_EQ;
			int prof_BINARY_EXP;
			int prof_BINARY_GT;
			int prof_BINARY_GE;
			int prof_BINARY_LT;
			int prof_BINARY_LE;
			int prof_BINARY_LOGIC_OR;
			int prof_BINARY_LOGIC_AND;
			int prof_BINARY_MINUS;
			int prof_BINARY_PLUS;
			int prof_BINARY_MOD;
			int prof_BINARY_TIMES;
			int prof_BINARY_NE;
			int prof_BINARY_SHIFT_LEFT;
			int prof_BINARY_SHIFT_RIGHT;
			int prof_UNARY_BIT_NOT;
			int prof_UNARY_LOGIC_NOT;
			int prof_UNARY_MINUS;
			int prof_UNARY_NUM_ELTS;
			int prof_DATAHANDLING_STORE;
			int prof_DATAHANDLING_ASSIGN;
			int prof_DATAHANDLING_CALL;
			int prof_DATAHANDLING_LOAD;
			int prof_DATAHANDLING_LIST_LOAD;
			int prof_DATAHANDLING_LIST_STORE;
			int prof_FLOWCONTROL_IF;
			int prof_FLOWCONTROL_WHILE;
		'''
	}

	override endProcedure(Action action) {
		'''
			«IF !actor.initializes.contains(action)»
				«FOR e : action.inputPattern.numTokensMap»
					«IF actor.incomingPortMap.get(e.key) != null»
						prof_CONSUME_«e.key.name» += «e.value»;
					«ENDIF»
				«ENDFOR»
				«FOR e : action.outputPattern.numTokensMap»
					«IF actor.outgoingPortMap.get(e.key) != null»
						prof_PRODUCE_«e.key.name» += «e.value»;
					«ENDIF»
				«ENDFOR»
				print_profiling_«action.body.name»();
				prof_reset_counters();
				firingId++;
			«ENDIF»
		'''
	}

	override additionalInitFuctions() {
		'''
			prof_reset_counters();
		'''
	}

	override caseBlockBasic(BlockBasic node) {
		var Map<String, Integer> profiling = new HashMap<String, Integer>;

		if (node.hasAttribute("profiling")) {
			profiling = node.getAttribute("profiling").objectValue as HashMap<String,Integer>
		}
		'''
			«FOR p : profiling.keySet»
				prof_«p» += «profiling.get(p)»;
			«ENDFOR»
			«super.caseBlockBasic(node)»
		'''
	}

	override caseBlockIf(BlockIf node) {
		var Map<String, Integer> profiling = new HashMap<String, Integer>;

		if (node.hasAttribute("profiling")) {
			profiling = node.getAttribute("profiling").objectValue as HashMap<String,Integer>
		}
		'''
			«FOR p : profiling.keySet»
				prof_«p» += «profiling.get(p)»;
			«ENDFOR»
			«super.caseBlockIf(node)»
		'''
	}

	override caseBlockWhile(BlockWhile node) {
		var Map<String, Integer> profiling = new HashMap<String, Integer>;

		if (node.hasAttribute("profiling")) {
			profiling = node.getAttribute("profiling").objectValue as HashMap<String,Integer>
		}
		'''
			«FOR p : profiling.keySet»
				prof_«p» += «profiling.get(p)»;
			«ENDFOR»
			«super.caseBlockWhile(node)»
		'''
	}

	override compileAction(Action action) {
		var Set<String> ops = new HashSet<String>()
		var Set<String> reads = new HashSet<String>()
		var Set<String> writes = new HashSet<String>()
		if (action.body.hasAttribute("OpTags")) {
			ops = action.body.getAttribute("OpTags").objectValue as Set<String>
			var Set<String> toBeRemoved = new HashSet
			for (String op : ops) {
				if (op.contains("LOAD_")) {
					toBeRemoved.add(op);
					reads.add(op.replace("LOAD_", ""))
				} else if (op.contains("STORE_")) {
					toBeRemoved.add(op);
					writes.add(op.replace("STORE_", ""))
				}
			}
			ops.removeAll(toBeRemoved)
		}
		
		var int maxActorName = actor.simpleName.length
		var int actionName = action.name.length
		
		var int preHeaderSize = ("\"actor\"").length + 1
		
		'''
			
			«super.compileAction(action)»
			«IF !actor.initializes.contains(action)»
				void print_profiling_«action.body.name»(){
				
					boost::shared_ptr<std::ostream> out = act_«actor.name»_tracer->getOutputStream();
				
					*out << "{ ";	
					*out << "\"actor\" : " << "\"«actor.simpleName»\", ";
					*out << "\"action\" : " <<"\"«action.name»\", ";
					*out << "\"firing\" : " << "\"" << firingId << "\", ";
					*out << "\"fsm\" : " << «IF actor.actionsOutsideFsm.contains(action)»"true"«ELSE»"false"«ENDIF»;
					«IF !action.inputPattern.ports.empty»
						*out << ", \"consume\" : [";
						«FOR port : action.inputPattern.ports SEPARATOR "\n*out << \",\" ;"»
							*out << "{ \"port\" : \"«port.name»\"," << "\"count\" : " << prof_CONSUME_«port.name»  << "}";
						«ENDFOR»
						*out << "] ";
					«ENDIF»
					«IF !action.outputPattern.ports.empty»
						*out << ", \"produce\" : [";
						«FOR port : action.outputPattern.ports SEPARATOR "\n*out << \",\" ;"»
							*out << "{ \"port\" : \"«port.name»\"," << "\"count\" : " << prof_PRODUCE_«port.name»  << "}";
						«ENDFOR»
						*out << "] ";
					«ENDIF»
					«IF !reads.empty»
						*out << ", \"read\" : [";
						«FOR read : reads SEPARATOR "\n*out << \",\" ;"»
							*out <<  "{ \"var\" : \"«read»\"," << "\"count\" : " << prof_LOAD_«read» << "}";
						«ENDFOR»
						*out << "] ";
					«ENDIF»
					«IF !writes.empty»
						*out << ", \"write\" : [";
						«FOR write : writes SEPARATOR "\n*out << \",\" ;"»
							*out << "{ \"var\" : \"«write»\"," << "\"count\" : " << prof_STORE_«write» << "}";
						«ENDFOR»
						*out << "] ";
					«ENDIF»
					«IF !ops.empty»
						*out << ", \"op\" : [ ";
						«FOR op : ops SEPARATOR "\n*out << \",\" ;"»
							*out << "{ \"name\" : \"«op»\"," << "\"count\" : " << prof_«op» << "}";
						«ENDFOR»
						*out << "] ";
					«ENDIF»
					*out << " }\n";
				}
			«ENDIF»
		'''
	}
}