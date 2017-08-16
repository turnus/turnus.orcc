package turnus.orcc.profiler.benchmark.util

import org.eclipse.core.runtime.IPath
import java.io.BufferedReader
import turnus.common.io.Logger
import java.io.FileReader

class CSVReader{
	val IPath path;
	var BufferedReader br=null
	var open=false
	val String sep;
	val String comment_marker;
	new(IPath path,String sep){
		this.path=path
		this.sep=sep
		comment_marker=null
		open()
	}
	new(IPath path,String sep, String comment_marker){
		this.path=path
		this.sep=sep
		this.comment_marker=comment_marker
		open()
		Logger.info('''
		Opened «path.toString» 
		''')
	}
	def is_open(){
		return open
	}
	def open(){
		br=new BufferedReader(new FileReader(path.toFile))
		open=true
	}
	
	def String[] next(){
		if(is_open){
			var line=br.readLine;
			while(line != null && ((comment_marker!=null && line.startsWith(comment_marker)) || line.empty)){
				line=br.readLine
			}
			if(line==null){
				open=false
				br.close
				return null
			}else{
				return line.split(sep)
			}
		}else{
			return null
		}
	}
	
}