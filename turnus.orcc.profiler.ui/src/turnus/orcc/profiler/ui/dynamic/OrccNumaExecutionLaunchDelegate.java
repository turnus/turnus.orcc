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
package turnus.orcc.profiler.ui.dynamic;

import static turnus.common.configuration.Configuration.LaunchConfigurationParser.parse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.ui.console.MessageConsole;

import net.sf.orcc.ui.console.OrccUiConsoleHandler;
import net.sf.orcc.util.OrccLogger;
import turnus.common.TurnusOptions;
import turnus.common.TurnusProcess;
import turnus.common.configuration.Configuration;
import turnus.common.io.Logger;
import turnus.orcc.profiler.Activator;
import turnus.orcc.profiler.dynamic.numa.OrccNumaExecution;
import turnus.ui.util.EclipseUtils;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class OrccNumaExecutionLaunchDelegate implements ILaunchConfigurationDelegate {

	@Override
	public void launch(ILaunchConfiguration launchConfiguration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		
		try {
			monitor.subTask("TURNUS Orcc Dynamic NUMA Profiler");

			final Configuration configuration = parse(launchConfiguration, mode);
			// build the profiling job
			Job job = new Job("TURNUS Orcc Dynamic NUMA Profiler") {

				private OrccNumaExecution profiler;

				@Override
				protected void canceling() {
					Logger.info("Stop request sent by user");
					profiler.stop();
				}

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						// get and clear the console
						MessageConsole console = EclipseUtils.getDefaultConsole();
						OrccLogger.configureLoggerWithHandler(new OrccUiConsoleHandler(console));
						try {
							console.clearConsole();
						} finally {
							boolean verbose = configuration.getValue(TurnusOptions.CONFIG_VERBOSE, false);
							OrccLogger.setLevel(verbose ? OrccLogger.ALL : OrccLogger.TRACE);
							Logger.setVerbose(verbose);
						}

						profiler = new OrccNumaExecution();
						profiler.run(configuration);

						// refresh the workspace
						EclipseUtils.refreshWorkspace(monitor);
					} catch (Exception e) {
						String message = e.getMessage();
						message = message != null ? message : "";
						Logger.error("Something went wrong: " + message);
						return new Status(Status.ERROR, Activator.PLUGIN_ID, message, e);
					}

					return Status.OK_STATUS;
				}

			};

			// configure process
			launch.addProcess(new TurnusProcess("dynamic numa analysis", job, launch));

			// Houston we are go
			job.setUser(false);
			job.schedule();
		} catch (Exception e) {
			throw new CoreException(new Status(Status.ERROR, Activator.PLUGIN_ID, e.getMessage(), e));
		}
	}

}
