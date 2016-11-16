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
package turnus.orcc.profiler.ui.code.tab;

import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.common.TurnusOptions.VERSIONER;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import turnus.model.versioning.VersioningFactory;
import turnus.orcc.profiler.util.OrccProfilerConstants;
import turnus.ui.Icon;
import turnus.ui.widget.launch.AbstractLaunchTab;
import turnus.ui.widget.launch.ILaunchWidget;
import turnus.ui.widget.launch.LaunchWidgetComboBox;
import turnus.ui.widget.launch.LaunchWidgetSelectProject;
import turnus.ui.widget.launch.LaunchWidgetSelectResource;

/**
 * This class defines the basic options tab for the static code analysis
 * 
 * @author Simone Casale Brunet
 *
 */
public class BasicOptionsTab extends AbstractLaunchTab {

	public BasicOptionsTab() {
		super("Analysis Options", Icon.APPLICATION_ICON);
	}

	@Override
	protected void createOptionWidgets(Composite composite) {
		{ // Orcc Project Configuration Options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("CAL Project");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			ILaunchWidget<?> w = new LaunchWidgetSelectProject(CAL_PROJECT,
					OrccProfilerConstants.ORCC_PROJECT_NATURE_ID, "", group);
			w.setText("Orcc Project");
			w.setTooltip("Select the Orcc Project containing the CAL network you want profile");
			addWidget(w);

			w = new LaunchWidgetSelectResource(CAL_XDF, CAL_PROJECT, "xdf", "", group);
			w.setText("XDF");
			w.setTooltip("Select the top XDF network file defining the CAL network you want profile");
			addWidget(w);
		}

		{// Versioning options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Versioning");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

			String[] versioners = VersioningFactory.eINSTANCE.getRegisteredVersioners();

			ILaunchWidget<?> w = new LaunchWidgetComboBox(VERSIONER, versioners, group);
			w.setText("Versioner");
			w.setTooltip("Select the file version for the generated output files");
			addWidget(w);
		}

	}

	@Override
	protected void updateComposableOptions() {

	}

}
