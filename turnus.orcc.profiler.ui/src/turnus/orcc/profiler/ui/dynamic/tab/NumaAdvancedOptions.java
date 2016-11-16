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

import static turnus.orcc.profiler.ProfilerOptions.CHECK_ARRAY_INBOUNDS;
import static turnus.orcc.profiler.ProfilerOptions.CODE_GENERATION_DEBUG_DIRECTIVES;
import static turnus.orcc.profiler.ProfilerOptions.INLINE_FUNCTIONS_AND_PROCEDURES;
import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_FOLDER;
import static turnus.orcc.profiler.ProfilerOptions.LINK_NATIVE_LIBRARY_HEADERS;
import static turnus.orcc.profiler.ProfilerOptions.USE_SMART_SCHEDULER;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import turnus.ui.Icon;
import turnus.ui.widget.launch.AbstractLaunchTab;
import turnus.ui.widget.launch.ILaunchWidget;
import turnus.ui.widget.launch.LaunchWidgetCheckBox;
import turnus.ui.widget.launch.LaunchWidgetSelectPathOptional;
import turnus.ui.widget.launch.LaunchWidgetTextOptional;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class NumaAdvancedOptions extends AbstractLaunchTab {

	public NumaAdvancedOptions() {
		super("Advanced Options", Icon.APPLICATION_PLUS);
	}

	@Override
	protected void createOptionWidgets(Composite composite) {
		{ // code generation options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Code generation options");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

			ILaunchWidget<?> w = new LaunchWidgetCheckBox(CHECK_ARRAY_INBOUNDS, false, group);
			w.setText("Check array bounds");
			w.setTooltip("Check this box if you want that array bounds are checked");
			addWidget(w);

			w = new LaunchWidgetCheckBox(INLINE_FUNCTIONS_AND_PROCEDURES, false, group);
			w.setText("Inline functions and procedures");
			w.setTooltip("Check this box if you want to inline functions and procedures");
			addWidget(w);

			w = new LaunchWidgetCheckBox(USE_SMART_SCHEDULER, false, group);
			w.setText("Use the smart scheduler");
			w.setTooltip("Check this box if you want to use the smart scheduler");
			addWidget(w);

			w = new LaunchWidgetCheckBox(CODE_GENERATION_DEBUG_DIRECTIVES, false, group);
			w.setText("Generate the source code inlining debug directives");
			w.setTooltip("Check this box if you want to generate the source code inlining debug directives");
			addWidget(w);
		}
		
		{ // link with native library
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Link with native library");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));
			
			ILaunchWidget<?> w = new LaunchWidgetSelectPathOptional(LINK_NATIVE_LIBRARY_FOLDER, false, null, group);
			w.setText("Path to native library folder (containing CMakeLists.txt)");
			w.setTooltip("Select the path to native library folder (containing CMakeLists.txt)");
			addWidget(w);
			
			w = new LaunchWidgetTextOptional(LINK_NATIVE_LIBRARY_HEADERS, "", group);
			w.setText("Names of the header files (separated by ';') containing all prototypes");
			w.setTooltip("Type the names of the header files (separated by ';') containing all prototypes");
			addWidget(w);			
		}

	}

	@Override
	protected void updateComposableOptions() {
/*		String libNativeLibraryFolder = (String) getWidget(LINK_NATIVE_LIBRARY_FOLDER).getValue();
		ILaunchWidget<?> wCompress = getWidget(LINK_NATIVE_LIBRARY_HEADERS);
		wCompress.setVisible(!libNativeLibraryFolder.isEmpty());
		wCompress.setEnabled(!libNativeLibraryFolder.isEmpty());*/
	}

}
