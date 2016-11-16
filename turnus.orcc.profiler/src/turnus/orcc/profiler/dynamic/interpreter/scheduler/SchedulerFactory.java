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
package turnus.orcc.profiler.dynamic.interpreter.scheduler;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import turnus.common.TurnusException;
import turnus.common.io.Logger;
import turnus.orcc.profiler.Activator;
import turnus.orcc.profiler.dynamic.interpreter.scheduler.impl.RoundRobinScheduler;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class SchedulerFactory {

	/** the set of names of registered */
	private Set<String> schedulers;

	public static final SchedulerFactory INSTANCE = new SchedulerFactory();

	private SchedulerFactory() {
		schedulers = new HashSet<>();
		try {
			IExtensionRegistry registry = Platform.getExtensionRegistry();
			IConfigurationElement[] elements = registry.getConfigurationElementsFor(Activator.PLUGIN_ID + ".scheduler");
			for (IConfigurationElement element : elements) {
				try {
					String name = element.getAttribute("name");
					if (name == null) {
						Logger.error("There is a scheduler without name. It cannot be registered");
					} else if (schedulers.contains(name)) {
						Logger.error("There is already a scheduler named \"%s\"", name);
					} else {
						schedulers.add(name);
						Logger.debug("Versioner \"%s\" has been registered", name);
					}

				} catch (Exception e) {
				}
			}
		} catch (Exception e) {
			Logger.error("Error while initializing the scheduler factory. No schedulers can be registered");
		}
	}

	public String[] getRegisteredSchedulers() {
		return schedulers.toArray(new String[0]);
	}

	public Scheduler createScheduler(String name) throws TurnusException {
		if (schedulers.contains(name)) {
			try {
				IExtensionRegistry registry = Platform.getExtensionRegistry();
				IConfigurationElement[] elements = registry
						.getConfigurationElementsFor(Activator.PLUGIN_ID + ".scheduler");
				for (IConfigurationElement element : elements) {
					try {
						if (name.equals(element.getAttribute("name"))) {
							Scheduler s = (Scheduler) element.createExecutableExtension("class");
							return s;
						}
					} catch (CoreException e) {
						Logger.warning("Versioning factory: " + e.getMessage());
					}
				}
			} catch (Exception e) {
				Logger.error("Error while loading the versioning factory: " + e.getMessage());
			}
		} else {
			Logger.error("No versioner with name \"%s\" is registered. A default one will be used", name);
		}
		return new RoundRobinScheduler();
	}

}
