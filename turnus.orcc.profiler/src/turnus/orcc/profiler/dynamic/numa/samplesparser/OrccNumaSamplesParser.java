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
package turnus.orcc.profiler.dynamic.numa.samplesparser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import turnus.common.util.MathUtils;
import turnus.model.analysis.profiler.AccessData;
import turnus.model.analysis.profiler.ActionMemoryProfilingData;
import turnus.model.analysis.profiler.BufferAccessData;
import turnus.model.analysis.profiler.LocalVariableAccessData;
import turnus.model.analysis.profiler.MemoryAccessData;
import turnus.model.analysis.profiler.MemoryProfilingReport;
import turnus.model.analysis.profiler.ProfilerFactory;
import turnus.model.analysis.profiler.SharedVariableAccessData;
import turnus.model.analysis.profiler.StateVariableAccessData;
import turnus.model.dataflow.Actor;
import turnus.model.dataflow.Buffer;
import turnus.model.dataflow.Network;
import turnus.orcc.profiler.dynamic.numa.backend.templates.NetworkPrinter;
import turnus.orcc.profiler.dynamic.numa.samplesparser.ObjectSymbol.ObjectType;

/**
 * This class is responsible for the parsing of the memory sampling files
 * provided by the numa profiler. This class relies on objdump and on stack
 * information provided at runtime in order to associate the memory samples
 * provided by numap with information in the dataflow model.
 * 
 * @author Manuel Selva 
 *
 */
public class OrccNumaSamplesParser {

	private static final String END_THREAD_LINE_PREFIX = "// End Thread:";

	private static final String NEW_THREAD_LINE_PREFIX = "// New Thread:";

	private static final String[] IGNORED_LINES_PREFIXES = { NEW_THREAD_LINE_PREFIX, END_THREAD_LINE_PREFIX };

	private static final String NOT_FOUND = "NOT_FOUND";

	private static final String THREAD_STACK_PREFIX = "Stack Info:";

	private final String appBinaryPath;

	private final Network network;

	private final String numaMode;

	private final String resultsFolder;
	
	private HashMap<Integer, List<String>> fifoMappingInfo = null;
	
	/**
	 * The list of functions of the binary computed using objdump.
	 */
	private final List<FunctionSymbol> functions;

	/**
	 * The list of global symbols of the binary computed using objdump.
	 */
	private final List<ObjectSymbol> globalSymbols;

	/**
	 * The list of stack symbols
	 */
	private final List<ObjectSymbol> stackSymbols;

	/**
	 * The map of stacks information, one for each thread of the application.
	 */
	private final Map<Integer, StackInfo> threadsStacks;

	public OrccNumaSamplesParser(String resultsFolder, String appBinaryPath, Network network, NetworkPrinter networkPrinter, String numaMode)
			throws IOException {
		this.appBinaryPath = appBinaryPath;
		this.resultsFolder = resultsFolder;
		this.network = network;
		this.numaMode = numaMode;
		this.functions = new LinkedList<FunctionSymbol>();
		this.globalSymbols = new LinkedList<ObjectSymbol>();
		this.stackSymbols = new LinkedList<ObjectSymbol>();
		this.threadsStacks = new HashMap<Integer, StackInfo>();
		this.fifoMappingInfo = networkPrinter.getFifoMappingInfo();
		buildSymbolTable();
		System.out.println(
				"functions.length: " + this.functions.size() + " objects.length: " + this.globalSymbols.size());
	}

	/**
	 * Create and save in the functions list a function symbol object for the
	 * given function line outputed by objdump
	 * 
	 * @param line
	 *            the function line
	 */
	private void buildFunctionSymbol(String[] line) {
		BigInteger address = new BigInteger(line[0], 16);
		String section = line[3];
		int size = Integer.parseInt(line[4], 16);
		String name = line[5];
		functions.add(new FunctionSymbol(address, size, section, name));
	}

	/**
	 * Create and save in the global symbols list a global symbol object for the
	 * given line outputed by objdump
	 * 
	 * @param line
	 *            the function line
	 */
	private void buildGlobalSymbol(String[] line) {
		BigInteger address = new BigInteger(line[0], 16);
		String section = line[3];
		int size = Integer.parseInt(line[4], 16);
		String name = line[5];
		
		ObjectType objType = ObjectType.STATE_VARIABLE;
		if (name.startsWith("shared_")) {
			objType = ObjectType.SHARED_VARIABLE;
		}
		else if (name.startsWith("fifo_") || name.startsWith("array_")) {
			objType = ObjectType.BUFFER;
		} 
		
		ObjectSymbol globalSymbol = new ObjectSymbol(address, size, section, name, objType);	
				
		if(objType.equals(ObjectType.BUFFER)) {
			String temp[] = name.split("(fifo|array)_");
			int fifoNum = MathUtils.safeParseInt(temp[1], -1);
			if(fifoNum != -1) {
				List<String> fifoInfo;
				if((fifoInfo = this.fifoMappingInfo.get(fifoNum)) != null) {
					globalSymbol.setSourceActor(fifoInfo.get(0));
					globalSymbol.setSourcePort(fifoInfo.get(1));
					globalSymbol.setTargetActor(fifoInfo.get(2));
					globalSymbol.setTargetPort(fifoInfo.get(3));					
				}
			}
			else {
				for (Actor act : network.getActors()) {
					if(temp[1].startsWith(act.getName()+"_"))
					{
						String temp2[] = name.split(act.getName()+"_");
						for (Buffer buffer : act.getBuffers()) {
							if(buffer.getSource().getName().equals(temp2[1]) || buffer.getTarget().getName().equals(temp2[1])) {
								globalSymbol.setSourceActor(buffer.getSource().getOwner().getName());
								globalSymbol.setSourcePort(buffer.getSource().getName());
								globalSymbol.setTargetActor(buffer.getTarget().getOwner().getName());
								globalSymbol.setTargetPort(buffer.getTarget().getName());
								break;
							}
						}
					}
				}
			}
		}
		
		globalSymbols.add(globalSymbol);
	}

	/**
	 * Build the symbol table using objdump. This symbol table is splitted in
	 * two lists: one containing the functions of the binary and one containing
	 * the global symbols of the binary. The functions list is latter used to
	 * know which actor/action generated the sample and the global symbols list
	 * is used to know which global symbol the actor/action accessed.
	 * 
	 * @throws IOException
	 */
	private void buildSymbolTable() throws IOException {
		Process process = new ProcessBuilder("objdump", "-t", appBinaryPath).start();
		InputStream is = process.getInputStream();
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);
		String line;
		while ((line = br.readLine()) != null) {
			String[] splitLine = line.split("\\s+");
			if (splitLine.length < 6) { // Ignoring lines before and after
										// symbols
				continue;
			}
			String type = splitLine[2];
			switch (type) {
			case "F":
				buildFunctionSymbol(splitLine);
				break;
			default:
				buildGlobalSymbol(splitLine);
				break;
			}
		}
	}

	/**
	 * Find or create in the given report an action memory profiling data for
	 * the given action of the given actor.
	 * 
	 * @param actor
	 *            the actor containing the action
	 * @param action
	 *            the action to search
	 * @param report
	 *            the report to use
	 * @return the action memory profiling data object found or created
	 */
	private ActionMemoryProfilingData findActionMemoryProfilingData(String actor, String action,
			MemoryProfilingReport report) {
		ActionMemoryProfilingData data = report.getActionData(actor, action);
		if (data == null) {
			data = ProfilerFactory.eINSTANCE.createActionMemoryProfilingData();
			data.setAction(action);
			data.setActor(actor);
			report.getActionsData().add(data);
		}
		return data;
	}

	/**
	 * Search the function associated to the given ip.
	 * 
	 * @param ip
	 *            the ip to search
	 * @return the function containing ip or null if such a function doesn't
	 *         exist
	 */
	private Symbol findFunction(BigInteger ip) {
		for (Symbol symbol : functions) {
			if (symbol.contains(ip)) {
				return symbol;
			}
		}
		return null;
	}

	/**
	 * Search the global symbol associated to the given data address.
	 * 
	 * @param addr
	 *            the address to search
	 * @return the global symbol containing addr or null if such a function
	 *         doesn't exist
	 */
	private ObjectSymbol findGlobal(BigInteger addr) {
		for (ObjectSymbol symbol : globalSymbols) {
			if (symbol.contains(addr)) {
				return symbol;
			}
		}
		return null;
	}

	/**
	 * Find or create in the given action memory profiling data object a memory
	 * access data object corresponding to the given symbol.
	 * 
	 * @param actionData
	 *            the action data to search in
	 * @param objectSymbol
	 *            the symbol to search
	 * @return the memory access data object found or created
	 */
	private MemoryAccessData findReadAccessData(ActionMemoryProfilingData actionData, ObjectSymbol objectSymbol) {
		MemoryAccessData accessData = null;
		ObjectType type = objectSymbol.getType();
		switch (type) {
		case BUFFER:
			accessData = actionData.getReadBufferData(objectSymbol.getSourceActor(), objectSymbol.getSourcePort(),
					objectSymbol.getTargetActor(), objectSymbol.getTargetPort());
			if (accessData == null) {
				BufferAccessData data = ProfilerFactory.eINSTANCE.createBufferAccessData();
				data.setSourceActor(objectSymbol.getSourceActor());
				data.setSourcePort(objectSymbol.getSourcePort());
				data.setTargetActor(objectSymbol.getTargetActor());
				data.setTargetPort(objectSymbol.getTargetPort());
				accessData = data;
				actionData.getReads().add(accessData);
			}
			break;
		case LOCAL_VARIABLE:
			accessData = actionData.getReadLocalVariableData(objectSymbol.getName());
			if (accessData == null) {
				LocalVariableAccessData data = ProfilerFactory.eINSTANCE.createLocalVariableAccessData();
				data.setName(objectSymbol.getName());
				accessData = data;
				actionData.getReads().add(accessData);
			}
			break;
		case SHARED_VARIABLE:
			accessData = actionData.getReadSharedVariableData(objectSymbol.getName());
			if (accessData == null) {
				SharedVariableAccessData data = ProfilerFactory.eINSTANCE.createSharedVariableAccessData();
				data.setName(objectSymbol.getName());
				accessData = data;
				actionData.getReads().add(accessData);
			}
			break;
		case STATE_VARIABLE:
			accessData = actionData.getReadStateVariableData(objectSymbol.getName());
			if (accessData == null) {
				StateVariableAccessData data = ProfilerFactory.eINSTANCE.createStateVariableAccessData();
				data.setName(objectSymbol.getName());
				accessData = data;
				actionData.getReads().add(accessData);
			}
			break;
		}
		return accessData;
	}

	/**
	 * Find or create a stack symbol for the given stack address
	 * 
	 * @param stackAddr
	 *            the stack address to search
	 * @return the associated symbol
	 */
	private ObjectSymbol findStackSymbol(BigInteger stackAddr) {
		for (ObjectSymbol stackSym : stackSymbols) {
			if (stackSym.contains(stackAddr)) {
				return stackSym;
			}
		}
		
		//ObjectSymbol stackSym = new ObjectSymbol(stackAddr, 0, "", "stack@" + Long.toHexString(stackAddr),
		//		ObjectType.LOCAL_VARIABLE);
		
		ObjectSymbol stackSym = new ObjectSymbol(stackAddr, 0, "", "stack@" + stackAddr.toString(16),
				ObjectType.LOCAL_VARIABLE);
		
		return stackSym;
	}

	/**
	 * Find or create in the given action memory profiling data object a memory
	 * access data object corresponding to the given symbol.
	 * 
	 * @param actionData
	 *            the action data to search in
	 * @param objectSymbol
	 *            the symbol to search
	 * @return the memory access data object found or created
	 */
	private MemoryAccessData findWriteAccessData(ActionMemoryProfilingData actionData, ObjectSymbol objectSymbol) {
		MemoryAccessData accessData = null;
		ObjectType type = objectSymbol.getType();
		switch (type) {
		case BUFFER:
			accessData = actionData.getWriteBufferData(objectSymbol.getSourceActor(), objectSymbol.getSourcePort(),
					objectSymbol.getTargetActor(), objectSymbol.getTargetPort());
			if (accessData == null) {
				BufferAccessData data = ProfilerFactory.eINSTANCE.createBufferAccessData();
				data.setSourceActor(objectSymbol.getSourceActor());
				data.setSourcePort(objectSymbol.getSourcePort());
				data.setTargetActor(objectSymbol.getTargetActor());
				data.setTargetPort(objectSymbol.getTargetPort());
				accessData = data;
				actionData.getWrites().add(accessData);
			}
			break;
		case LOCAL_VARIABLE:
			accessData = actionData.getWriteLocalVariableData(objectSymbol.getName());
			if (accessData == null) {
				LocalVariableAccessData data = ProfilerFactory.eINSTANCE.createLocalVariableAccessData();
				data.setName(objectSymbol.getName());
				accessData = data;
				actionData.getWrites().add(accessData);
			}
			break;
		case SHARED_VARIABLE:
			accessData = actionData.getWriteSharedVariableData(objectSymbol.getName());
			if (accessData == null) {
				SharedVariableAccessData data = ProfilerFactory.eINSTANCE.createSharedVariableAccessData();
				data.setName(objectSymbol.getName());
				accessData = data;
				actionData.getWrites().add(accessData);
			}
			break;
		case STATE_VARIABLE:
			accessData = actionData.getWriteStateVariableData(objectSymbol.getName());
			if (accessData == null) {
				StateVariableAccessData data = ProfilerFactory.eINSTANCE.createStateVariableAccessData();
				data.setName(objectSymbol.getName());
				accessData = data;
				actionData.getWrites().add(accessData);
			}
			break;
		}
		return accessData;
	}

	/**
	 * Indicates wether or not the given line of a memory read or write samples
	 * file should be ignored. These files contain information about threads in
	 * addition to samples, we ignore these lines.
	 * 
	 * @param line
	 *            the line to test
	 * @return true if the line must be ignored and false otherwise
	 */
	private boolean isIgnored(String line) {
		for (String ignored : IGNORED_LINES_PREFIXES) {
			if (line.startsWith(ignored)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Parse the memory samples file.
	 * 
	 * @return a memory profiling report
	 * @throws IOException
	 *             if the file to be parsed couldn't be read
	 */
	public MemoryProfilingReport parse() throws IOException {

		// Create the report
		ProfilerFactory f = ProfilerFactory.eINSTANCE;
		MemoryProfilingReport report = f.createMemoryProfilingReport();
		report.setAlgorithm("NUMA profiler");
		report.setDate(new Date());
		report.setNetworkName("The network name");

		// Parse stack info for all threads.
		BufferedReader br = new BufferedReader(
				new FileReader(resultsFolder + "/__execution_data/mem-" + numaMode + "-threads-stacks"));
		String line;
		while ((line = br.readLine()) != null) {
			if (line.startsWith(THREAD_STACK_PREFIX)) {
				String[] splitLine = line.split(", ");
				int tid = Integer.parseInt(splitLine[0].split(" = ")[1], 16);
				long start = Long.parseLong(splitLine[1].split(" = ")[1], 16);
				long end = Long.parseLong(splitLine[2].split(" = ")[1], 16);
				threadsStacks.put(tid, new StackInfo(tid, start, end));
			}
		}
		br.close();

		// Parse samples
		int currThreadID = -1;
		br = new BufferedReader(new FileReader(resultsFolder + "/__execution_data/mem-" + numaMode + "-samples"));
		if (numaMode.equals("write")) {
			// Parse write samples
			while ((line = br.readLine()) != null) {
				if (line.startsWith(NEW_THREAD_LINE_PREFIX)) {
					currThreadID = Integer.parseInt(line.split(" = ")[1], 16);
				} else if (isIgnored(line)) {
					continue;
				} else
					parseWriteLine(line, report, currThreadID);
			}
		} else {
			// Parse read samples
			while ((line = br.readLine()) != null) {
				if (line.startsWith(NEW_THREAD_LINE_PREFIX)) {
					currThreadID = Integer.parseInt(line.split(" = ")[1], 16);
				} else if (isIgnored(line)) {
					continue;
				} else
					parseReadLine(line, report, currThreadID);
			}
		}
		br.close();

		return report;
	}

	/**
	 * Par a single line from the file containing memory read samples. Each line
	 * represents a single memory read sample.
	 * 
	 * @param line
	 *            the line to parse.
	 * @param report
	 *            the report to use to find or create an action memory profiling data object.
	 * @param theadId
	 *            the id of the thread of the sample represented by the given line.
	 */
	private void parseReadLine(String line, MemoryProfilingReport report, int threadId) {

		// Parse the sample
		String[] splitLine = line.split(", ");
		BigInteger ip = new BigInteger(splitLine[0].split(" = ")[1], 16);
		BigInteger addr = new BigInteger(splitLine[1].split(" = ")[1], 16);
		int weight = Integer.parseInt(splitLine[2].split(" = ")[1], 16);
		String level = splitLine[3].split(" = ")[1];

		// Search for associated actor and action by using the function
		// associated to the sample
		Symbol functionSymbol = findFunction(ip);
		String actor = NOT_FOUND;
		String action = NOT_FOUND;
		if (functionSymbol != null) {
			for (Actor act : network.getActors()) {
				if (functionSymbol.name.startsWith(act.getName())) {
					actor = act.getName();
					action = functionSymbol.name.substring(actor.length());
					break;
				}
			}
		}

		// Search for the global symbol associated to the sample
		ActionMemoryProfilingData actionData = findActionMemoryProfilingData(actor, action, report);
		ObjectSymbol objectSymbol = findGlobal(addr);

		// Search in stack if not global symbol
		if (objectSymbol == null) {
			StackInfo stackInfo = threadsStacks.get(threadId);
			if (stackInfo.contains(addr)) {
				objectSymbol = findStackSymbol(addr);
			}
		}

		// Create memory access data object
		if (objectSymbol != null) {
			MemoryAccessData memoryAccessData = findReadAccessData(actionData, objectSymbol);
			AccessData accessData = memoryAccessData.getAccessesData().get(level);
			if (accessData == null) {
				accessData = ProfilerFactory.eINSTANCE.createAccessData();
				accessData.setAccesses(0);
				accessData.setTotal(0);
				accessData.setMin(Double.MAX_VALUE);
				accessData.setMax(Double.MIN_VALUE);
				memoryAccessData.getAccessesData().put(level, accessData);
			}
			accessData.setAccesses(accessData.getAccesses() + 1);
			accessData.setTotal(accessData.getTotal() + weight);
			if (weight > accessData.getMax()) {
				accessData.setMax(weight);
			}
			if (weight < accessData.getMin()) {
				accessData.setMin(weight);
			}
		}
	}

	/**
	 * Par a single line from the file containing memory write samples. Each
	 * line represents a single memory write sample.
	 * 
	 * @param line
	 *            the line to parse.
	 * @param report
	 *            the report to use to find or create an action memory profiling data object.
	 * @param theadId
	 *            the id of the thread of the sample represented by the given line.
	 */
	private void parseWriteLine(String line, MemoryProfilingReport report, int threadId) {

		// Parse the sample
		String[] splitLine = line.split(", ");
		BigInteger ip = new BigInteger(splitLine[0].split(" = ")[1], 16);
		BigInteger addr = new BigInteger(splitLine[1].split(" = ")[1], 16);
		String level = splitLine[2].split(" = ")[1];

		// Search for associated actor and action by using the function
		// associated to the sample
		Symbol functionSymbol = findFunction(ip);
		String actor = NOT_FOUND;
		String action = NOT_FOUND;
		if (functionSymbol != null) {
			for (Actor act : network.getActors()) {
				if (functionSymbol.name.startsWith(act.getName())) {
					actor = act.getName();
					action = functionSymbol.name.substring(actor.length());
					break;
				}
			}
		}

		// Search for the global symbol associated to the sample
		ActionMemoryProfilingData actionData = findActionMemoryProfilingData(actor, action, report);
		ObjectSymbol objectSymbol = findGlobal(addr);

		// Search in stack if not global symbol
		if (objectSymbol == null) {
			StackInfo stackInfo = threadsStacks.get(threadId);
			if (stackInfo.contains(addr)) {
				objectSymbol = findStackSymbol(addr);
			}
		}

		// Create memory access data object
		if (objectSymbol != null) {
			MemoryAccessData memoryAccessData = findWriteAccessData(actionData, objectSymbol);
			AccessData accessData = memoryAccessData.getAccessesData().get(level);
			if (accessData == null) {
				accessData = ProfilerFactory.eINSTANCE.createAccessData();
				accessData.setAccesses(0);
				accessData.setTotal(0);
				accessData.setMin(0);
				accessData.setMax(0);
				memoryAccessData.getAccessesData().put(level, accessData);
			}
			accessData.setAccesses(accessData.getAccesses() + 1);
		}
	}
}
