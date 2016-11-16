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

import static turnus.common.TurnusOptions.COMPRESS_TRACE;
import static turnus.common.TurnusOptions.EXPORT_GANTT_CHART;
import static turnus.common.TurnusOptions.EXPORT_TRACE;
import static turnus.common.TurnusOptions.SHARED_VARIABLES;
import static turnus.common.TurnusOptions.STACK_PROTECTION;
import static turnus.common.TurnusOptions.VERSIONER;
import static turnus.orcc.profiler.ProfilerOptions.CONSTANT_FOLDING;
import static turnus.orcc.profiler.ProfilerOptions.CONSTANT_PROPAGATION;
import static turnus.orcc.profiler.ProfilerOptions.DEAD_ACTION_ELIMINATION;
import static turnus.orcc.profiler.ProfilerOptions.DEAD_CODE_ELIMINATION;
import static turnus.orcc.profiler.ProfilerOptions.EXPRESSION_EVALUATION;
import static turnus.orcc.profiler.ProfilerOptions.SCHEDULER;
import static turnus.orcc.profiler.ProfilerOptions.TYPE_RESIZE_NATIVEPORTS;
import static turnus.orcc.profiler.ProfilerOptions.TYPE_RESIZE_TO32BITS;
import static turnus.orcc.profiler.ProfilerOptions.TYPE_RESIZE_TONBITS;
import static turnus.orcc.profiler.ProfilerOptions.VARIABLE_INITIALIZER;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import turnus.model.versioning.VersioningFactory;
import turnus.orcc.profiler.dynamic.interpreter.scheduler.SchedulerFactory;
import turnus.ui.Icon;
import turnus.ui.widget.launch.AbstractLaunchTab;
import turnus.ui.widget.launch.ILaunchWidget;
import turnus.ui.widget.launch.LaunchWidgetCheckBox;
import turnus.ui.widget.launch.LaunchWidgetComboBox;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class InterpreterAdvancedOptionsTab extends AbstractLaunchTab {

	public InterpreterAdvancedOptionsTab() {
		super("Advanced Options", Icon.APPLICATION_PLUS);
	}

	@Override
	protected void createOptionWidgets(Composite composite) {

		{ // Scheduler Options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Scheduler configuration");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			String[] schedulers = SchedulerFactory.INSTANCE.getRegisteredSchedulers();
			ILaunchWidget<?> w = new LaunchWidgetComboBox(SCHEDULER, schedulers, group);
			w.setText("Sheduler");
			w.setTooltip("Select the actors scheduler that should be used during the code interpretation");
			addWidget(w);
		}

		{ // Versioning options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Versioning");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			String[] versioners = VersioningFactory.eINSTANCE.getRegisteredVersioners();
			ILaunchWidget<?> w = new LaunchWidgetComboBox(VERSIONER, versioners, group);
			w.setText("Versioner");
			w.setTooltip("Select the file version for the generated output files");
			addWidget(w);
		}

		{// Profiling files options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Profiling files");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false));

			ILaunchWidget<?> w = new LaunchWidgetCheckBox(EXPORT_TRACE, true, group);
			w.setText("Export the execution trace graph file");
			w.setTooltip("Check this box if you want to export the ETG file");
			addWidget(w);

			w = new LaunchWidgetCheckBox(COMPRESS_TRACE, true, group);
			w.setText("Compressed ETG file (tracez)");
			w.setTooltip("Check this box if you want to export a compressed ETG file");
			addWidget(w);

			w = new LaunchWidgetCheckBox(EXPORT_GANTT_CHART, false, group);
			w.setText("Export the gantt chart");
			w.setTooltip("Check this box if you want to export the gantt chartt");
			addWidget(w);
		}

		{ // IR code interpretation Options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("IR code interpretation");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			ILaunchWidget<?> w = new LaunchWidgetCheckBox(STACK_PROTECTION, false, group);
			w.setText("Stack protection");
			w.setTooltip("Check this box if you want to enable the stack protection");
			addWidget(w);

			w = new LaunchWidgetCheckBox(SHARED_VARIABLES, true, group);
			w.setText("Shared variables support");
			w.setTooltip("Check this box if you want to enable the shared-variable support");
			addWidget(w);
		}

		{ // IR code transformation Options
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("IR code transformation");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			ILaunchWidget<?> w = new LaunchWidgetCheckBox(CONSTANT_FOLDING, false, group);
			w.setText("Constant folding");
			w.setTooltip("Check this box if you want to enable the the constant folding");
			addWidget(w);

			w = new LaunchWidgetCheckBox(CONSTANT_PROPAGATION, false, group);
			w.setText("Constant propagation");
			w.setTooltip("Check this box if you want to enable the constant propagation");
			addWidget(w);

			w = new LaunchWidgetCheckBox(DEAD_ACTION_ELIMINATION, true, group);
			w.setText("Dead action");
			w.setTooltip("Check this box if you want to enable the dead action");
			addWidget(w);

			w = new LaunchWidgetCheckBox(DEAD_CODE_ELIMINATION, true, group);
			w.setText("Dead code elimination");
			w.setTooltip("Check this box if you want to enable the dead code elimination");
			addWidget(w);

			w = new LaunchWidgetCheckBox(EXPRESSION_EVALUATION, false, group);
			w.setText("Expression evaluation");
			w.setTooltip("Check this box if you want to enable the expression evaluation");
			addWidget(w);

			w = new LaunchWidgetCheckBox(VARIABLE_INITIALIZER, false, group);
			w.setText("Variable initialiser");
			w.setTooltip("Check this box if you want to enable the variable initialiser");
			addWidget(w);
		}

		{ // Type Resize
			Group group = new Group(composite, SWT.NONE);
			group.setFont(composite.getFont());
			group.setText("Type resizing");
			group.setLayout(new GridLayout(1, false));
			group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			ILaunchWidget<?> w = new LaunchWidgetCheckBox(TYPE_RESIZE_TONBITS, false, group);
			w.setText("To N bit");
			w.setTooltip("Check this box if you want to enable the type resize to N bit");
			addWidget(w);

			w = new LaunchWidgetCheckBox(TYPE_RESIZE_TO32BITS, false, group);
			w.setText("To 32 bit");
			w.setTooltip("Check this box if you want to enable the type resize to 32 bit");
			addWidget(w);

			w = new LaunchWidgetCheckBox(TYPE_RESIZE_NATIVEPORTS, false, group);
			w.setText("Native ports");
			w.setTooltip("Check this box if you want to enable the native ports resize");
			addWidget(w);
		}

	}

	@Override
	protected void updateComposableOptions() {
		boolean exportTrace = (Boolean) getWidget(EXPORT_TRACE).getValue();
		ILaunchWidget<?> wCompress = getWidget(COMPRESS_TRACE);
		wCompress.setVisible(exportTrace);
		wCompress.setEnabled(exportTrace);
	}

}
