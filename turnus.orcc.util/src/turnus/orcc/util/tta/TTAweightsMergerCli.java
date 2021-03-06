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
package turnus.orcc.util.tta;

import static turnus.common.TurnusOptions.TRACE_FILE;
import static turnus.common.TurnusOptions.ACTION_WEIGHTS;

import java.io.File;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.configuration.Configuration.CliParser;
import turnus.common.configuration.Option;
import turnus.common.configuration.Option.Description;
import turnus.common.io.Logger;
import turnus.model.ModelsRegister;
import turnus.model.mapping.NetworkWeight;
import turnus.model.mapping.io.XmlNetworkWeightReader;
import turnus.model.mapping.io.XmlNetworkWeightWriter;
import turnus.model.trace.TraceProject;
import turnus.model.trace.impl.splitted.SplittedTraceLoader;

/**
 * 
 * @author Malgorzata Michalska
 *
 */
public class TTAweightsMergerCli implements IApplication {
	
	@Description("The overheads file.")
	public static final Option<File> OVERHEADS_FILE = Option.create().//
			setName("overhead").//
			setDescription("The file with profiled overheads.").//
			setLongName("overhead").//
			setType(File.class).build();
	
	public static void main(String[] args) throws TurnusException {
		ModelsRegister.init();

		TTAweightsMergerCli cliApp = null;

		try {
			cliApp = new TTAweightsMergerCli();
			cliApp.parse(args);
		} catch (TurnusException e) {
			return;
		}

		try {
			cliApp.run();
		} catch (Exception e) {
			Logger.error("Application error: %s", e.getMessage());
		}
	}

	private Configuration configuration;
	private TTAweightsMerger merger;

	private IProgressMonitor monitor = new NullProgressMonitor();

	private void parse(String[] args) throws TurnusException {
		CliParser cliParser = new CliParser().setOption(TRACE_FILE, true)//
				.setOption(ACTION_WEIGHTS, true)//
				.setOption(OVERHEADS_FILE, true);
		
		configuration = cliParser.parse(args);
	}

	private void run() throws TurnusException {
		monitor.beginTask("TTA weights merging", IProgressMonitor.UNKNOWN);

		TraceProject tProject = null;
		NetworkWeight weights = null;
		File output = null;
		
		merger = new TTAweightsMerger();

		{ // STEP 1 : parse the configuration
			monitor.subTask("Parsing the configuration");
			try {
				File traceFile = configuration.getValue(TRACE_FILE);
				tProject = TraceProject.open(traceFile);
				tProject.loadTrace(new SplittedTraceLoader(), configuration);
			} catch (Exception e) {
				throw new TurnusException("Trace file is not valid", e);
			}

			try {
				File weightsFile = configuration.getValue(ACTION_WEIGHTS);
				output = new File(weightsFile.getParent(), "Merged_TTA_Weights.exdf");
				weights = new XmlNetworkWeightReader().load(weightsFile);
			} catch (Exception e) {
				throw new TurnusException("Weights file is not valid", e);
			}
			
			try {
				File overheadsFile = configuration.getValue(OVERHEADS_FILE);
				merger.readSchedCost(overheadsFile);
				merger.updateNetworkWeight(weights, tProject);
			} catch (Exception e) {
				throw new TurnusException("Overheads file is not valid or merging couldn't be completed", e);
			}
			
		}

		{ // STEP 2 : Process
			monitor.subTask("Running the simulation");
			try {
				new XmlNetworkWeightWriter().write(weights, output);
				Logger.info("Merged TTA weights stored in file " + output.getAbsolutePath());
			} catch (Exception e) {
				throw new TurnusException("The merged results cannot be stored.", e);
			}
		}

		monitor.done();
	}

	@Override
	public Object start(IApplicationContext context) throws Exception {
		try {
			parse((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
		} catch (TurnusException e) {
			Logger.error("Command line argument parsing error: see the help");
			return IApplication.EXIT_RELAUNCH;
		}

		try {
			run();
		} catch (Exception e) {
			Logger.error("Application error: %s", e.getMessage());
			return IApplication.EXIT_RELAUNCH;
		}

		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
	}
}
