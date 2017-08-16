package turnus.orcc.profiler.ui.benchmark

import java.util.List
import org.eclipse.core.runtime.CoreException
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.swt.widgets.Composite
import turnus.ui.widget.launch.ILaunchWidget

import static turnus.common.TurnusOptions.BENCHMARK_BACKENDS
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent
import turnus.common.io.Logger

class LaunchBackendSelector extends BackendSelector implements ILaunchWidget<List<ILaunchConfiguration>> {
	
	private final String id;
	
	new(ILaunchWidget<?> project_w, ILaunchWidget<?> xdf_w,  List<ILaunchConfiguration> initialValue, int gridPlaces, Composite parent) {
		super(project_w, xdf_w,BENCHMARK_BACKENDS.longName, "TODO add tooltip", initialValue, gridPlaces, parent)
		this.id=BENCHMARK_BACKENDS.longName
	}
	
	
	override getId() {
	return id
	}
	
	override initializeFrom(ILaunchConfiguration configuration) throws CoreException {
		setProject(configuration.getAttribute(projectWidgetId, ""))
		setXdf(configuration.getAttribute(xdfWidgetId,""))
		setRawValue(configuration.getAttribute(this.id,""))
 		Util.initializeFrom(this,configuration)	
	}
	
	override performApply(ILaunchConfigurationWorkingCopy configuration) throws CoreException {

		Util.performApply(this,configuration)
	}
	
}