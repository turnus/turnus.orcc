package turnus.orcc.profiler.ui.benchmark.tab

import static turnus.common.TurnusOptions.CAL_PROJECT
import static turnus.common.TurnusOptions.CAL_XDF
import static turnus.common.TurnusOptions.CAL_STIMULUS_FILE
import static turnus.common.TurnusOptions.VERSIONER
import static turnus.common.TurnusOptions.BENCHMARK_N_LOOPS
import static turnus.common.TurnusOptions.BENCHMARK_OTHER_OPTIONS
import static turnus.common.TurnusOptions.BENCHMARK_COMPILER_OPTIONS
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Group
import turnus.model.versioning.VersioningFactory
import turnus.orcc.profiler.util.OrccProfilerConstants
import turnus.ui.Icon
import turnus.ui.widget.launch.AbstractLaunchTab
import turnus.ui.widget.launch.ILaunchWidget
import turnus.ui.widget.launch.LaunchWidgetComboBox
import turnus.ui.widget.launch.LaunchWidgetSelectProject
import turnus.ui.widget.launch.LaunchWidgetSelectResource
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import javax.xml.ws.soap.AddressingFeature.Responses
import turnus.orcc.profiler.benchmark.TurnusBenchmark
import turnus.orcc.profiler.ui.code.tab.BasicOptionsTab
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationType
import weka.core.PluginManager
import turnus.common.util.EcoreUtils
import turnus.orcc.profiler.ui.benchmark.LaunchBackendSelector
import java.util.ArrayList
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent
import turnus.ui.widget.launch.LaunchWidgetSelectFileOptional
import turnus.ui.widget.launch.LaunchWidgetSpinnerInteger
import turnus.ui.widget.launch.LaunchWidgetTextOptional

class BenchmarkCenterTab extends AbstractLaunchTab {
	new() {
		super("Benchmarksetup", Icon.APPLICATION_ICON)
	}

	override protected void createOptionWidgets(Composite composite) {
		
			// Orcc Project Configuration Options
			val Group group_resource_selection = new Group(composite, SWT.NONE)
			group_resource_selection.setFont(composite.getFont())
			group_resource_selection.setText("CAL Project")
			group_resource_selection.setLayout(new GridLayout(1, false))
			group_resource_selection.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false))
			val w_project = new LaunchWidgetSelectProject(CAL_PROJECT,
				OrccProfilerConstants.ORCC_PROJECT_NATURE_ID, "", group_resource_selection)
			w_project.setText("Orcc Project")
			w_project.setTooltip("Select the Orcc Project containing the CAL network you want benchmark")
			addWidget(w_project)
			val w_xdf = new LaunchWidgetSelectResource(CAL_XDF, CAL_PROJECT, "xdf", "", group_resource_selection)
			w_xdf.setText("XDF")
			w_xdf.setTooltip("Select the top XDF network file defining the CAL network you want benchmark")
			addWidget(w_xdf)
		
		
			// Versioning options
			val Group group_versioning = new Group(composite, SWT.NONE)
			group_versioning.setFont(composite.getFont())
			group_versioning.setText("Versioning")
			group_versioning.setLayout(new GridLayout(1, false))
			group_versioning.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false))
			var String[] versioners = VersioningFactory.eINSTANCE.getRegisteredVersioners()
			var ILaunchWidget<?> w_version = new LaunchWidgetComboBox(VERSIONER, versioners, group_versioning)
			w_version.setText("Versioner")
			w_version.setTooltip("Select the file version for the generated output files")
			addWidget(w_version)
		
			//Bin options
			val Group group_options = new Group(composite, SWT.NONE)
			group_options.font = composite.font
			group_options.text = "Benchmark binary options"
			group_options.layout = new GridLayout(1,false)
			group_options.layoutData=new GridData(SWT.FILL,SWT.TOP,true,false)
		
			val w_stimulus = new LaunchWidgetSelectFileOptional(CAL_STIMULUS_FILE, "*", null, group_options);
			w_stimulus.setText("Input Stimulus (optional)");
			addWidget(w_stimulus);	
			
			val max_loops = Integer.MAX_VALUE;
			val w_loops = new LaunchWidgetSpinnerInteger(BENCHMARK_N_LOOPS,1,max_loops,1,1,group_options)
			w_loops.text = "Loop input N times"
			
			
			addWidget(w_loops)
			val w_compiler = new LaunchWidgetTextOptional(BENCHMARK_COMPILER_OPTIONS,"",group_options)
			w_compiler.text = "Compiler options to pass to the benchmark binary"
			addWidget(w_compiler)
			
			val w_other = new LaunchWidgetTextOptional(BENCHMARK_OTHER_OPTIONS,"",group_options)
			w_other.text = "Other options to pass to the benchmark binary"
			addWidget(w_other)
			
		//Compilers
			val group_backends = new Group(composite,SWT.NONE)
			group_backends.font = composite.font
			group_backends.text = "Backend Configurations"
			group_backends.layout = new GridLayout(1,false)
			group_backends.layoutData=new GridData(SWT.FILL,SWT.TOP,true,false)
		
			val w_backends = new LaunchBackendSelector(w_project,w_xdf,new ArrayList,4,group_backends)
			w_project.addModifyListener(new ModifyListener(){
				override modifyText(ModifyEvent e){
					w_backends.project = w_project.value
				}
			})
			
			w_xdf.addModifyListener(new ModifyListener(){
				override modifyText(ModifyEvent e){
					w_backends.xdf= w_xdf.value
				}
			})
			w_backends.enabled=true;
			addWidget(w_backends)
			
	}

	override protected void updateComposableOptions() {
	}
}
