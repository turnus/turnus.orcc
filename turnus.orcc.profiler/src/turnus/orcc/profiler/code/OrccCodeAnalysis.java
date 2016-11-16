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
package turnus.orcc.profiler.code;

import static turnus.common.TurnusConstants.DEFAULT_VERSIONER;
import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.VERSIONER;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import com.google.inject.Injector;

import net.sf.orcc.cal.CalStandaloneSetup;
import net.sf.orcc.cal.cal.AstEntity;
import net.sf.orcc.df.transform.Instantiator;
import net.sf.orcc.df.transform.NetworkFlattener;
import turnus.analysis.profiler.code.StaticProfiler;
import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.common.util.EcoreUtils;
import turnus.common.util.FileUtils;
import turnus.model.dataflow.ActorClass;
import turnus.model.dataflow.Network;
import turnus.model.versioning.Versioner;
import turnus.model.versioning.VersioningFactory;
import turnus.orcc.profiler.code.ast.AstAnalyser;
import turnus.orcc.profiler.util.OutputDirectoryBuilder;
import turnus.orcc.profiler.util.TurnusModelInjector;

/**
 * This class defines the core of the Orcc Code analysis
 * 
 * @author Simone Casale Brunet
 *
 */
public class OrccCodeAnalysis {

	private boolean stopRequest = false;

	/**
	 * Run the analysis
	 * 
	 * @param configuration
	 * @return
	 * @throws TurnusException
	 */
	public IStatus run(Configuration configuration) throws TurnusException {
		Logger.info("===== TURNUS CODE ANALYSIS ====");

		IFile xdf = null;
		Versioner fv = null;
		Logger.info("Configuring the project");
		try {
			// parse the options
			String projectName = configuration.getValue(CAL_PROJECT);
			IProject project = EcoreUtils.getProject(projectName);
			String xdfFile = configuration.getValue(CAL_XDF);
			xdf = project.getFile(xdfFile);
			String versioner = configuration.getValue(VERSIONER, DEFAULT_VERSIONER);
			fv = VersioningFactory.eINSTANCE.getVersioner(versioner);
			Logger.info("* Project: %s", projectName);
			Logger.info("* XDF: %s", xdf.getName());
			Logger.debug("* Versioner: %s (%s)", versioner, fv.getClass().getName());

			// create output directory
			OutputDirectoryBuilder.create("profiling_code_analysis", configuration);
		} catch (Exception e) {
			throw new TurnusException("Error during the configuration parsing", e);
		}

		// parse the ORCC network and make a full instantiation
		Network network = null;
		ResourceSet resourceSet = null;
		Logger.info("Loading CAL files");
		try {
			ResourceSet set = new ResourceSetImpl();
			net.sf.orcc.df.Network orccNetwork = EcoreUtils.loadEObject(set, xdf);
			new Instantiator(true).doSwitch(orccNetwork);

			// flattens the network
			new NetworkFlattener().doSwitch(orccNetwork);

			// Get the resource set used by Frontend
			Injector injector = new CalStandaloneSetup().createInjectorAndDoEMFRegistration();
			resourceSet = injector.getInstance(ResourceSet.class);

			// inject the TURNUS model
			network = new TurnusModelInjector(fv).inject(orccNetwork);

		} catch (Exception e) {
			throw new TurnusException("Error during the CAL files parsing", e);
		}

		// configure the profiler
		Logger.info("Configuring the profiler");
		StaticProfiler profiler = null;
		try {
			profiler = new StaticProfiler(network);
			profiler.setConfiguration(configuration);
		} catch (Exception e) {
			throw new TurnusException("Error during the profiler configuration", e);
		}

		// start the analysis
		Logger.info("AST analysis");
		for (ActorClass aClass : network.getActorClasses()) {
			if (stopRequest) {
				Logger.warning("The analysis has been stopped by the user");
				break;
			}

			try {
				IFile file = FileUtils.getIFile(aClass.getSourceFile());
				AstEntity ast = EcoreUtils.loadEObject(resourceSet, file);
				new AstAnalyser(ast.getActor(), file, profiler).run();
				Logger.info("* CAL file: %s done", aClass.getSourceFile());
			} catch (Exception e) {
				Logger.error("* CAL file: %s error during the analysis!", aClass.getSourceFile());
			}
		}

		// stop the profiler
		try {
			Logger.info("Finalising the reults...");
			profiler.stop();
		} catch (Exception e) {
			throw new TurnusException("Error while stopping the profiler", e);
		}

		Logger.info("===== ANALYSIS DONE ====");
		return Status.OK_STATUS;

	}

	/**
	 * Stop by the user interface the analysis
	 */
	public void stop() {
		Logger.info("Stop request sent to the scheduler");
		stopRequest = true;
	}

}
