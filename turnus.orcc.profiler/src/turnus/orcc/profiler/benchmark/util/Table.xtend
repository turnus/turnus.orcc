package turnus.orcc.profiler.benchmark.util

import java.util.List
import java.util.HashMap
import java.util.Map
import java.util.ArrayList
import turnus.common.io.Logger
import java.util.Set
/**
 * Simple Table using List<Map<String,Sring>> as data store
 * Main benefit is checking for the correct fields using the addRow method and allowing easy merging 
 * with combine (join entries on one common field),append(append one table with matching columns to another)
 *  and concat (concatenate two tables with the same columns into a new one)
 */
class Table{
	val Set<String> columns;
	val List<Map<String, String> > rows=new ArrayList;
	val String sep;
	new(Set<String> columns){
		this(columns,"|")		
	}
	/**
	 *  Creates a new Table with the specified columns and separator
	 * @param columns Set<String> Set of column names
	 * @param sep String Separator to be used in the "toString()" method
	 * 
	 */
	new(Set<String> columns,String sep){
		this.columns=columns
		this.sep=sep
	}
	
	def getNumRows(){
		return rows.size
	}
	
	def getColumns(){
		return columns
	}
	/**
	 * Adds a row to the table if the entries in columns and row.keyset match exactly , throws an exception otherwise
	 * @param row Map<String,String> map with the row contents, with keys which must matcht the columns of the table
	 * @exception 
	 */
	def addRow(Map<String,String> row){
		if(columns.containsAll(row.keySet) && row.keySet.containsAll(columns)){
		rows.add(row)	
		}else{
			Logger.error('''Error in adding row:Columns don't match:foreign columns «FOR c:row.keySet.filter[c|!columns.contains(c)] SEPARATOR ","»«c»«ENDFOR»''')
		}
	}
	
	
	override String toString(){
		'''
		«FOR c:columns SEPARATOR sep   »«c»«ENDFOR»
		«FOR r:0..<numRows»
		«FOR c:columns  SEPARATOR sep   »«rows.get(r).get(c)»«ENDFOR»
		«ENDFOR»
		'''
	}
	/** 
	 * 
	 */
	def static concat(Table t1,Table t2){
		if(t1.columns.containsAll(t2.columns) && t2.columns.containsAll(t1.columns)){
			val t=new Table(t1.columns)
			for(c:t.columns){
				t.rows.addAll(t1.rows)
				t.rows.addAll(t2.rows)
			}
			return t
		}
		Logger.error('''Error in concat:Tables differ in columns, foreign columns in t2:«FOR c:t2.columns.filter[c|!t1.columns.contains(c)] SEPARATOR ","»«c»«ENDFOR»''')
		return null
	}
	
	def combine(String join_col,Table t1,Table t2){
		val newRows=new ArrayList
		for(r:t1.rows){
			val newR=new HashMap
			newR.putAll(r)
			val other=t2.rows.findFirst[t|t.get(join_col).equals(r.get(join_col))]
			newR.putAll(other)
			newRows.add(newR)
		}
		val newT=new Table(newRows.last.keySet)
		newT.rows.addAll(newRows)
		return newT
	}
	
	def append(Table t) {
		if(t.columns.containsAll(columns) && columns.containsAll(t.columns)){
				rows.addAll(t.rows)
				return
			}
		Logger.error('''Error in append:Tables differ in columns, foreign columns :«FOR c:t.columns.filter[c|!columns.contains(c)] SEPARATOR ","»«c»«ENDFOR»''')
	}
	
	def getRow(Integer r) {
			rows.get(r)
	}
	
}
