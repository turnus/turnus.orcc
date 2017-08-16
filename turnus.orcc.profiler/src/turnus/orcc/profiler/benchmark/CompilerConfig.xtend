package turnus.orcc.profiler.benchmark

import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.core.runtime.IPath
import turnus.common.io.Logger
import java.io.File

import static net.sf.orcc.OrccLaunchConstants.XDF_FILE
import java.io.BufferedReader
import java.io.FileReader
import java.util.List
import java.util.HashMap
import java.util.ArrayList
import java.util.Map
import com.google.common.collect.TreeBasedTable

import static net.sf.orcc.OrccLaunchConstants.BACKEND;
import turnus.orcc.profiler.benchmark.util.Table
import java.io.InputStreamReader

abstract class CompilerConfig {
	protected val ILaunchConfiguration launch_config;
	protected val File input
	protected val Integer n_loops;
	protected val String other_options;
	
	new(ILaunchConfiguration conf, File input, Integer n_loops, String other_options){
		this.launch_config=conf
		this.input = input
		this.n_loops = n_loops
		this.other_options = other_options
	}
	
	new(ILaunchConfiguration conf){
		this.launch_config=conf
		this.input = null
		this.n_loops = 1
		this.other_options = ""
	}
	
	def config(){
		return this.launch_config
	}
		def runProc(String[] args,File dir){
		val proc_build = new ProcessBuilder(args)
		proc_build.directory(dir)
		Logger.info('''«String.join(" ",args)» in «dir.toString»''')
		
		val proc =proc_build.start
		val mkdir_out = new BufferedReader(new InputStreamReader(proc.inputStream))
		val mkdir_err = new BufferedReader(new InputStreamReader(proc.errorStream))
		var String out=null;
		var String err = null;
		while((out=mkdir_out.readLine)!=null){
			Logger.info(out)
		}
		while((err=mkdir_err.readLine)!=null){
			Logger.warning(err)
		}
		
		proc.waitFor
		
	}
	
	abstract def Table measure(IPath path) 
	
}