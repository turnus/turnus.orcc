package turnus.orcc.profiler.ui.benchmark

import turnus.ui.widget.Widget
import turnus.ui.widget.launch.ILaunchWidget
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import java.util.ArrayList
import java.util.List
import turnus.ui.widget.WidgetSelectResource
import turnus.ui.widget.WidgetComboBox
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.DebugPlugin
import turnus.ui.widget.launch.LaunchWidgetComboBox
import java.util.HashMap
import org.eclipse.emf.ecore.util.EcoreUtil
import turnus.common.util.EcoreUtils
import org.eclipse.core.runtime.Path
import static net.sf.orcc.OrccLaunchConstants.RUN_CONFIG_TYPE
import static net.sf.orcc.OrccLaunchConstants.PROJECT
import static net.sf.orcc.OrccLaunchConstants.XDF_FILE
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent
import turnus.common.io.Logger

class BackendSelector extends Widget<List<ILaunchConfiguration>> {
		protected List<WidgetComboBox> selectors = new ArrayList;
		protected Button add_button;
		protected Button delete_button;
	
		ILaunchWidget<?> project_w;
		ILaunchWidget<?> xdf_w;
		String xdf;
		String project;
		val config_map = new HashMap;
	
	new(ILaunchWidget<?> project_w, ILaunchWidget<?> xdf_w,String text, String toolTip, List<ILaunchConfiguration> initialValue, int gridPlaces, Composite parent) {
		super(text, toolTip, initialValue, gridPlaces, parent)
		this. project_w=project_w;
		this.xdf_w = xdf_w;
	}
	def clearMap(){
		config_map.clear
		config_map.put("No configurations",null)
	}
	def setProject(String p){
		project=p
	}
	
	def setXdf(String x){
		this.xdf =if(x.contains(".xdf")){
			val p=new Path(x)
			val newx=p.removeFileExtension.lastSegment
			if(!newx.contains(".")){
				"."+newx //"class path" to local top level xdf
			}else{
				newx //normal class path
			}
		}else{
			x
		}
	}
	
	def projectWidgetId(){
		return project_w.id
	}
	def xdfWidgetId(){
		return xdf_w.id
	}
	def removeSelector(){
		selectors.last.dispose
		selectors.remove(selectors.size-1)
		notifyListeners
	}
	
	def addSelector(){
					refreshMap
					val bnames=new ArrayList;
					for(e:config_map.entrySet){
						if (e.value!=null){
							bnames.add(e.key as String) 
							}
					}
					if(bnames.size==0){
						bnames.add("No configurations")
					}
					val cb = new WidgetComboBox("Select backend configuration",
					"Select a compiler backend configuration which will be used for this benchmark",
					bnames,
					parent)
					cb.addModifyListener(new ModifyListener(){
						
						override modifyText(ModifyEvent e) {
							notifyListeners
						}
						
					})
					selectors.add(cb)
	}
		
		override protected createWidgets(String text, String toolTip, List<ILaunchConfiguration> initialValue) {
			add_button = new Button(this,SWT.PUSH);
			add_button.text="Add backend config"
			add_button.toolTipText = "Select one of the run configurations to include in the benchmark";
			add_button.addSelectionListener(new SelectionAdapter(){
			
				override widgetSelected(SelectionEvent e){
				addSelector()
				}
			});
			delete_button = new Button(this, SWT.PUSH);
			delete_button.text="Delete backend config"
			delete_button.toolTipText = "Delete the last added run configurations from the benchmark";
			delete_button.addSelectionListener(new SelectionAdapter(){
			override widgetSelected(SelectionEvent e){
				removeSelector()
				}
			});
			
			
			for(v:initialValue){
				addSelector
				selectors.get(selectors.size-1).value = v.name
			}
		}
		
		override getValueAsString() {
			val selVals=selectors.map[s|s.value]
			val asString =String.join(";",selVals.filter[v|config_map.get(v)!=null])
			return asString		
		}
		
		override getValue(){
			val curVal= new ArrayList;
			for(s:selectors){
				val v=config_map.get(s.value);
				if(v!=null){
					curVal.add(v as ILaunchConfiguration)
				}
			}
			return curVal
		}
		def clearSelectors(){
			for(s:selectors){
				s.dispose
		}
		selectors.clear
		}
		override setValue(List<ILaunchConfiguration> configs){
			clearSelectors()
			refreshMap
			while(selectors.size<configs.size){
				addSelector
			}
			var i=0;
			for (c:configs){
				config_map.put(c.name,c)
				selectors.get(i).value=c.name
				i++
			}
		}
		
		override setRawValue(String value) {
			clearSelectors()
			val vals = value.split(";").filter[v|!v.isNullOrEmpty];
			while(vals.size>selectors.size){
					addSelector
			}
			var i =0;
			refreshMap
			
			for(v:vals){
				selectors.get(i).rawValue = v	
				i++
			}
		}
	def matchXdf(String cand){
		cand.contains(xdf)//TODO: refactor this, possible use the matching xdf selector like in compilation
	}
	
	def getRefreshMap() {
			clearMap
			val orcc_comp_type = DebugPlugin.^default.launchManager.getLaunchConfigurationType(RUN_CONFIG_TYPE)
			val orcc_comp = DebugPlugin.^default.launchManager.getLaunchConfigurations(orcc_comp_type)
			
			val project_comp = orcc_comp
			val backends = project_comp.filter[c|c.attributes.containsKey(XDF_FILE) && matchXdf(c.attributes.get(XDF_FILE) as String)]

			for (b:backends){
				config_map.put(b.name,b)
			}
			
	}
		
}