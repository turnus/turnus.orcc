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
package turnus.orcc.profiler.ui.dynamic.tab;

import static turnus.common.TurnusOptions.MAPPING_FILE;
import static turnus.orcc.profiler.ProfilerOptions.NUMA_PROFILING_MODE;
import static turnus.orcc.profiler.ProfilerOptions.NUMA_SAMPLING_RATE;
import static turnus.orcc.profiler.ProfilerOptions.EXECUTION_LOOP;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import turnus.ui.widget.launch.ILaunchWidget;
import turnus.ui.widget.launch.LaunchWidgetComboBox;
import turnus.ui.widget.launch.LaunchWidgetSelectFileOptional;
import turnus.ui.widget.launch.LaunchWidgetSpinnerInteger;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class NumaBasicOptionsTab extends BasicOptionsTab {

	@Override
	protected void createOptionWidgets(Composite composite) {
		super.createOptionWidgets(composite);

		Group group = new Group(composite, SWT.SHADOW_ETCHED_OUT);
		group.setFont(composite.getFont());
		group.setText("NUMA Profiling mode");
		group.setLayout(new GridLayout(1, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		String[] modes = { "read", "write" };
		ILaunchWidget<?> w = new LaunchWidgetComboBox(NUMA_PROFILING_MODE, modes, group);
		w.setText("Mode");
		w.setTooltip("Select the NUMA profiling mode");
		addWidget(w);
		
		w = new LaunchWidgetSpinnerInteger(NUMA_SAMPLING_RATE, 1, 100000, 100, 10000, group);
		w.setText("Memory sampling rate");
		w.setTooltip("Set the memory sampling rate (1 sample in x recorded)");
		addWidget(w);
		
		w = new LaunchWidgetSpinnerInteger(EXECUTION_LOOP, 1, 1000, 10, 1, group);
		w.setText("The number of executions in a loop");
		w.setTooltip("Set the number of executions in a loop (-l)");
		addWidget(w);
		
		w = new LaunchWidgetSelectFileOptional(MAPPING_FILE, "*", null, group);
		w.setText("Mapping configuration (optional)");
		w.setTooltip("Select the .xcf mapping configuration (optional)");
		addWidget(w);
	}

}
