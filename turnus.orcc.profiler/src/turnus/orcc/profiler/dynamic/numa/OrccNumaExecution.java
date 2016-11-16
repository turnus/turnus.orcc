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
package turnus.orcc.profiler.dynamic.numa;

import static net.sf.orcc.OrccLaunchConstants.FIFO_SIZE;
import static turnus.common.TurnusConstants.DEFAULT_VERSIONER;
import static turnus.common.TurnusOptions.BUFFER_SIZE_DEFAULT;
import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_STIMULUS_FILE;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.MAPPING_FILE;
import static turnus.common.TurnusOptions.VERSIONER;
import static turnus.orcc.profiler.ProfilerOptions.BUFFER_SIZE_MAP;
import static turnus.orcc.profiler.ProfilerOptions.CHECK_ARRAY_INBOUNDS;
import static turnus.orcc.profiler.ProfilerOptions.CODE_GENERATION_DEBUG_DIRECTIVES;
import static turnus.orcc.profiler.ProfilerOptions.EXECUTION_LOOP;
import static turnus.orcc.profiler.ProfilerOptions.INLINE_FUNCTIONS_AND_PROCEDURES;
import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_FOLDER;
import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_HEADERS;
import static turnus.orcc.profiler.ProfilerOptions.NUMA_PROFILING_MODE;
import static turnus.orcc.profiler.ProfilerOptions.NUMA_SAMPLING_RATE;
import static turnus.orcc.profiler.ProfilerOptions.USE_SMART_SCHEDULER;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import net.sf.orcc.df.Network;
import net.sf.orcc.df.transform.Instantiator;
import net.sf.orcc.df.transform.NetworkFlattener;
import turnus.common.TurnusException;
import turnus.common.TurnusExtensions;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.common.io.StreamGobbler;
import turnus.common.util.EcoreUtils;
import turnus.common.util.FileUtils;
import turnus.model.analysis.profiler.MemoryProfilingReport;
import turnus.model.mapping.data.MemoryAccess;
import turnus.model.mapping.io.XmlCommunicationWeightWriter;
import turnus.model.mapping.io.util.BufferIdentifier;
import turnus.model.versioning.Versioner;
import turnus.model.versioning.VersioningFactory;
import turnus.orcc.profiler.dynamic.numa.backend.CNumaProfiler;
import turnus.orcc.profiler.dynamic.numa.backend.templates.NetworkPrinter;
import turnus.orcc.profiler.dynamic.numa.samplesparser.OrccNumaSamplesParser;
import turnus.orcc.profiler.dynamic.numa.weightsgeneration.OrccNumaWeightsGenerator;
import turnus.orcc.profiler.util.OrccBufferSizer;
import turnus.orcc.profiler.util.OrccSourceCodeDebugger;
import turnus.orcc.profiler.util.OutputDirectoryBuilder;
import turnus.orcc.profiler.util.TurnusModelAdapter;
import turnus.orcc.profiler.util.TurnusModelInjector;

/**
 * 
 * @author Manuel Selva
 * @author Simone Casale Brunet
 *
 */
public class OrccNumaExecution {
	
	private boolean stopRequest = false;
	private String numaMode = "read"; 
	private String communicationWeightsLocation = null;

	public IStatus run(Configuration configuration) throws TurnusException {
		Logger.info("===== TURNUS DYNAMIC NUMA ANALYSIS ====");
		Logger.info("Configuring the project");
		String projectName = null;
		IFile xdf = null;
		File out = null;
		File stimulus = null;
		File mapping = null;
		int samplingRate = 0;
		int executionLoop = 0;
		Versioner fv = null;
		// parse the options
		try {
			Logger.info("Parsing the configuration");
			projectName = configuration.getValue(CAL_PROJECT);
			IProject project = EcoreUtils.getProject(projectName);
			String xdfFile = configuration.getValue(CAL_XDF);
			xdf = project.getFile(xdfFile);
			numaMode = configuration.getValue(NUMA_PROFILING_MODE, "read");
			stimulus = configuration.getValue(CAL_STIMULUS_FILE);
			mapping = configuration.getValue(MAPPING_FILE);
			samplingRate = configuration.getValue(NUMA_SAMPLING_RATE, 10000);
			executionLoop = configuration.getValue(EXECUTION_LOOP, 1);
			String versioner = configuration.getValue(VERSIONER, DEFAULT_VERSIONER);
			fv = VersioningFactory.eINSTANCE.getVersioner(versioner);
			Logger.info("* Project: %s", projectName);
			Logger.info("* XDF: %s", xdf.getName());
			Logger.debug("* Versioner: %s (%s)", versioner, fv.getClass().getName());
			if (stimulus != null) {
				Logger.info("* Stimulus: %s", stimulus);
			}
			if (mapping != null) {
				Logger.info("* Mapping: %s", mapping);
			}
			Logger.info("Program will be executed with -l " + executionLoop);

			// create output directory
			out = OutputDirectoryBuilder.create("profiling_dynamic_analysis", configuration);

		} catch (Exception e) {
			throw new TurnusException("Error during the configuration parsing", e);
		}

		// check if a stop request has been send during the configuration
		if (stopRequest) {
			Logger.info("The simulation was aborted by the user during the network configuration");
			return Status.CANCEL_STATUS;
		}

		try {
			Logger.info("Network and profiler building");
			// load the Orcc network
			ResourceSet set = new ResourceSetImpl();
			Network network = EcoreUtils.loadEObject(set, xdf);
			new Instantiator(true).doSwitch(network);
			// flattens the Orcc network
			new NetworkFlattener().doSwitch(network);

			// inject the TURNUS model
			new TurnusModelInjector(fv).inject(network);

			// create the NUMA profiler
			CNumaProfiler profiler = new CNumaProfiler(network);

			// back-end options
			Map<String, Object> options = new HashMap<String, Object>();
			options.put("net.sf.orcc.backend", "NumaProfiler");
			options.put("net.sf.orcc.project", projectName);
			options.put("net.sf.orcc.outputFolder", out.getAbsolutePath());
			options.put("net.sf.orcc.plugins.compileXDF", true);
			options.put("net.sf.orcc.core.xdfFile", xdf);
			
			// copy the user compiling options
			options.put(NUMA_PROFILING_MODE.longName(), configuration.getValue(NUMA_PROFILING_MODE, numaMode));			
			options.put(INLINE_FUNCTIONS_AND_PROCEDURES.longName(),
					configuration.getValue(INLINE_FUNCTIONS_AND_PROCEDURES, false));
			options.put(USE_SMART_SCHEDULER.longName(), configuration.getValue(USE_SMART_SCHEDULER, false));
			options.put(CHECK_ARRAY_INBOUNDS.longName(), configuration.getValue(CHECK_ARRAY_INBOUNDS, false));			
			options.put(LINK_NATIVE_LIBRARY_FOLDER.longName(), 
					(configuration.getValue(LINK_NATIVE_LIBRARY_FOLDER, null) != null) ? configuration.getValue(LINK_NATIVE_LIBRARY_FOLDER, null).getAbsolutePath() : null);
			options.put(LINK_NATIVE_LIBRARY_HEADERS.longName(), configuration.getValue(LINK_NATIVE_LIBRARY_HEADERS, null));			
			
			// set the default buffer size
			int bufferSize = configuration.getValue(BUFFER_SIZE_DEFAULT, 512);
			Logger.debug("Default buffer size %d", bufferSize);
			options.put(FIFO_SIZE, bufferSize);

			// configure punctually the buffer size
			Map<String, String> customSizeMap = configuration.getValue(BUFFER_SIZE_MAP);
			new OrccBufferSizer(customSizeMap).doSwitch(network);

			// check if source code should be printed with debug information
			if (configuration.getValue(CODE_GENERATION_DEBUG_DIRECTIVES, false)) {
				Logger.info("Source code will be genarted with debug directives");
				new OrccSourceCodeDebugger().doSwitch(network);
			}

			// Generate C code
			Logger.info("Generating the C source code");
			profiler.setOptions(options);
			profiler.compile(new NullProgressMonitor());

			// compile the generated code
			Logger.info("Compile the generated C source code");
			if (!profiler.compileGenerateCode(samplingRate)) {
				throw new TurnusException("The design cannot be compiled");
			}

			// execute the generated code
			Logger.info("Executing the compiled program");
			if (!launchNumaProfiler(out.getAbsolutePath(), network, stimulus, mapping, executionLoop)) {
				throw new TurnusException("The design cannot be executed");
			}

			// parse the file
			Logger.info("Parsing the execution profiling data");
			if (!launchNumaParser(out.getAbsolutePath(), network, profiler.getNetworkPrinter(), stimulus)) {
				throw new TurnusException("The design results cannot be parsed");
			}
		} catch (Exception e) {
			throw new TurnusException("Error during the network and profiler building", e);
		}
		Logger.info("===== ANALYSIS DONE ====");
		return Status.OK_STATUS;
	}

	private boolean launchNumaParser(String executablePath, Network network, NetworkPrinter networkPrinter, File stimulus) {
		try {
			String dynamicProfiler = executablePath + File.separator + "src-gen/bin" + File.separator + network.getSimpleName();
			OrccNumaSamplesParser parser = new OrccNumaSamplesParser(executablePath, dynamicProfiler,
					TurnusModelAdapter.getFrom(network), networkPrinter, numaMode);
			MemoryProfilingReport report = parser.parse();
			File root = new File(executablePath);
			File file = FileUtils.createFile(root, "mem-"+numaMode+"-samples", TurnusExtensions.PROFILING_MEMORY);
			EcoreUtils.storeEObject(report, new ResourceSetImpl(), file);
			
			OrccNumaWeightsGenerator weightsGenerator = new OrccNumaWeightsGenerator(report, numaMode);
			Map<BufferIdentifier, List<MemoryAccess>> weights = weightsGenerator.launchGeneration(); 
			File weightsFile = 	FileUtils.createFile(root, numaMode + "-latencies", TurnusExtensions.COMMUNICATION_WEIGHT);
			new XmlCommunicationWeightWriter().write(weights, network.getName(), numaMode, weightsFile);
			communicationWeightsLocation = weightsFile.getAbsolutePath();
			Logger.info("Profiling data for %s stored in \"%s\"", numaMode, weightsFile);
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (TurnusException e) {
			e.printStackTrace();
		}
		return false;
	}

	private boolean launchNumaProfiler(String executablePath, Network network, File stimulus, File mapping, int executionLoop) {
		try {
			String dynamicProfiler = executablePath + File.separator + "src-gen" + File.separator + "bin" + File.separator + network.getSimpleName();
			String stimulusFile = "";
			String mappingFile = "";
			ProcessBuilder builder;
			if (stimulus != null && mapping != null) {
				stimulusFile = stimulus.getAbsolutePath();
				mappingFile = mapping.getAbsolutePath();
				builder = new ProcessBuilder(dynamicProfiler, "-i", stimulusFile, "-m", mappingFile, "-l", executionLoop + "");
			} else if (stimulus != null) {
				stimulusFile = stimulus.getAbsolutePath();
				builder = new ProcessBuilder(dynamicProfiler, "-i", stimulusFile, "-l", executionLoop + "");
			} else {
				builder = new ProcessBuilder(dynamicProfiler);
			}
			builder.redirectErrorStream(true);
			Process process = builder.start();
			StreamGobbler gobbler = new StreamGobbler("execution output", process.getInputStream());

			process.waitFor();
			gobbler.join();

			if (process.exitValue() == 0) {
				Logger.info("NUMA Profiler exited correctly");
				return true;
			} else {
				Logger.error("NUMA Profiler did not exited correctly");
				return false;
			}
		} catch (Exception ep) {
			Logger.debug(ep.getMessage());
		}
		return false;

	}

	public void stop() {
		Logger.info("Stop request sent to the server");
		stopRequest = true;
	}
	
	public String getCommunicationWeightLocation() {
		return communicationWeightsLocation;
	}

}
