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

import static turnus.common.TurnusOptions.BUFFER_SIZE_DEFAULT;
import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_STIMULUS_FILE;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.VERSIONER;
import static turnus.orcc.profiler.ProfilerOptions.BUFFER_SIZE_MAP;
import static turnus.orcc.profiler.ProfilerOptions.CHECK_ARRAY_INBOUNDS;
import static turnus.orcc.profiler.ProfilerOptions.CODE_GENERATION_DEBUG_DIRECTIVES;
import static turnus.orcc.profiler.ProfilerOptions.INLINE_FUNCTIONS_AND_PROCEDURES;
import static turnus.orcc.profiler.ProfilerOptions.NUMA_PROFILING_MODE;
import static turnus.orcc.profiler.ProfilerOptions.USE_SMART_SCHEDULER;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import turnus.common.TurnusException;
import turnus.common.configuration.Configuration;
import turnus.common.configuration.Configuration.CliParser;
import turnus.common.io.Logger;

/**
 * 
 * @author Manuel Selva
 * @author Simone Casale Brunet
 *
 */
public class OrccNumaExecutionCli implements IApplication {

	private Configuration configuration;
	private IWorkspace workspace;
	private boolean isAutoBuildActivated;

	public OrccNumaExecutionCli() {
		workspace = ResourcesPlugin.getWorkspace();
	}

	private void disableAutoBuild() throws CoreException {
		IWorkspaceDescription desc = workspace.getDescription();
		if (desc.isAutoBuilding()) {
			isAutoBuildActivated = true;
			desc.setAutoBuilding(false);
			workspace.setDescription(desc);
		}
	}

	private void restoreAutoBuild() throws CoreException {
		if (isAutoBuildActivated) {
			IWorkspaceDescription desc = workspace.getDescription();
			desc.setAutoBuilding(true);
			workspace.setDescription(desc);
		}
	}

	@Override
	public Object start(IApplicationContext context) throws Exception {
		if (workspace == null) {
			Logger.error("The Eclipse workspace is not registered");
			return IApplication.EXIT_RELAUNCH;
		}

		try {
			parse((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
		} catch (TurnusException e) {
			return IApplication.EXIT_RELAUNCH;
		}

		// try to disable autobuild
		try {
			disableAutoBuild();
		} catch (Exception e) {
			Logger.error("Unable to set the workspace properties");
			return IApplication.EXIT_RELAUNCH;
		}

		Integer returnValue = IApplication.EXIT_OK;
		try {
			OrccNumaExecution profiler = new OrccNumaExecution();
			profiler.run(configuration);
		} catch (Exception e) {
			returnValue = IApplication.EXIT_RELAUNCH;
			Logger.error("The profiler exited with errors: %s", e.getMessage());
		}

		// try to restore autobuild
		try {
			restoreAutoBuild();
		} catch (Exception e) {
			returnValue = IApplication.EXIT_RELAUNCH;
			Logger.warning("Unable to restore the workspace auto build");
		}

		return returnValue;
	}

	private void parse(String[] args) throws TurnusException {
		CliParser cliParser = new CliParser()//
				.setOption(CAL_PROJECT, true)//
				.setOption(CAL_XDF, true)//
				.setOption(NUMA_PROFILING_MODE, true)//
				.setOption(CAL_STIMULUS_FILE, false)//
				.setOption(VERSIONER, false)//
				.setOption(INLINE_FUNCTIONS_AND_PROCEDURES, false)//
				.setOption(USE_SMART_SCHEDULER, false)//
				.setOption(CHECK_ARRAY_INBOUNDS, false)//
				.setOption(CODE_GENERATION_DEBUG_DIRECTIVES, false)//
				.setOption(BUFFER_SIZE_DEFAULT, false)//
				.setOption(BUFFER_SIZE_MAP, false)//
				.setOption(VERSIONER, false);
		configuration = cliParser.parse(args);

	}

	@Override
	public void stop() {
	}

}
