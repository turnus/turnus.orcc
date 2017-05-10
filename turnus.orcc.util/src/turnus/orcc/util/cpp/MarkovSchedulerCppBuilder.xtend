package turnus.orcc.util.cpp

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import turnus.model.analysis.scheduling.MarkovSimpleSchedulerReport

class MarkovSchedulerCppBuilder {
	
	private final MarkovSimpleSchedulerReport report;
	
	new(MarkovSimpleSchedulerReport report){
		this.report = report;	
	}
	
	public def write(File targetFile){
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
			status = None;
			
			«FOR actor : partition.actors SEPARATOR '\n'»
			s_init_«actor.name»: act_«actor.name»->action_selection(status);
			«var state = partition.getAssociatedState(actor)»
			«IF state != null && !state.outgoings.isEmpty»
			if(status!=None) goto s_next_«state.name»;
			«ENDIF»
			«ENDFOR»
			
			«FOR state : partition.states SEPARATOR '\n'»
				s_next_«state.name»: 
				status = None;
				«FOR transition : state.outgoings»
				«var target = transition.target»
				act_«target.actor.name»->action_selection(status);
				if(status!=None) goto s_next_«target.name»;
				«ENDFOR»
				goto s_init;
			«ENDFOR»
		}
		«ENDFOR»

		'''
	}
		
	
}