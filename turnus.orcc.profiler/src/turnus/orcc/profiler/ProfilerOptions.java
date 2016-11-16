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
package turnus.orcc.profiler;

import java.io.File;
import java.util.Map;

import net.sf.orcc.backends.BackendsConstants;
import turnus.common.configuration.Option;
import turnus.common.configuration.Option.Description;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class ProfilerOptions {

	@Description("Enable the constant folding transformation")
	public static final Option<Boolean> CONSTANT_FOLDING;

	@Description("Enable the constant propagation transformation")
	public static final Option<Boolean> CONSTANT_PROPAGATION;

	@Description("Enable the dead action elimination transformation")
	public static final Option<Boolean> DEAD_ACTION_ELIMINATION;

	@Description("Enable the dead code elimination transformation")
	public static final Option<Boolean> DEAD_CODE_ELIMINATION;

	@Description("Enable the expression evaluation transformation")
	public static final Option<Boolean> EXPRESSION_EVALUATION;

	@Description("The buffer size map configuration")
	public static final Option<Map<String,String>> BUFFER_SIZE_MAP;

	@Description("The scheduler name used when interpreting the code")
	public static final Option<String> SCHEDULER;

	@Description("Enable the native ports type resize transformation")
	public static final Option<Boolean> TYPE_RESIZE_NATIVEPORTS;

	@Description("Enable the \"to32Bits\" type resize transformation")
	public static final Option<Boolean> TYPE_RESIZE_TO32BITS;

	@Description("Enable the \"toNBits\" type resize transformation")
	public static final Option<Boolean> TYPE_RESIZE_TONBITS;

	@Description("Enable the variable initializer transformation")
	public static final Option<Boolean> VARIABLE_INITIALIZER; 
	
	@Description("Delete the generated source data directory")
	public static final Option<Boolean> DELETE_SRCGEN_DIRECTORY;
	
	@Description("Delete the profiling trace files generated by the cpp execution profiler")
	public static final Option<Boolean> DELETE_PROFILING_TRACE_DIRECTORY;
	
	@Description("Check array bounds")
	public static final Option<Boolean> CHECK_ARRAY_INBOUNDS;
	
	@Description("Inline functions and procedures")
	public static final Option<Boolean> INLINE_FUNCTIONS_AND_PROCEDURES;
	
	@Description("Use the smart scheduler")
	public static final Option<Boolean> USE_SMART_SCHEDULER;
	
	@Description("Generate the source code inlining debug directives")
	public static final Option<Boolean> CODE_GENERATION_DEBUG_DIRECTIVES;
	
	@Description("The numa profiling mode: read or write")
	public static final Option<String> NUMA_PROFILING_MODE;
	
	@Description("The numa sampling rate")
	public static final Option<Integer> NUMA_SAMPLING_RATE;
	
	@Description("The number of executions in a loop (-l)")
	public static final Option<Integer> EXECUTION_LOOP;

	@Description("Path to native library folder containing CMakeLists.txt")
	public static final Option<File> LINK_NATIVE_LIBRARY_FOLDER;

	@Description("Names of the header files (separated by ';') containing all prototypes")
	public static final Option<String> LINK_NATIVE_LIBRARY_HEADERS;

	static {
		
		EXECUTION_LOOP = Option.create().//
				setName("executionLoop").//
				setDescription("The number of executions in a loop (-l)").//
				setLongName("turnus.profiler.orcc.execution.loop").//
				setType(Integer.class).build();
		
		NUMA_SAMPLING_RATE = Option.create().//
				setName("numaSamplingRate").//
				setDescription("The numa sampling rate").//
				setLongName("turnus.profiler.orcc.numa.sampling").//
				setType(Integer.class).build();
		
		NUMA_PROFILING_MODE = Option.create().//
				setName("numaMode").//
				setDescription("The numa profiling mode: read or write").//
				setLongName("turnus.profiler.orcc.numa.mode").//
				setType(String.class).build();
		
		LINK_NATIVE_LIBRARY_FOLDER = Option.create().//
				setName("linkNativeLibraryFolder").//
				setDescription("Link with a native library containing native functions").//
				setLongName("turnus.profiler.orcc.numa.linkNativeLibraryFolder").//
				setType(File.class).build();
		
		LINK_NATIVE_LIBRARY_HEADERS = Option.create().//
				setName("linkNativeLibraryHeaders").//
				setDescription("Names of the header files (separated by ';') containing all prototypes").//
				setLongName("turnus.profiler.orcc.numa.linkNativeLibraryHeaders").//
				setType(String.class).build();
		
		CODE_GENERATION_DEBUG_DIRECTIVES = Option.create().//
				setName("codeDebug").//
				setDescription("Generate the source code inlining debug directives").//
				setLongName("turnus.profiler.orcc.source.debug").//
				setType(Boolean.class).build();
		
		USE_SMART_SCHEDULER = Option.create().//
				setName("smartScheduler").//
				setDescription("Use the smart scheduler").//
				setLongName(BackendsConstants.NEW_SCHEDULER).//
				setType(Boolean.class).build();
		
		INLINE_FUNCTIONS_AND_PROCEDURES = Option.create().//
				setName("inlineFuncProc").//
				setDescription("Inline functions and procedures").//
				setLongName(BackendsConstants.INLINE).//
				setType(Boolean.class).build();
		
		CHECK_ARRAY_INBOUNDS = Option.create().//
				setName("checkArrayBounds").//
				setDescription("Check array bounds").//
				setLongName(BackendsConstants.CHECK_ARRAY_INBOUNDS).//
				setType(Boolean.class).build();
		
		DELETE_PROFILING_TRACE_DIRECTORY = Option.create().//
				setName("deletedProfilingTrace").//
				setDescription("Delete the profiling trace files generated by the cpp execution profiler").//
				setLongName("turnus.profiler.orcc.delete.profilingTrace").//
				setType(Boolean.class).build();
		
		
		DELETE_SRCGEN_DIRECTORY = Option.create().//
				setName("deletedSrcGen").//
				setDescription("Delete the generated source data directory").//
				setLongName("turnus.profiler.orcc.delete.srcGen").//
				setType(Boolean.class).build();
		
		
		SCHEDULER = Option.create().//
				setName("scheduler").//
				setDescription("The scheduler name used when interpreting the code").//
				setLongName("turnus.profiler.orcc.scheduler").//
				setType(String.class).build();

		CONSTANT_FOLDING = Option.create().//
				setName("constantFolding").//
				setDescription("Enable the constant folding transformation").//
				setLongName("turnus.profiler.orcc.transfo.constantFolding").//
				setType(Boolean.class).build();

		CONSTANT_PROPAGATION = Option.create().//
				setName("constantPropagation").//
				setDescription("Enable the constant propagation transformation").//
				setLongName("turnus.profiler.orcc.transfo.constantPropagation").//
				setType(Boolean.class).build();

		DEAD_ACTION_ELIMINATION = Option.create().//
				setName("deadAction").//
				setDescription("Enable the dead action elimination transformation").//
				setLongName("turnus.profiler.orcc.transfo.deadAction").//
				setType(Boolean.class).build();

		DEAD_CODE_ELIMINATION = Option.create().//
				setName("deadCode").//
				setDescription("Enable the dead code elimination transformation").//
				setLongName("turnus.profiler.orcc.transfo.deadCode").//
				setType(Boolean.class).build();

		EXPRESSION_EVALUATION = Option.create().//
				setName("exprEval").//
				setDescription("Enable the expression evaluation transformation").//
				setLongName("turnus.profiler.orcc.transfo.exprEval").//
				setType(Boolean.class).build();

		VARIABLE_INITIALIZER = Option.create().//
				setName("varInit").//
				setDescription("Enable the variable initializer transformation").//
				setLongName("turnus.profiler.orcc.transfo.varInit").//
				setType(Boolean.class).build();

		TYPE_RESIZE_TONBITS = Option.create().//
				setName("resizeToN").//
				setDescription("Enable the \"toNBits\" type resize transformation").//
				setLongName("turnus.profiler.orcc.transfo.resizeToN").//
				setType(Boolean.class).build();

		TYPE_RESIZE_TO32BITS = Option.create().//
				setName("resizeTo32").//
				setDescription("Enable the \"to32Bits\" type resize transformation").//
				setLongName("turnus.profiler.orcc.transfo.resizeTo32").//
				setType(Boolean.class).build();

		TYPE_RESIZE_NATIVEPORTS = Option.create().//
				setName("resizePorts").//
				setDescription("Enable the native ports type resize transformation").//
				setLongName("turnus.profiler.orcc.transfo.resizePorts").//
				setType(Boolean.class).build();

		BUFFER_SIZE_MAP = Option.create().//
				setName("bufferConfig").//
				setDescription("The buffer size map configuration").//
				setLongName("turnus.profiler.orcc.buffersSize").//
				setType(Map.class).build(); 
	}

	/**
	 * Private constructor
	 */
	private ProfilerOptions() {

	}

}