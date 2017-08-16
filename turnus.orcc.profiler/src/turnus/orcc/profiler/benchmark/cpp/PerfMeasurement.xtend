package turnus.orcc.profiler.benchmark.cpp

import java.util.List
import java.util.Map
import org.eclipse.core.runtime.IPath
import turnus.common.io.Logger
import java.util.ArrayList
import java.util.HashMap
import turnus.orcc.profiler.benchmark.util.Table
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger

/**
 * 
 *  Runs and parses a linux "perf stat" call, @return a Table with the specified fields + runTime and config name
 * 
 */
class PerfMeasurement{
	val List<Map<String,String>> raw_rows;
	val fieldsWithoutVariance = #["value","unit","eventName","runTime","percentRuntime","metric value","metric unit"]
	val fieldsWithVariance = #["value","unit","eventName","variance","runTime","percentRuntime","metric value","metric unit"]
	
	val List<String> events;
	String name;
	val Integer numMeas
	val Boolean normalize
	val Double numCores
	
	/**
	 *  PerfMeasurement constructor
	 *  @param name String name of the configuration used for the measuremet
	 *  @param binpath IPath of the compiled test binary
	 *  @param events List<String> specifying the perf events measured 
	 *  @param input String additional input for the test binary
	 *  @param numCores number of Cores which will be used by the binary, to normalize runTime
	 *  @param Boolean normalize whether or not we use the "--scale" option of perf @default is false
	 *  @param Integer numberOfMeasurements how many measurement rounds we use with perfs "-r" option. @default is 10
	 *  
	 */
	new(String name,IPath binpath,List<String> events, String input,Integer numCores,Boolean normalize,Integer numberOfMeasurements){
		this.numMeas = numberOfMeasurements
		this.name=name
		this.events = events
		this.numCores = numCores as int as double as Double
		this.normalize=normalize
		
		val IPath csv_path = binpath.removeLastSegments(2).append("test.csv")
		val String[] perf = #["bash","-c",
		'''/usr/bin/perf stat «IF normalize»-c «ENDIF»-r «numMeas» -x ';'  -o «csv_path.toOSString» -e «FOR e:events SEPARATOR ","»«e»«ENDFOR» -- «binpath.toOSString» «input»'''
		]
		Logger.info("Using perfcmd:\n"+ String.join(" ",perf))
		runProc(perf,binpath.removeLastSegments(2).toFile)
		val fields =if(numMeas>1){fieldsWithVariance}else{fieldsWithoutVariance}
		val csv=new PerfCSVReader(csv_path,";",fields,"#");
		val rows=new ArrayList
		//TODO write iterator in CSVreader?
		while(csv.is_open){
			val row=csv.parse_next
			if(row!=null){
			rows.add(row)
			}
		}
		raw_rows=rows
	}
	
	new(String name,IPath binpath,List<String> events, String input,Integer numCores){
		this(name,binpath,events,input,numCores,false,10)
	}
	
	def runProc(String[] args,File dir){
		val proc_build = new ProcessBuilder(args)
		proc_build.directory(dir)
		
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
	/**
	 *  Returns a Table with config, runTime (normalized by number of cores) and the fields specified in the constructor
	 */
	def asTable(){
		val id_col="config"
		val columns=new ArrayList
		columns.add(id_col)
		columns.addAll(raw_rows.map[r|r.get("eventName").replace(":u","")])
		val trow=new HashMap
		trow.put(id_col,'''«name»''')
		var runTime=0.0;
		for(r:raw_rows){
			val key=r.get("eventName").replace(":u","")
			val value=r.get("value")
			val variance=r.get("variance")
			val rts=r.get("runTime")
			val rt= Double.parseDouble(rts)
			if(rt>runTime){
				runTime=rt
			}
			if(variance!=null){
				val var_key='''«key»_variance'''
				trow.put(var_key,variance)
				columns.add(var_key)
			}
			trow.put(key,value)
		}
		trow.put("runTime",'''«runTime/numCores»''')
		columns.add("runTime")
		val table = new Table(columns.toSet)
		table.addRow(trow)
		table
	}
}
