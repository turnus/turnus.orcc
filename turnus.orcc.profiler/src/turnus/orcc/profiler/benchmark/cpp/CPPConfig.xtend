package turnus.orcc.profiler.benchmark.cpp

import turnus.orcc.profiler.benchmark.CompilerConfig
import org.eclipse.debug.core.ILaunchConfiguration
import java.io.File
import org.eclipse.core.runtime.IPath
import turnus.common.io.Logger
import org.apache.commons.cli.CommandLine
import java.io.BufferedReader
import java.io.InputStreamReader
import static net.sf.orcc.OrccLaunchConstants.MAPPING;
import java.util.Map
import java.util.HashMap

class CPPConfig extends CompilerConfig {
	val String cpp_options;
	new(ILaunchConfiguration conf, File input, Integer n_loops, String other_options,String cpp_options) {
		super(conf, input, n_loops, other_options)
		this.cpp_options=cpp_options
	}
	
	def String getOptionString(){
		''' -l «n_loops» «IF input!=null»-i «input.absolutePath»«ENDIF» «other_options»'''
	}
	def perf_profile( IPath binpath, IPath csv_path){
		val mapping=launch_config.getAttribute(MAPPING,new HashMap)
		val numCores =if(mapping.size>0){
			mapping.size
		}else{
			1
		}
		val t= new PerfMeasurement(launch_config.name,binpath,
			#["cache-misses","cache-references",
			"cycles","instructions","task-clock",
			"cpu-clock","mem-loads","ref-cycles"],
			optionString,
			numCores
		).asTable
		return t
	}
	

	
	override measure(IPath path){
		Logger.info("Entering "+path.toString)
		val buildPath=path.append("build")
		val String[] mkdir = #["mkdir",buildPath.toOSString]
		runProc(mkdir,path.toFile)
		val rootPath=path.removeLastSegments(1);
		val String[] cmake = #["cmake","-DCMAKE_BUILD_TYPE=\"Release\"",'''«cpp_options»''',".."]
		runProc(cmake,buildPath.toFile)
		
		val make=#["make","-j"]
		runProc(make,buildPath.toFile)
		
		val binName=path.append("bin").toFile.list.get(0)
		val binPath = path.append("bin").append(binName)
		Logger.info('''Binary path:«binPath.toString»''')
		val csv_path=rootPath.append("turnus_perf.csv");
		
		val perf_results=perf_profile(binPath,csv_path)
		
		Logger.info('''Measuring done for «launch_config.name»''')
		return perf_results	
	}
}