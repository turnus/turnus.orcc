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
package turnus.orcc.util.numa;

import static turnus.common.TurnusOptions.TRACE_FILE;

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
import turnus.model.dataflow.Network;
import turnus.model.mapping.CommunicationWeight;
import turnus.model.mapping.io.XmlCommunicationWeightReader;
import turnus.model.mapping.io.XmlCommunicationWeightWriter;
import turnus.model.trace.TraceProject;

/**
 * 
 * @author Malgorzata Michalska
 *
 */
public class NUMAweightsMergerCli implements IApplication {
	
	@Description("The read weights file.")
	public static final Option<File> READ_WEIGHTS_FILE = Option.create().//
			setName("readWeight").//
			setDescription("The file with communication weights for read.").//
			setLongName("read.weights").//
			setType(File.class).build();
	
	@Description("The write weights file.")
	public static final Option<File> WRITE_WEIGHTS_FILE = Option.create().//
			setName("writeWeight").//
			setDescription("The file with communication weights for write.").//
			setLongName("write.weights").//
			setType(File.class).build();
	
	public static void main(String[] args) throws TurnusException {
		ModelsRegister.init();

		NUMAweightsMergerCli cliApp = null;

		try {
			cliApp = new NUMAweightsMergerCli();
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
	private IProgressMonitor monitor = new NullProgressMonitor();

	private void parse(String[] args) throws TurnusException {
		CliParser cliParser = new CliParser().setOption(TRACE_FILE, true)//
				.setOption(READ_WEIGHTS_FILE, true)//
				.setOption(WRITE_WEIGHTS_FILE, true);
		
		configuration = cliParser.parse(args);
	}

	private void run() throws TurnusException {
		monitor.beginTask("NUMA weights merging", IProgressMonitor.UNKNOWN);

		File output = null;
		Network network = null;
		CommunicationWeight readWeights = null;
		CommunicationWeight writeWeights = null;
		
		{ // STEP 1 : parse the configuration
			monitor.subTask("Parsing the configuration");
			try {
				File traceFile = configuration.getValue(TRACE_FILE);
				network = TraceProject.open(traceFile).getNetwork();
			} catch (Exception e) {
				throw new TurnusException("Trace file is not valid", e);
			}

			try {
				File readWeightsFile = configuration.getValue(READ_WEIGHTS_FILE);
				output = new File(readWeightsFile.getParent(), "communication-weights.exdf");
				readWeights = new XmlCommunicationWeightReader(network).load(readWeightsFile);
			} catch (Exception e) {
				throw new TurnusException("Communication (read) weights file is not valid", e);
			}
			
			try {
				File writeWeightsFile = configuration.getValue(WRITE_WEIGHTS_FILE);
				writeWeights = new XmlCommunicationWeightReader(network).load(writeWeightsFile);
			} catch (Exception e) {
				throw new TurnusException("Communication (write) weights file is not valid", e);
			}
			
		}

		{ // STEP 2 : Process
			monitor.subTask("Writing the file");
			try {
				readWeights.setWriteWeights(writeWeights.getWriteWeights());
				new XmlCommunicationWeightWriter().write(readWeights, network, output);
			} catch (Exception e) {
				throw new TurnusException("The merging couldn't be completed.", e);
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
