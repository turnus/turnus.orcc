package turnus.orcc.profiler.ui.benchmark.tab

import org.eclipse.debug.ui.AbstractLaunchConfigurationTabGroup
import org.eclipse.debug.ui.ILaunchConfigurationDialog
import org.eclipse.debug.ui.ILaunchConfigurationTab
import turnus.orcc.profiler.ui.code.tab.BasicOptionsTab
import org.eclipse.debug.core.ILaunchConfiguration

class TurnusBenchmarkTabGroup extends AbstractLaunchConfigurationTabGroup{
	
	def override createTabs(ILaunchConfigurationDialog dialog, String mode) {
		val ILaunchConfigurationTab[] tabs = #{ new BenchmarkCenterTab};
		setTabs(tabs);
	}
	
}