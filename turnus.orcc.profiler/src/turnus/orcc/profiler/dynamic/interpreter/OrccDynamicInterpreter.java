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
package turnus.orcc.profiler.dynamic.interpreter;

import static turnus.common.TurnusConstants.DEFAULT_VERSIONER;
import static turnus.common.TurnusOptions.BUFFER_SIZE_DEFAULT;
import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_STIMULUS_FILE;
import static turnus.common.TurnusOptions.SIMULATION_OUTPUT_FILE;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.SHARED_VARIABLES;
import static turnus.common.TurnusOptions.STACK_PROTECTION;
import static turnus.common.TurnusOptions.VERSIONER;
import static turnus.orcc.profiler.ProfilerOptions.CONSTANT_FOLDING;
import static turnus.orcc.profiler.ProfilerOptions.CONSTANT_PROPAGATION;
import static turnus.orcc.profiler.ProfilerOptions.DEAD_ACTION_ELIMINATION;
import static turnus.orcc.profiler.ProfilerOptions.DEAD_CODE_ELIMINATION;
import static turnus.orcc.profiler.ProfilerOptions.EXPRESSION_EVALUATION;
import static turnus.orcc.profiler.ProfilerOptions.BUFFER_SIZE_MAP;
import static turnus.orcc.profiler.ProfilerOptions.SCHEDULER;
import static turnus.orcc.profiler.ProfilerOptions.TYPE_RESIZE_TO32BITS;
import static turnus.orcc.profiler.ProfilerOptions.TYPE_RESIZE_TONBITS;
import static turnus.orcc.profiler.ProfilerOptions.VARIABLE_INITIALIZER;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Connection;
import net.sf.orcc.df.Network;
import net.sf.orcc.df.Port;
import net.sf.orcc.df.transform.Instantiator;
import net.sf.orcc.df.transform.NetworkFlattener;
import net.sf.orcc.df.transform.TypeResizer;
import net.sf.orcc.graph.Vertex;
import net.sf.orcc.simulators.SimulatorDescriptor;
import net.sf.orcc.simulators.runtime.impl.GenericSource;
import net.sf.orcc.simulators.runtime.impl.GenericWriter;
import net.sf.orcc.simulators.runtime.std.video.impl.Display;
import net.sf.orcc.util.Attribute;
import turnus.analysis.profiler.dynamic.DynamicProfiler;
import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.common.util.EcoreUtils;
import turnus.model.versioning.Versioner;
import turnus.model.versioning.VersioningFactory;
import turnus.orcc.profiler.code.transfo.SharedVariablesLinker;
import turnus.orcc.profiler.code.transfo.XronosConstantFolding;
import turnus.orcc.profiler.code.transfo.XronosConstantPropagation;
import turnus.orcc.profiler.code.transfo.XronosDeadActionEliminaton;
import turnus.orcc.profiler.code.transfo.XronosDeadCodeElimination;
import turnus.orcc.profiler.code.transfo.XronosExprEvaluator;
import turnus.orcc.profiler.code.transfo.XronosVarInitializer;
import turnus.orcc.profiler.dynamic.interpreter.network.ProfiledActor;
import turnus.orcc.profiler.dynamic.interpreter.network.ProfiledBuffer;
import turnus.orcc.profiler.dynamic.interpreter.scheduler.Scheduler;
import turnus.orcc.profiler.dynamic.interpreter.scheduler.SchedulerFactory;
import turnus.orcc.profiler.util.OrccBufferSizer;
import turnus.orcc.profiler.util.OutputDirectoryBuilder;
import turnus.orcc.profiler.util.TurnusModelAdapter;
import turnus.orcc.profiler.util.TurnusModelInjector;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class OrccDynamicInterpreter {

	private boolean stopRequest = false;
	private Network network;
	private DynamicProfiler profiler;
	private Scheduler scheduler;
	private Collection<ProfiledActor> interpreters;
	private Collection<ProfiledBuffer> buffers;

	public IStatus run(Configuration configuration) throws TurnusException {
		Logger.info("===== TURNUS DYNAMIC INTERPRETATION ANALYSIS ====");
		Logger.info("Configuring the project");
		IFile xdf = null;
		Versioner fv = null;
		File stimulus = null;
		File output = null;
		// parse the options
		try {
			Logger.info("Parsing the configuration");
			String projectName = configuration.getValue(CAL_PROJECT);
			IProject project = EcoreUtils.getProject(projectName);
			String xdfFile = configuration.getValue(CAL_XDF);
			xdf = project.getFile(xdfFile);
			stimulus = configuration.getValue(CAL_STIMULUS_FILE);
			output = configuration.getValue(SIMULATION_OUTPUT_FILE);
			String versioner = configuration.getValue(VERSIONER, DEFAULT_VERSIONER);
			fv = VersioningFactory.eINSTANCE.getVersioner(versioner);
			String schedulerName = configuration.getValue(SCHEDULER);
			scheduler = SchedulerFactory.INSTANCE.createScheduler(schedulerName);
			Logger.info("* Project: %s", projectName);
			Logger.info("* XDF: %s", xdf.getName());
			Logger.info("* Scheduler: %s", scheduler.getClass().getName());
			Logger.debug("* Versioner: %s (%s)", versioner, fv.getClass().getName());
			if (stimulus != null) {
				Logger.debug("* Stimulus: %s", stimulus);
			}

			// create output directory
			OutputDirectoryBuilder.create("profiling_dynamic_analysis", configuration);

		} catch (Exception e) {
			throw new TurnusException("Error during the configuration parsing", e);
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

			// create interpreters and buffers
			createInterpreters(configuration);
			createBuffers(configuration);

			// set the scheduler
			scheduler.configure(interpreters, configuration);
		} catch (Exception e) {
			throw new TurnusException("Error during the network and profiler building", e);
		}

		// check if a stop request has been send during the configuration
		if (stopRequest) {
			Logger.warning("The simulation was aborted by the user during the network configuration");
			return Status.CANCEL_STATUS;
		}

		try {
			Logger.info("Initialize the network");
			// kill all the survived descriptors
			killOrccDescriptors();

			// initialize orcc source actors if required
			if (stimulus != null) {
				GenericSource.setInputStimulus(stimulus.getAbsolutePath());
				GenericSource.setNbLoops(1);
			}
			
			// initialize orcc writer actors if required
			if (output != null) {
				File outFile = new File(output + "/orcc-simulation-out");
				GenericWriter.setOutputFile(outFile.getAbsolutePath());
			}

			// initialize the interpreters
			for (ProfiledActor interpreter : interpreters) {
				interpreter.initialize();
			}

		} catch (Exception e) {
			throw new TurnusException("Error during the network and profiler initialization", e);
		}

		try {
			Logger.info("Start the network interpretation");
			scheduler.run();
		} catch (Exception e) {
			throw new TurnusException("Error during the network interpretation", e);
		}
		try {
			Logger.info("Finalizing the simulation");
			Logger.warning("!! Please wait !!");
			profiler.stop();
			killOrccDescriptors();
		} catch (Exception e) {
			throw new TurnusException("Error during the network interpretation", e);
		}

		Logger.info("===== ANALYSIS DONE ====");
		return Status.OK_STATUS;
	}

	private void applyTransformations(Configuration configuration) throws TurnusException {
		try {
			// Type resizing
			boolean toNBit = configuration.getValue(TYPE_RESIZE_TONBITS, false);
			boolean to32Bit = configuration.getValue(TYPE_RESIZE_TO32BITS, false);
			boolean port = configuration.getValue(TYPE_RESIZE_TO32BITS, false);
			Logger.debug("Type resize {toN: %s, to32: %s, port: %s}", toNBit, to32Bit, port);
			new TypeResizer(toNBit, to32Bit, port, false).doSwitch(network);

			// Xronos transformations
			if (configuration.getValue(DEAD_ACTION_ELIMINATION, false)) {
				Logger.debug("Dead action elimination transformation [ON]");
				new XronosDeadActionEliminaton(true).doSwitch(network);
			}
			if (configuration.getValue(CONSTANT_FOLDING, false)) {
				Logger.debug("Constant folding transformation [ON]");
				new XronosConstantFolding().doSwitch(network);
			}
			if (configuration.getValue(CONSTANT_PROPAGATION, false)) {
				Logger.debug("Constant propagation transformation [ON]");
				new XronosConstantPropagation().doSwitch(network);
			}
			if (configuration.getValue(DEAD_CODE_ELIMINATION, false)) {
				Logger.debug("Dead code elimination transformation [ON]");
				new XronosDeadCodeElimination().doSwitch(network);
			}
			if (configuration.getValue(EXPRESSION_EVALUATION, false)) {
				Logger.debug("Expression evaluation transformation [ON]");
				new XronosExprEvaluator().doSwitch(network);
			}
			if (configuration.getValue(VARIABLE_INITIALIZER, false)) {
				Logger.debug("Variable initialization transformation [ON]");
				new XronosVarInitializer().doSwitch(network);
			}

			// Shared variables transformation
			if (configuration.getValue(SHARED_VARIABLES, false)) {
				Logger.debug("Shared variable transformation [ON]");
				new SharedVariablesLinker().doSwitch(network);
			}

		} catch (Exception e) {
			throw new TurnusException("Error during the network transformation execution", e);
		}
	}

	private void createBuffers(Configuration configuration) throws TurnusException {
		buffers = new HashSet<>();

		int defaultSize = configuration.getValue(BUFFER_SIZE_DEFAULT, 512);
		Map<String, String> customSizeMap = configuration.getValue(BUFFER_SIZE_MAP);
		Logger.debug("Default buffer size: " + defaultSize);

		// Loop over the connections and ask for the source and target actors
		// connection through specified I/O ports.
		for (Connection connection : network.getConnections()) {
			Vertex srcVertex = connection.getSource();
			Vertex tgtVertex = connection.getTarget();

			if (srcVertex instanceof Actor && tgtVertex instanceof Actor && connection.getSourcePort() != null
					&& connection.getTargetPort() != null) {

				int size = defaultSize;
				String key = OrccBufferSizer.getKey(connection);
				if (customSizeMap != null && customSizeMap.containsKey(key)) {
					size = Integer.parseInt(customSizeMap.get(key));
					Logger.debug("Custon buffer size: %s > %d", key, size);
				}

				connectActors(connection, size);
			}
		}

		// print a warning for the unconnected ports
		for (Actor actor : network.getAllActors()) {
			// check input ports
			for (Port port : actor.getInputs()) {
				if (port.getAttribute("fifo") == null) {
					Logger.warning("Unconnected input port [%s] on Actor %s", port.getName(), actor.getSimpleName());
				}
			}

			// check output ports
			for (Port port : actor.getOutputs()) {
				if (port.getAttribute("fifo") == null) {
					Logger.warning("Unconnected output Port [%s] on Actor %s", port.getName(), actor.getSimpleName());
				}
			}
		}

	}

	@SuppressWarnings("unchecked")
	private void connectActors(Connection connection, int size) {
		Port srcPort = connection.getSourcePort();
		Port tgtPort = connection.getTargetPort();
		// Type type = srcPort.getType();
		ProfiledBuffer buffer = new ProfiledBuffer(connection, size, profiler);
		buffers.add(buffer);

		// connect the output
		Attribute attribute = srcPort.getAttribute("fifo");
		List<ProfiledBuffer> outBuffers = attribute != null ? (List<ProfiledBuffer>) attribute.getObjectValue() : null;
		if (outBuffers == null) {
			outBuffers = new ArrayList<ProfiledBuffer>();
			srcPort.setAttribute("fifo", outBuffers);
		}
		outBuffers.add(buffer);

		// connect the input
		tgtPort.setAttribute("fifo", buffer);

	}

	private void killOrccDescriptors() {
		Runnable killer = new Runnable() {
			public void run() {
				SimulatorDescriptor.killDescriptors();
				Display.clearAll();
			}
		};
		SwingUtilities.invokeLater(killer);
	}

	private void createInterpreters(Configuration configuration) throws TurnusException {
		interpreters = new HashSet<>();

		// check if the stack protection should be activated or not
		boolean stackProtection = configuration.getValue(STACK_PROTECTION, false);
		Logger.debug("Stack protection: %s", stackProtection);

		// for each actor associate an interpreter
		for (Vertex vertex : network.getChildren()) {
			// create a new interpreter to associate to this actor
			Actor actor = vertex.getAdapter(Actor.class);
			interpreters.add(new ProfiledActor(actor, profiler, stackProtection));
		}

	}

	/**
	 * Stop by the user interface the analysis
	 */
	public void stop() {
		Logger.warning("Stop request sent to the scheduler");
		stopRequest = true;

		if (scheduler != null) {
			scheduler.stop();
		}
	}
}
