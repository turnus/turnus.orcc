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
package turnus.orcc.profiler.dynamic.numa.backend;

import static net.sf.orcc.backends.BackendsConstants.ADDITIONAL_TRANSFOS;
import static net.sf.orcc.backends.BackendsConstants.BXDF_FILE;
import static net.sf.orcc.backends.BackendsConstants.IMPORT_BXDF;
import static turnus.orcc.profiler.ProfilerOptions.NUMA_PROFILING_MODE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import com.google.common.base.Joiner;

import net.sf.orcc.backends.c.CBackend;
import net.sf.orcc.backends.c.transform.CBroadcastAdder;
import net.sf.orcc.backends.transform.CastAdder;
import net.sf.orcc.backends.transform.DeadVariableRemoval;
import net.sf.orcc.backends.transform.DisconnectedOutputPortRemoval;
import net.sf.orcc.backends.transform.DivisionSubstitution;
import net.sf.orcc.backends.transform.EmptyBlockRemover;
import net.sf.orcc.backends.transform.Inliner;
import net.sf.orcc.backends.transform.InlinerByAnnotation;
import net.sf.orcc.backends.transform.InstPhiTransformation;
import net.sf.orcc.backends.transform.InstTernaryAdder;
import net.sf.orcc.backends.transform.ListFlattener;
import net.sf.orcc.backends.transform.LoopUnrolling;
import net.sf.orcc.backends.transform.Multi2MonoToken;
import net.sf.orcc.backends.transform.ParameterImporter;
import net.sf.orcc.backends.transform.StoreOnceTransformation;
import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Network;
import net.sf.orcc.df.transform.ArgumentEvaluator;
import net.sf.orcc.df.transform.BroadcastAdder;
import net.sf.orcc.df.transform.BroadcastRemover;
import net.sf.orcc.df.transform.FifoSizePropagator;
import net.sf.orcc.df.transform.Instantiator;
import net.sf.orcc.df.transform.NetworkFlattener;
import net.sf.orcc.df.transform.SharedVarsDetection;
import net.sf.orcc.df.transform.TypeResizer;
import net.sf.orcc.df.transform.UnitImporter;
import net.sf.orcc.df.util.DfVisitor;
import net.sf.orcc.graph.Vertex;
import net.sf.orcc.ir.CfgNode;
import net.sf.orcc.ir.Expression;
import net.sf.orcc.ir.transform.BlockCombine;
import net.sf.orcc.ir.transform.ControlFlowAnalyzer;
import net.sf.orcc.ir.transform.DeadCodeElimination;
import net.sf.orcc.ir.transform.DeadGlobalElimination;
import net.sf.orcc.ir.transform.PhiRemoval;
import net.sf.orcc.ir.transform.RenameTransformation;
import net.sf.orcc.ir.transform.SSATransformation;
import net.sf.orcc.ir.transform.SSAVariableRenamer;
import net.sf.orcc.ir.transform.TacTransformation;
import net.sf.orcc.tools.classifier.Classifier;
import net.sf.orcc.tools.mapping.XmlBufferSizeConfiguration;
import net.sf.orcc.tools.merger.action.ActionMerger;
import net.sf.orcc.tools.merger.actor.ActorMerger;
import net.sf.orcc.util.OrccLogger;
import net.sf.orcc.util.Result;
import net.sf.orcc.util.Void;
import turnus.common.TurnusConstants;
import turnus.common.TurnusException;
import turnus.common.TurnusRuntimeException;
import turnus.common.io.Logger;
import turnus.common.util.FileUtils;
import turnus.orcc.profiler.dynamic.numa.backend.templates.CMakePrinter;
import turnus.orcc.profiler.dynamic.numa.backend.templates.InstancePrinter;
import turnus.orcc.profiler.dynamic.numa.backend.templates.NetworkPrinter;
import turnus.orcc.profiler.util.RuntimeUtils;

/**
 * 
 * @author Manuel Selva
 * @author Simone Casale Brunet
 *
 */
public class CNumaProfiler extends CBackend{
	
	private Network network;	
	private NetworkPrinter networkPrinter;
	
	private String executionDataPath;
	private String srcGenPath;

	private String buildPath;
	private String libsPath;
	private String srcPath;

	private File libsDir;	
	private FileWriter writer;
	
	public CNumaProfiler(Network network){
		this.network = network;
		this.networkPrinter = null;
	}
	
	public NetworkPrinter getNetworkPrinter() {
		return this.networkPrinter;
	}

	@Override
	protected void doInitializeOptions() {
		// -- Create the 'execution_data' folder
		executionDataPath = outputPath + File.separator + TurnusConstants.EXECUTION_PROFILING_DATA;
		createDirectory(executionDataPath);
		// -- Create the 'src-gen' folder
		srcGenPath = outputPath + File.separator + "src-gen";
		createDirectory(outputPath + File.separator + "src-gen");
		// -- Create the 'bin' folder
		createDirectory(srcGenPath + File.separator + "bin");		
		// -- Create the 'builds' folder
		buildPath = srcGenPath + File.separator + "build";
		createDirectory(buildPath);
		// -- Create the 'libs' folder
		libsPath = srcGenPath + File.separator + "libs";
		libsDir = createDirectory(libsPath);
		// -- Create the 'src' folder
		srcPath = srcGenPath + File.separator + "src";
		createDirectory(srcPath);

		getOptions().put("execution_data_path", executionDataPath);

		// -----------------------------------------------------
		// Transformations that will be applied on the Network
		// -----------------------------------------------------
		if (mergeActors) {
			networkTransfos.add(new FifoSizePropagator(fifoSize));
			networkTransfos.add(new BroadcastAdder());
		}
		networkTransfos.add(new Instantiator(true));
		networkTransfos.add(new NetworkFlattener());
		networkTransfos.add(new UnitImporter());
		networkTransfos.add(new DisconnectedOutputPortRemoval());
		if (classify) {
			networkTransfos.add(new Classifier());
		}
		if (mergeActors) {
			networkTransfos.add(new ActorMerger());
		} else {
			networkTransfos.add(new CBroadcastAdder());
		}
		if (mergeActors) {
			networkTransfos.add(new BroadcastRemover());
		}
		networkTransfos.add(new ArgumentEvaluator());
		networkTransfos.add(new TypeResizer(true, false, true, false));
		networkTransfos.add(new RenameTransformation(getRenameMap()));		
		networkTransfos.add(new SharedVarsDetection());

		// -------------------------------------------------------------------
		// Transformations that will be applied on children (instances/actors)
		// -------------------------------------------------------------------
		if (mergeActions) {
			childrenTransfos.add(new ActionMerger());
		}
		if (convertMulti2Mono) {
			childrenTransfos.add(new Multi2MonoToken());
		}
		childrenTransfos.add(new DfVisitor<Void>(new InlinerByAnnotation()));
		childrenTransfos.add(new DfVisitor<Void>(new LoopUnrolling()));

		// If "-t" option is passed to command line, apply additional
		// transformations
		if (getOption(ADDITIONAL_TRANSFOS, false)) {
			childrenTransfos.add(new StoreOnceTransformation());
			childrenTransfos.add(new DfVisitor<Void>(new SSATransformation()));
			childrenTransfos.add(new DfVisitor<Void>(new PhiRemoval()));
			childrenTransfos.add(new Multi2MonoToken());
			childrenTransfos.add(new DivisionSubstitution());
			childrenTransfos.add(new ParameterImporter());
			childrenTransfos.add(new DfVisitor<Void>(new Inliner(true, true)));

			// transformations.add(new UnaryListRemoval());
			// transformations.add(new GlobalArrayInitializer(true));

			childrenTransfos.add(new DfVisitor<Void>(new InstTernaryAdder()));
			childrenTransfos.add(new DeadGlobalElimination());

			childrenTransfos.add(new DfVisitor<Void>(new DeadVariableRemoval()));
			childrenTransfos.add(new DfVisitor<Void>(new DeadCodeElimination()));
			childrenTransfos.add(new DfVisitor<Void>(new DeadVariableRemoval()));
			childrenTransfos.add(new DfVisitor<Void>(new ListFlattener()));
			childrenTransfos.add(new DfVisitor<Expression>(new TacTransformation()));
			childrenTransfos.add(new DfVisitor<CfgNode>(new ControlFlowAnalyzer()));
			childrenTransfos.add(new DfVisitor<Void>(new InstPhiTransformation()));
			childrenTransfos.add(new DfVisitor<Void>(new EmptyBlockRemover()));
			childrenTransfos.add(new DfVisitor<Void>(new BlockCombine()));

			childrenTransfos.add(new DfVisitor<Expression>(new CastAdder(true, true)));
			childrenTransfos.add(new DfVisitor<Void>(new SSAVariableRenamer()));
		}
	}

	
	@Override
	protected Result doLibrariesExtraction() {
		RuntimeUtils.copyRuntimeFiles("C", libsDir);
		
		return Result.newInstance();
	}
	
	@Override
	protected void beforeGeneration(Network network) {
		network.computeTemplateMaps();

		// if required, load the buffer size from the mapping file
		if (getOption(IMPORT_BXDF, false)) {
			File f = new File(getOption(BXDF_FILE, ""));
			new XmlBufferSizeConfiguration(true, true).load(f, network);
		}

		if (network.getVertex(network.getSimpleName()) != null) {
			final StringBuilder warnMsg = new StringBuilder();
			warnMsg.append('"').append(network.getSimpleName()).append('"');
			warnMsg.append(" is the name of both the network you want to generate");
			warnMsg.append(" and a vertex in this network.").append('\n');
			warnMsg.append("The 2 entities will be generated");
			warnMsg.append(" in the same file. Please rename one of these elements to prevent");
			warnMsg.append(" unwanted overwriting.");
			OrccLogger.warnln(warnMsg.toString());
		}
	}
	
	@Override
	protected Result doGenerateNetwork(Network network) {
		this.networkPrinter = new NetworkPrinter(network, getOptions());
		
		int NumaProfilerExitStatus = this.networkPrinter.getNumaProfilerExitStatus();
		if(NumaProfilerExitStatus > 1) {
			Logger.error("NUMA profiler cannot continue as application contains multiple NUMA exit actions [tagged with @numaProfilerExit(condition=\"...\")]");
			throw new TurnusRuntimeException("Multiple numa exit actions.");
		}
		else if(NumaProfilerExitStatus == 0) {	
			Logger.error("NUMA profiler cannot continue as application doesn't contain a NUMA exit action [tagged with @numaProfilerExit(condition=\"...\")]");
			throw new TurnusRuntimeException("No numa exit actions.");
		}
			
		this.networkPrinter.printNetwork(srcPath);
		return Result.newInstance();
	}
	
	@Override
	protected Result doAdditionalGeneration(Network network) {
		// -- Print CMake files
		CMakePrinter cmakePrinter = new CMakePrinter(network, getOptions());
		cmakePrinter.printRootCMake(srcGenPath);
		cmakePrinter.printSrcCMake(srcPath);

		return Result.newInstance();		
	}

	protected Result doGenerateActor(Actor actor) {
		// -- Print Actor header
		InstancePrinter instancePrinter = new InstancePrinter(actor, getOptions());
		instancePrinter.printInstance(srcPath);

		return Result.newInstance();
	}

	protected void stopIfRequested() {
		if (monitor != null) {
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}
		}
	}	

	@Override
	public void compile(IProgressMonitor progressMonitor) {
		// New ResourceSet for a new compilation
		currentResourceSet = new ResourceSetImpl();

		// Initialize the monitor. Can be used to stop the back-end
		// execution and provide feedback to user
		monitor = progressMonitor;

		if (network == null) {
			throw new TurnusRuntimeException("The input file seems to not contains any network");
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
		doAdditionalGeneration(network);
	}
	
	public boolean compileGenerateCode(int samplingRate) {

		RuntimeUtils.copyRuntimeFiles("C", libsDir);
		
		try {
		  	 File schedulerFile = new File(libsPath, "orcc-libs/orcc-runtime/src/scheduler.c");
		     String content = FileUtils.toString(schedulerFile);
		     content = content.replaceAll("#define MEMORY_SAMPLING_RATE 10000UL", "#define MEMORY_SAMPLING_RATE " + samplingRate + "UL");
		     FileUtils.writeStringToFile(schedulerFile, content);
		  } catch (TurnusException e) {
			  throw new TurnusRuntimeException("Could not update macro in the orcc-runtime/scheduler.c to adjust the sampling rate.", e);
		  }	
		
		if(getOptions().containsKey(NUMA_PROFILING_MODE.longName()) && getOptions().get(NUMA_PROFILING_MODE.longName()).equals("write")) {
			try { 
				File schedulerFile = new File(libsPath, "orcc-libs/orcc-runtime/src/scheduler.c");
				String content = FileUtils.toString(schedulerFile);
				content = content.replaceAll("#define NUMAP_PROFILE_MODE 0", "#define NUMAP_PROFILE_MODE 1");
				FileUtils.writeStringToFile(schedulerFile, content);
			} catch (TurnusException e) {
				throw new TurnusRuntimeException("Could not update macro in the orcc-runtime/scheduler.c to enable memory write sampling.", e);
			}
		}

		// -- Create file to log compiling activities.
		try {
			writer = new FileWriter(new File(srcGenPath, "compilerLog.txt"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String inputCommand;
		//inputCommand = "cmake .. -DCMAKE_BUILD_TYPE=Release -DCMAKE_VERBOSE_MAKEFILE=true";
		inputCommand = "cmake .. -DCMAKE_BUILD_TYPE=Release";
		if (!runActivity("Configure-Project-with-CMake", inputCommand, buildPath)) {
			return false;
		}
		
		inputCommand = "make -j8";
		if (!runActivity("Compiling-build", inputCommand, buildPath)) {
			return false;
		}

		return true;
	}
	
	private boolean runActivity(String activityName, String inputCommand, String workingDirPath){	
		//  -- Command List
		List<String> commands = new ArrayList<String>();
		commands.addAll(Arrays.asList(inputCommand.split(" ")));

		try {
			Logger.debug(Joiner.on(" ").join(commands));

			ProcessBuilder builder = new ProcessBuilder(commands);
			builder.directory(new File(workingDirPath));
			builder.redirectErrorStream(true);
			Process process = builder.start();
			InputStream stdout = process.getInputStream();

			String line;
			BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
			while ((line = reader.readLine()) != null) {
				Logger.debug("[%s] %s", activityName, line);
				writer.write(line + "\n");
			}
			reader.close();

			process.waitFor();
			if (process.exitValue() == 0) {
				Logger.info("Commands successfully executed for " + activityName);
				return true;
			} else {
				Logger.error("Commands execution did not succeed for " + activityName);
				writer.close();
				return false;
			}

		} catch (Exception ep) {
			System.err.println(ep);
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}		
	}

	private File createDirectory(String dirPath) {
		File dirObj = new File(dirPath);
		if (!dirObj.exists()) {
			dirObj.mkdir();
		}
		
		return dirObj;
	}
}

