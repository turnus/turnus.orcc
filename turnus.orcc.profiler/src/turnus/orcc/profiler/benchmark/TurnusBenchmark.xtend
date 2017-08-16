package turnus.orcc.profiler.benchmark

import static net.sf.orcc.OrccLaunchConstants.RUN_CONFIG_TYPE
import java.io.File
import java.util.HashMap
import java.util.Map
import net.sf.orcc.df.Network
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import turnus.common.TurnusException
import turnus.common.configuration.Configuration
import turnus.common.io.Logger
import turnus.common.util.EcoreUtils
import turnus.model.versioning.Versioner
import turnus.model.versioning.VersioningFactory
import turnus.orcc.profiler.util.OutputDirectoryBuilder

import static turnus.common.TurnusConstants.DEFAULT_VERSIONER
import static turnus.common.TurnusOptions.CAL_PROJECT
import static turnus.common.TurnusOptions.CAL_STIMULUS_FILE
import static turnus.common.TurnusOptions.CAL_XDF
import static turnus.common.TurnusOptions.VERSIONER
import static turnus.common.TurnusOptions.BENCHMARK_BACKENDS
import static turnus.common.TurnusOptions.BENCHMARK_N_LOOPS
import static turnus.common.TurnusOptions.BENCHMARK_OTHER_OPTIONS
import static turnus.common.TurnusOptions.BENCHMARK_COMPILER_OPTIONS
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import net.sf.orcc.df.transform.Instantiator
import net.sf.orcc.df.transform.NetworkFlattener
import turnus.orcc.profiler.util.TurnusModelInjector
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import static net.sf.orcc.OrccLaunchConstants.OUTPUT_FOLDER
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.ILaunchesListener
import org.eclipse.debug.core.ILaunchListener
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchesListener2
import org.eclipse.core.runtime.jobs.IJobChangeListener
import org.eclipse.debug.core.model.IDebugTarget
import org.eclipse.debug.core.DebugException
import org.eclipse.debug.core.model.IBreakpoint
import org.eclipse.core.resources.IMarkerDelta
import org.eclipse.debug.core.Launch
import net.sf.orcc.OrccRuntimeException
import net.sf.orcc.backends.BackendFactory
import org.eclipse.core.runtime.CoreException
import net.sf.orcc.util.OrccLogger
import net.sf.orcc.backends.Backend

import static net.sf.orcc.OrccLaunchConstants.BACKEND;
import net.sf.orcc.OrccActivator
import org.eclipse.core.runtime.IPath
import turnus.common.util.FileUtils
import turnus.model.analysis.profiler.impl.ProfilerFactoryImpl
import turnus.orcc.profiler.benchmark.util.Table
import turnus.orcc.profiler.benchmark.cpp.CPPConfig

class TurnusBenchmark {
	
	private Network network;
	private boolean stopRequest = false;
	var String projectName = null;
		var IFile xdf = null;
		var File out = null;
		var Versioner fv = null;
		var File stimulus = null;
		
	def public IStatus run(Configuration configuration) throws TurnusException {
		Logger.info("===== TURNUS BENCHMARK ====");
		Logger.info("Configuring the project");
		// parse the options
		try {
			Logger.info("Parsing the configuration");
			projectName = configuration.getValue(CAL_PROJECT);
			val IProject project = EcoreUtils.getProject(projectName);
			val String xdfFile = configuration.getValue(CAL_XDF);
			xdf = project.getFile(xdfFile);
			stimulus = configuration.getValue(CAL_STIMULUS_FILE);
			val String versioner = configuration.getValue(VERSIONER, DEFAULT_VERSIONER);
			fv = VersioningFactory.eINSTANCE.getVersioner(versioner);
			Logger.info("* Project: %s", projectName);
			Logger.info("* XDF: %s", xdf.getName());

			Logger.debug("* Versioner: %s (%s)", versioner, fv.getClass().getName());
			if (stimulus != null) {
				Logger.info("* Stimulus: %s", stimulus);
			}

			// create output directory
			out = OutputDirectoryBuilder.create("benchmark", configuration);

		} catch (Exception e) {
			throw new TurnusException("Error during the configuration parsing", e);
		}

		// check if a stop request has been send during the configuration
		if (stopRequest) {
			Logger.info("The simulation was aborted by the user during the network configuration");
			return Status.CANCEL_STATUS;
		}
		try{
		val backendCSV=configuration.getValue(BENCHMARK_BACKENDS)
		val n_loops = configuration.getValue(BENCHMARK_N_LOOPS,1)
		val other_options = configuration.getValue(BENCHMARK_OTHER_OPTIONS,"")
		val compiler_options = configuration.getValue(BENCHMARK_COMPILER_OPTIONS,"")
		val compileVariations=getBackends(backendCSV, stimulus,n_loops,other_options,compiler_options)
		val results=compileProfile(compileVariations)
		Logger.info('''
		Benchmark Results:
		
		«results.toString»
			''')
		val benchreport = (new ProfilerFactoryImpl).createBenchmarkReport
		benchreport.column_names.addAll(results.columns)
		for(r:0..<results.numRows){
			val row = (new ProfilerFactoryImpl).createTableRow
			for(e:results.getRow(r).entrySet){
			row.cells.put(e.key,e.value)
			}
			benchreport.rows.add(row)
		}
		val reportCSV=FileUtils.createFile(out,"benchmark_report","csv");
		FileUtils.writeStringToFile(reportCSV,results.toString)
		val reportFile=FileUtils.createFile(out,"bechmark_report","xml");
		EcoreUtils.storeEObject(benchreport, new ResourceSetImpl(),reportFile);
		Logger.info('''Stored report  in «reportFile.absolutePath»''')
		Logger.info('''Stored csv at «reportCSV.absolutePath»''')
		}
		 catch (TurnusException e) {
			throw e;
		} catch (Exception e) {
			throw new TurnusException("Error during the network and profiler building", e);
		}

		Logger.info("===== ANALYSIS DONE ====");
		return Status.OK_STATUS;
		
	}
	

	
	def getBackends(String backendCSV,  File stimulus, Integer n_loops,String other_options,String compiler_options) {
		val backends=new HashMap
		val orcc_comp_type = DebugPlugin.^default.launchManager.getLaunchConfigurationType(RUN_CONFIG_TYPE)
		val orcc_comp=DebugPlugin.^default.launchManager.getLaunchConfigurations(orcc_comp_type)
			
		for(v:backendCSV.split(";"))
		{
			for(o:orcc_comp){
				if(o.name==v){
	
					switch (o.getAttribute("net.sf.orcc.backend","")){
						case "Exelixi CPP Code Generation":backends.put(o.name,new CPPConfig(o, stimulus,n_loops,other_options,compiler_options)),
						case "Exelixi CPP Code Generation Caltoopia":backends.put(o.name,new CPPConfig(o, stimulus,n_loops,other_options,compiler_options)),
					default:Logger.warning('''Benchmark for backend «o.getAttribute("net.sf.orcc.backend","net.sf.orcc.backend attribute not found")» not yet implemented''')		
					}
				}
			}
		}
		return backends
	}
	
	def compileAndProfile(CompilerConfig compileProfile){
		try{
				val ILaunchConfigurationWorkingCopy cp= compileProfile.config.getWorkingCopy
				//change output path to our nested path
				//then compile to native, then profile with tools inferred for the given backend
				val old_out=cp.getAttribute(OUTPUT_FOLDER,"");
				val code_folder = new Path(out.absolutePath).append("code");
				val mode="run";//TODO get from conf/project?
				cp.setAttribute(OUTPUT_FOLDER,code_folder.toString)
				
				//since waiting on launch doesn't seem to be something supported, copy paste from orcc launch delegate
				//BEGIN copy paste
				{		
					val monitor = new NullProgressMonitor
					var String backendName = "unknown";
				try {
					backendName = cp.getAttribute(BACKEND, "");
				} catch (CoreException e1) {
					OrccLogger
							.severeln("Unable to find backend name in configuration attributes !");
				}


				try {
					// Get the backend instance
					val Backend backend = BackendFactory.getInstance().getBackend(
							backendName);

					// Configure it with options set in "Run Config" panel by
					// user
					backend.setOptions(cp.getAttributes());
					// Launch compilation
					backend.compile(monitor);

				} catch (OrccRuntimeException e) {

					if (!e.getMessage().isEmpty()) {
						OrccLogger.severeln(e.getMessage());
					}
					OrccLogger.severeln(backendName
							+ " backend could not generate code ("
							+ e.getCause() + ")");


				} catch (Exception e) {
										OrccLogger.severeln(backendName
							+ " backend could not generate code ("
							+ e.getCause() + ")");
				}
				}
				//END copy paste
				Logger.info("Generated code in "+code_folder.toString)
				val res=compileProfile.measure(code_folder)
				FileUtils.deleteDirectory(code_folder.toFile)
				return res;
		}
		 catch (TurnusException e) {
			throw e;
		} catch (Exception e) {
			throw new TurnusException("Error during the network and profiler building", e);
		}
		
	}
	def  compileProfile(Map<String,? extends CompilerConfig> compileVariations){
		val results=new HashMap()
		
		for(e:compileVariations.entrySet){
			Logger.info('''Measuring «e.key»''')
			val result=compileAndProfile(e.value)
			results.put(e.key,result)
		}
		
		val finalTable=new Table(results.values.last.columns)
		for(e:results.entrySet){
			finalTable.append(e.value)
		}
		return finalTable;
		
	}
	
	def public void stop() {
		Logger.info("Stop request sent to the server");
		stopRequest = true;
	}
}