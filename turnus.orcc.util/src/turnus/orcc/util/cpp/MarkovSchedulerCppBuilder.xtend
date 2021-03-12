package turnus.orcc.util.cpp

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import turnus.model.analysis.scheduling.MarkovSimpleSchedulerReport
import turnus.model.analysis.scheduling.MarkovSchedulingState

class MarkovSchedulerCppBuilder {

	private final MarkovSimpleSchedulerReport report;

	new(MarkovSimpleSchedulerReport report) {
		this.report = report;
	}

	public def write(File targetFile) {
		val ps = new PrintStream(new FileOutputStream(targetFile))
		ps.print(content)
		ps.close
	}

	private def getContent() {
		'''
			«FOR partition : report.partitions»
				void partition_«partition.partitionId»(){
					// initialize all the actors
					«FOR actor : partition.actors»
						act_«actor.name»->initialize();
					«ENDFOR»
					
					EStatus status = None;
					
					// rescue scheduling
					s_init:
					
					«var actors = partition.actors»
					
					«FOR actor : actors SEPARATOR '\n'»
						«var state = partition.getAssociatedState(actor)»
						«IF state !== null && !state.outgoings.isEmpty»
							s_init_«actor.name»: if(act_«actor.name»->action_selection(status)) goto s_next_«state.name»;
						«ELSE»
							// no available states
							act_«actor.name»->action_selection(status);
						«ENDIF»
					«ENDFOR»
					
					return;
					
					«FOR state : partition.states SEPARATOR '\n'»
						s_next_«state.name»:
						«FOR transition : state.outgoings.subList(transitionsShift(state),state.outgoings.size)»
							«var target = transition.target»
							// «state.actor.name» --> «target.actor.name»
							if(act_«target.actor.name»->action_selection(status)) goto s_next_«target.name»; // firings=«transition.firings»
						«ENDFOR»
						«var nextActorIdx = actors.indexOf(state.actor) + 1»
						«IF nextActorIdx < actors.size»
							goto s_init_«actors.get(nextActorIdx).name»;
						«ELSE»
							goto s_init;
						«ENDIF»
						
					«ENDFOR»
				}
			«ENDFOR»
			
		'''
	}

	private def int transitionsShift(MarkovSchedulingState state) {
		if (!state.outgoings.isEmpty) {
			if (state.actor == state.outgoings.get(0).target.actor) {
				return 1;
			}
		}
		return 0;
	}

}
