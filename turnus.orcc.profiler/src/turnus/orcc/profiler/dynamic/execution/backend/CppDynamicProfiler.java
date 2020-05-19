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
package turnus.orcc.profiler.dynamic.execution.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import com.google.common.base.Joiner;

import net.sf.orcc.OrccRuntimeException;
import net.sf.orcc.backends.AbstractBackend;
import net.sf.orcc.backends.transform.DisconnectedOutputPortRemoval;
import net.sf.orcc.backends.util.Validator;
import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Network;
import net.sf.orcc.df.transform.Instantiator;
import net.sf.orcc.df.transform.TypeResizer;
import net.sf.orcc.df.transform.UnitImporter;
import net.sf.orcc.df.util.DfVisitor;
import net.sf.orcc.df.util.NetworkValidator;
import net.sf.orcc.graph.Vertex;
import net.sf.orcc.ir.transform.BlockCombine;
import net.sf.orcc.ir.transform.RenameTransformation;
import net.sf.orcc.util.Result;
import net.sf.orcc.util.Void;
import turnus.common.TurnusConstants;
import turnus.common.io.Logger;
import turnus.common.io.StreamGobbler;
import turnus.orcc.profiler.dynamic.execution.backend.templates.ActorHeader;
import turnus.orcc.profiler.dynamic.execution.backend.templates.TracerHeader;
import turnus.orcc.profiler.dynamic.execution.backend.templates.CppNetwork;
import turnus.orcc.profiler.dynamic.execution.backend.templates.CppProfiledActor;
import turnus.orcc.profiler.dynamic.execution.backend.templates.FifoHeader;
import turnus.orcc.profiler.dynamic.execution.backend.templates.GetOptHeader;
import turnus.orcc.profiler.dynamic.execution.backend.templates.GetOptSource;
import turnus.orcc.profiler.dynamic.execution.backend.templates.NativeDisplay;
import turnus.orcc.profiler.dynamic.execution.backend.templates.NativeSource;
import turnus.orcc.profiler.dynamic.execution.backend.transformations.ConnectionReaders;
import turnus.orcc.profiler.dynamic.execution.backend.transformations.CppProfilerTag;
import turnus.orcc.profiler.dynamic.execution.backend.transformations.SharedVariableDetection;

/**
 * 
 * @author Endri Bezati
 * @author Simone Casale Brunet
 *
 */
public class CppDynamicProfiler extends AbstractBackend {

	private String srcGenPath;

	private String includePath;

	private String executionDataPath;

	private Network network;

	private static final String EXECUTABLE_NAME = "dynamic_profiler";

	public CppDynamicProfiler(Network network) {
		this.network = network;
	}

	@Override
	public void compile(IProgressMonitor progressMonitor) {
		// New ResourceSet for a new compilation
		currentResourceSet = new ResourceSetImpl();

		// Initialize the monitor. Can be used to stop the back-end
		// execution and provide feedback to user
		monitor = progressMonitor;

		if (network == null) {
			throw new OrccRuntimeException("The input file seems to not contains any network");
		}

		if (!networkTransfos.isEmpty()) {
			stopIfRequested();
			applyTransformations(network, networkTransfos, debug);
		}

		stopIfRequested();
		beforeGeneration(network);

		stopIfRequested();
		doValidate(network);

		stopIfRequested();
		Result result = doGenerateNetwork(network);
		result.merge(doAdditionalGeneration(network));

		if (!childrenTransfos.isEmpty()) {
			stopIfRequested();
			applyTransformations(network.getAllActors(), childrenTransfos, debug);
		}

		stopIfRequested();
		result = Result.newInstance();
		for (final Vertex vertex : network.getChildren()) {
			stopIfRequested();
			final Actor actor = vertex.getAdapter(Actor.class);
			beforeGeneration(actor);
			result.merge(doGenerateActor(actor));
			result.merge(doAdditionalGeneration(actor));
		}

		stopIfRequested();
		doLibrariesExtraction();
	}

	protected Result doGenerateActor(Actor actor) {
		// -- Print Actor header
		new CppProfiledActor(actor, getOptions()).print(includePath);

		return Result.newInstance();
	}

	@Override
	protected Result doGenerateNetwork(Network network) {
		// -- Compute the Network Template Map
		network.computeTemplateMaps();

		// -- Print network
		CppNetwork printer = new CppNetwork(network, getOptions());
		printer.printMain(srcGenPath);
		return Result.newInstance();
	}

	@Override
	protected void doInitializeOptions() {

		// -- Create the src-gen path
		srcGenPath = outputPath + File.separator + "src-gen";

		File srcGenDir = new File(srcGenPath);
		if (!srcGenDir.exists()) {
			srcGenDir.mkdir();
		}

		// -- Create the Header folder
		includePath = srcGenPath + File.separator + "include";

		File includeDir = new File(includePath);
		if (!includeDir.exists()) {
			includeDir.mkdir();
		}

		// -- Create the execution_data folder
		executionDataPath = outputPath + File.separator + TurnusConstants.EXECUTION_PROFILING_DATA;
		File executionDataDIr = new File(executionDataPath);
		if (!executionDataDIr.exists()) {
			executionDataDIr.mkdir();
		}

		// -- Replacement Map
		Map<String, String> replacementMap = new HashMap<String, String>();
		replacementMap.put("abs", "abs_");
		replacementMap.put("getw", "getw_");
		replacementMap.put("index", "index_");
		replacementMap.put("max", "max_");
		replacementMap.put("min", "min_");
		replacementMap.put("select", "select_");
		replacementMap.put("bitand", "bitand_");
		replacementMap.put("bitor", "bitor_");
		replacementMap.put("not", "not_");
		replacementMap.put("and", "and_");
		replacementMap.put("OUT", "OUT_");
		replacementMap.put("IN", "IN_");
		replacementMap.put("DEBUG", "DEBUG_");
		replacementMap.put("INT_MIN", "INT_MIN_");

		// -- Network and Actor Transformations
		networkTransfos.add(new Instantiator(true));
		networkTransfos.add(new ConnectionReaders());
		networkTransfos.add(new UnitImporter());
		networkTransfos.add(new DisconnectedOutputPortRemoval());
		networkTransfos.add(new TypeResizer(true, false, true, false));
		networkTransfos.add(new SharedVariableDetection());
		childrenTransfos.add(new RenameTransformation(replacementMap));
		childrenTransfos.add(new DfVisitor<Void>(new BlockCombine()));
		childrenTransfos.add(new CppProfilerTag());

		getOptions().put("execution_data_path", executionDataPath);
	}

	@Override
	protected Result doLibrariesExtraction() {
		// -- Print Lib
		new ActorHeader().printActorClass(includePath);
		new FifoHeader().printFifoClass(includePath);
		new TracerHeader().printClientProfilerClass(includePath);
		// new FiringHeader().printFiringClass(includePath);
		new GetOptSource().printGetOptSource(srcGenPath);
		new GetOptHeader().printGetOptHeader(includePath);

		// -- Print Native
		new NativeSource().printSource(srcGenPath);
		new NativeDisplay(network).printDisplay(srcGenPath);

		return Result.newInstance();
	}

	@Override
	protected void doValidate(Network network) {
		Validator.checkMinimalFifoSize(network, fifoSize);

		new NetworkValidator().doSwitch(network);
	}

	protected void stopIfRequested() {
		if (monitor != null) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
		}
	}

	public String getFiringTrace() {
		return executionDataPath + File.separator + "executiondata.etracez";
	}

	public String getSourceGenPath() {
		return srcGenPath;
	}

	public String getExecutionDataPath() {
		return executionDataPath;
	}

	public boolean compileGenerateCode() {
		//  -- Command List
		List<String> commands = new ArrayList<String>();

		// -- Used Compiler
		String compiler = "g++";
		commands.add(compiler);

		String optimization = "-O3";
		commands.add(optimization);

		String cpp11 = "-std=c++11";
		commands.add(cpp11);

		String fpermissive = "-fpermissive";
		commands.add(fpermissive);

		// -- Create compiler arguments
		String gccOut = "-o";
		commands.add(gccOut);
	
		commands.add(EXECUTABLE_NAME);

		// -- Cpp files
		String getOptCpp = "get_opt.cpp";
		commands.add(getOptCpp);

		String mainCpp = "main.cpp";
		commands.add(mainCpp);

		String sourceCpp = "source.cpp";
		commands.add(sourceCpp);

		String displayCpp = "display.cpp";
		commands.add(displayCpp);

		// -- Include Actor headers
		String actorIncludePath = "-I" + includePath;
		commands.add(actorIncludePath);

		// -- SDL Include and Library
		String sdlCFlags = "";
		String sdlLib = "";

		// -- Find SDL C Flags

		try {
			ProcessBuilder builder = new ProcessBuilder("sdl-config", "--cflags");
			Process process = builder.start();
			builder.redirectErrorStream(true);
			InputStream stdout = process.getInputStream();
			String line;
			BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
			if ((line = reader.readLine()) != null) {
				sdlCFlags = line;
				commands.addAll(Arrays.asList(sdlCFlags.split(" ")));
			}

			process.waitFor();
			// TODO Auto-generated catch block
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			ProcessBuilder builder = new ProcessBuilder("sdl-config", "--libs");
			Process process = builder.start();
			builder.redirectErrorStream(true);
			InputStream stdout = process.getInputStream();
			String line;
			BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
			if ((line = reader.readLine()) != null) {
				sdlLib = line;
				commands.addAll(Arrays.asList(sdlLib.split(" ")));
			}

			process.waitFor();
			// TODO Auto-generated catch block
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// -- Boost iostreams
		String boostIOstream = "-lboost_iostreams";
		commands.add(boostIOstream);

		try {
			String compilerCommands = Joiner.on(" ").join(commands);
			Logger.debug("[%s] arguments: %s", compiler, compilerCommands);
			
			// store the compiler output with its arguments
			FileWriter writer = new FileWriter(new File(srcGenPath, "compiler.out"));
			writer.write(compilerCommands + "\n");
			
			ProcessBuilder builder = new ProcessBuilder(commands);
			builder.directory(new File(srcGenPath));
			builder.redirectErrorStream(true);
			Process process = builder.start();
			InputStream stdout = process.getInputStream();

			String line;
			BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

			
			while ((line = reader.readLine()) != null) {
				Logger.debug("[%s] %s", compiler, line);
				writer.write(line + "\n");
			}
			writer.close();

			process.waitFor();
			if (process.exitValue() == 0) {
				Logger.info("Code Compiled Correctly by " + compiler);
				return true;
			} else {
				Logger.error("Code did not compiled correctly by " + compiler);
				return false;
			}

		} catch (Exception ep) {
			System.err.println(ep);
		}

		return false;
	}

	/**
	 * Return <code>true</code> if the program exited correctly
	 * 
	 * @param stimulus
	 * @return
	 */
	public boolean launchExecutable(File stimulus) {
		try {
			String dynamicProfiler = new File(srcGenPath, EXECUTABLE_NAME).getAbsolutePath();
			String stimulusFile = "";
			ProcessBuilder builder;
			if (stimulus != null) {
				stimulusFile = stimulus.getAbsolutePath();
				builder = new ProcessBuilder(dynamicProfiler, "-i", stimulusFile, "-l", "1");
			} else {
				builder = new ProcessBuilder(dynamicProfiler);
			}

			builder.redirectErrorStream(true);
			Process process = builder.start();
			StreamGobbler gobbler = new StreamGobbler("execution output", process.getInputStream());

			process.waitFor();
			gobbler.join();

			if (process.exitValue() == 0) {
				Logger.info("Dynamic Profiler exited correctly");
				return true;
			} else {
				Logger.error("Dynamic Profiler did not exited correctly");
				return false;
			}
		} catch (Exception ep) {
			Logger.debug(ep.getMessage());
		}
		return false;
	}

}
