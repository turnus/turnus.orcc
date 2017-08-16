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
package turnus.orcc.profiler.ui.benchmark

import static turnus.common.TurnusOptions.CAL_PROJECT
import static turnus.common.TurnusOptions.CAL_XDF
import static turnus.common.TurnusOptions.VERSIONER
import static turnus.orcc.profiler.util.OrccProfilerConstants.LAUNCH_CONFIG_TYPE_BENCHMARK_ANALYSIS
import java.util.ArrayList
import java.util.Collection
import java.util.HashSet
import java.util.List
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.ILaunchConfigurationType
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy
import org.eclipse.debug.core.ILaunchManager
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.debug.ui.IDebugModelPresentation
import org.eclipse.debug.ui.IDebugUIConstants
import org.eclipse.debug.ui.ILaunchShortcut2
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.window.Window
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.IEditorInput
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.dialogs.ElementListSelectionDialog
import net.sf.orcc.util.OrccUtil
import turnus.common.util.FileUtils
import turnus.model.versioning.VersioningFactory

/** 
 * @author Igor Krawczuk
 */
class TurnusBenchmarkLaunchShortcut implements ILaunchShortcut2 {
	def private void chooseAndLaunch(IFile file, ILaunchConfiguration[] configs, String mode) {
		var ILaunchConfiguration config = null
		if (configs.length === 0) {
			config = createConfiguration(file)
		} else if (configs.length === 1) {
			config = configs.get(0)
		} else {
			config = chooseConfiguration(configs)
		}
		if (config !== null) {
			var Shell shell = getShell()
			DebugUITools::openLaunchConfigurationDialogOnGroup(shell, new StructuredSelection(config),
				IDebugUIConstants::ID_RUN_LAUNCH_GROUP)
		}
	}

	def private ILaunchConfiguration[] getConfigurations(IFile file) {
		var ILaunchManager manager = DebugPlugin::getDefault().getLaunchManager()
		var ILaunchConfigurationType type = manager.getLaunchConfigurationType(
			LAUNCH_CONFIG_TYPE_BENCHMARK_ANALYSIS)
		try {
			// configurations that match the given resource
			var List<ILaunchConfiguration> configs = new ArrayList<ILaunchConfiguration>()
			// candidates
			var ILaunchConfiguration[] candidates = manager.getLaunchConfigurations(type)
			var String name = FileUtils::getRelativePath(file)
			for (ILaunchConfiguration config : candidates) {
				var String fileName = config.getAttribute(CAL_XDF.longName(), "")
				if (fileName.equals(name)) {
					configs.add(config)
				}
			}
			return configs.toArray((#[] as ILaunchConfiguration[]))
		} catch (Exception e) {
			e.printStackTrace()
			return null
		}

	}

	def private ILaunchConfiguration chooseConfiguration(ILaunchConfiguration[] configs) {
		var IDebugModelPresentation labelProvider = DebugUITools::newDebugModelPresentation()
		var ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), labelProvider)
		dialog.setElements(configs)
		dialog.setTitle("Select TURNUS benchmark configuration")
		dialog.setMessage("&Select existing configuration:")
		dialog.setMultipleSelection(false)
		var int result = dialog.open()
		labelProvider.dispose()
		if (result === Window::OK) {
			return (dialog.getFirstResult() as ILaunchConfiguration)
		}
		return null
	}

	def private ILaunchConfiguration createConfiguration(IFile file) {
		var ILaunchManager manager = DebugPlugin::getDefault().getLaunchManager()
		var ILaunchConfigurationType type = manager.getLaunchConfigurationType(
			LAUNCH_CONFIG_TYPE_BENCHMARK_ANALYSIS)
		var ILaunchConfiguration config = null
		try {
			// configuration name
			var String name = OrccUtil::getQualifiedName(file)
			// remove spaces and initial dot
			name = name.trim()
			if (name.charAt(0) === Character.valueOf('.').charValue) {
				name = name.substring(1)
			}
			var String confName = manager.generateLaunchConfigurationName('''[Execution] «name»'''.toString)
			// create configuration
			var ILaunchConfigurationWorkingCopy wc = type.newInstance(null, confName)
			wc.setAttribute(CAL_PROJECT.longName(), file.getProject().getName())
			wc.setAttribute(CAL_XDF.longName(), FileUtils::getRelativePath(file))
			var String[] versioners = VersioningFactory::eINSTANCE.getRegisteredVersioners()
			wc.setAttribute(VERSIONER.longName(), versioners.get(0))
			// search for a project name that does not yet exists
			var String pojo = name
			try {
				var Collection<String> names = new HashSet<String>()
				for (IProject p : ResourcesPlugin::getWorkspace().getRoot().getProjects()) {
					names.add(p.getName())
				}
				var int v = 2
				while (names.contains(pojo)) {
					pojo = '''«name»-v«v»'''.toString
					v++
				}
			} catch (Exception e) {
			}

			config = wc.doSave()
		} catch (Exception e) {
			e.printStackTrace()
		}

		return config
	}

	override IResource getLaunchableResource(IEditorPart editorpart) {
		var IEditorInput input = editorpart.getEditorInput()
		if (input instanceof IFileEditorInput) {
			return ((input as IFileEditorInput)).getFile()
		}
		return null
	}

	override IResource getLaunchableResource(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			var IStructuredSelection ssel = (selection as IStructuredSelection)
			var Object obj = ssel.getFirstElement()
			if (obj instanceof IFile) {
				return (obj as IFile)
			}
		}
		return null
	}

	override ILaunchConfiguration[] getLaunchConfigurations(IEditorPart editorpart) {
		var IResource resource = getLaunchableResource(editorpart)
		if (resource instanceof IFile) {
			return getConfigurations((resource as IFile))
		} else {
			return null
		}
	}

	override ILaunchConfiguration[] getLaunchConfigurations(ISelection selection) {
		var IResource resource = getLaunchableResource(selection)
		if (resource instanceof IFile) {
			return getConfigurations((resource as IFile))
		} else {
			return null
		}
	}

	def private Shell getShell() {
		var IWorkbench workbench = PlatformUI::getWorkbench()
		var IWorkbenchWindow window = workbench.getActiveWorkbenchWindow()
		return window.getShell()
	}

	override void launch(IEditorPart editor, String mode) {
		var IResource resource = getLaunchableResource(editor)
		if (resource instanceof IFile) {
			var IFile file = (resource as IFile)
			chooseAndLaunch(file, getConfigurations(file), mode)
		}
	}

	override void launch(ISelection selection, String mode) {
		var IResource resource = getLaunchableResource(selection)
		if (resource instanceof IFile) {
			var IFile file = (resource as IFile)
			chooseAndLaunch(file, getConfigurations(file), mode)
		}
	}
}
