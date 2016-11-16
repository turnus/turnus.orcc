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
package turnus.orcc.profiler.dynamic.execution;

import static turnus.common.TurnusConstants.DEFAULT_VERSIONER;
import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_STIMULUS_FILE;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.VERSIONER;
import static turnus.orcc.profiler.ProfilerOptions.DELETE_PROFILING_TRACE_DIRECTORY;
import static turnus.orcc.profiler.ProfilerOptions.DELETE_SRCGEN_DIRECTORY;

import java.io.File;
import java.util.HashMap;
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
import turnus.analysis.profiler.dynamic.DynamicProfiler;
import turnus.analysis.profiler.dynamic.util.ProfiledExecutionDataReader;
import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.common.util.EcoreUtils;
import turnus.common.util.FileUtils;
import turnus.model.versioning.Versioner;
import turnus.model.versioning.VersioningFactory;
import turnus.orcc.profiler.code.transfo.SharedVariablesLinker;
import turnus.orcc.profiler.dynamic.execution.backend.CppDynamicProfiler;
import turnus.orcc.profiler.util.OutputDirectoryBuilder;
import turnus.orcc.profiler.util.TurnusModelAdapter;
import turnus.orcc.profiler.util.TurnusModelInjector;

/**
 * 
 * @author Endri Bezati
 * @author Simone Casale Brunet
 *
 */
public class OrccDynamicExecution {

	private Network network;
	private DynamicProfiler profiler;
	private boolean stopRequest = false;

	public void stop() {
		Logger.info("Stop request sent to the server");
		stopRequest = true;
	}

	public IStatus run(Configuration configuration) throws TurnusException {
		Logger.info("===== TURNUS DYNAMIC EXECUTION ANALYSIS ====");
		Logger.info("Configuring the project");
		String projectName = null;
		IFile xdf = null;
		File out = null;
		Versioner fv = null;
		File stimulus = null;
		// parse the options
		try {
			Logger.info("Parsing the configuration");
			projectName = configuration.getValue(CAL_PROJECT);
			IProject project = EcoreUtils.getProject(projectName);
			String xdfFile = configuration.getValue(CAL_XDF);
			xdf = project.getFile(xdfFile);
			stimulus = configuration.getValue(CAL_STIMULUS_FILE);
			String versioner = configuration.getValue(VERSIONER, DEFAULT_VERSIONER);
			fv = VersioningFactory.eINSTANCE.getVersioner(versioner);
			Logger.info("* Project: %s", projectName);
			Logger.info("* XDF: %s", xdf.getName());
			Logger.debug("* Versioner: %s (%s)", versioner, fv.getClass().getName());
			if (stimulus != null) {
				Logger.info("* Stimulus: %s", stimulus);
			}

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
			network = EcoreUtils.loadEObject(set, xdf);
			new Instantiator(true).doSwitch(network);
			// flattens the Orcc network
			new NetworkFlattener().doSwitch(network);

			// apply transformations
			applyTransformations(configuration);

			// inject the TURNUS model
			new TurnusModelInjector(fv).inject(network);

			// create the profiler
			profiler = new DynamicProfiler(TurnusModelAdapter.getFrom(network));
			profiler.setConfiguration(configuration);

			// generate the code
			CppDynamicProfiler cppDynamicProfiler = new CppDynamicProfiler(network);

			// back-end options
			Map<String, Object> options = new HashMap<String, Object>();
			options.put("net.sf.orcc.backend", "CppProfiler");
			options.put("net.sf.orcc.project", projectName);
			options.put("net.sf.orcc.outputFolder", out.getAbsolutePath());
			options.put("net.sf.orcc.plugins.compileXDF", true);
			options.put("net.sf.orcc.core.xdfFile", xdf);

			cppDynamicProfiler.setOptions(options);
			cppDynamicProfiler.compile(new NullProgressMonitor());

			// compile the generated code
			Logger.info("Generating the C++ source code");
			if (!cppDynamicProfiler.compileGenerateCode()) {
				throw new TurnusException("The design cannot be compiled");
			}

			// execute the generated code
			Logger.info("Executing the compiled program");
			if (!cppDynamicProfiler.launchExecutable(stimulus)) {
				Logger.error("The execution trace graph will not be created since the execution ended incorrectly");
			} else {
				// parse the file
				Logger.info("Parsing the execution profiling data");
				File jsonFile = new File(cppDynamicProfiler.getFiringTrace());
				ProfiledExecutionDataReader reader = new ProfiledExecutionDataReader(profiler, jsonFile);
				reader.start();
				reader.join();
			}

			// check if the output files should be deleted
			if (configuration.getValue(DELETE_SRCGEN_DIRECTORY, false)) {
				Logger.info("Deleting the source generated directory");
				try {
					FileUtils.deleteDirectory(new File(cppDynamicProfiler.getSourceGenPath()));
				} catch (Exception e) {
					Logger.warning("The source generated directory cannot be deleted");
				}
			}

			// check if the output files should be deleted
			if (configuration.getValue(DELETE_PROFILING_TRACE_DIRECTORY, false)) {
				Logger.info("Deleting the execution data directory");
				try {
					FileUtils.deleteDirectory(new File(cppDynamicProfiler.getExecutionDataPath()));
				} catch (Exception e) {
					Logger.warning("The execution data directory cannot be deleted");
				}
			}

		} catch (TurnusException e) {
			throw e;
		} catch (Exception e) {
			throw new TurnusException("Error during the network and profiler building", e);
		}

		Logger.info("===== ANALYSIS DONE ====");
		return Status.OK_STATUS;

	}

	private void applyTransformations(Configuration configuration) throws TurnusException {
		Logger.debug("Shared variable transformation [ON]");
		new SharedVariablesLinker().doSwitch(network);
	}

}
