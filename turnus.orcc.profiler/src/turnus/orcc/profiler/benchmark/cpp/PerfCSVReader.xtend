package turnus.orcc.profiler.benchmark.cpp

import turnus.orcc.profiler.benchmark.util.CSVReader
import java.util.List
import org.eclipse.core.runtime.IPath
import java.util.Map
import java.util.HashMap
import turnus.common.io.Logger

class PerfCSVReader extends CSVReader{
	val List<String> fields;
	new(IPath path, String sep,List<String> fields) {
		super(path, sep)
		this.fields=fields
	}
	new(IPath path, String sep,List<String> fields,String comment_marker) {
		super(path, sep,comment_marker)
		this.fields=fields
	}
	def Map<String,String> parse_next(){
		val raw_fields=super.next
		if(raw_fields==null){
			return null
		}
		
		val row=new HashMap;
		if(fields.size!=raw_fields.size){
			Logger.error('''
			Given field mask doesn't match actual fields in length, mask given:
			«FOR f:fields SEPARATOR ","»«f»«ENDFOR»
			actual fields
			«FOR f:raw_fields SEPARATOR ","»«f»«ENDFOR»
			''')
		}
		
		for(i:0..<fields.size){
			row.put(fields.get(i),raw_fields.get(i))
		}
		return row
	}
	
	
}