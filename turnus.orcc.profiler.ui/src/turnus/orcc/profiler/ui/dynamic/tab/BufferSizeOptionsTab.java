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

import static turnus.common.TurnusConstants.CONFIGURATION_UNDEFINED_OPTION;
import static turnus.common.TurnusExtensions.BUFFER_SIZE;
import static turnus.common.TurnusOptions.BUFFER_SIZE_DEFAULT;
import static turnus.common.TurnusOptions.CAL_PROJECT;
import static turnus.common.TurnusOptions.CAL_XDF;
import static turnus.orcc.profiler.ProfilerOptions.BUFFER_SIZE_MAP;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import net.sf.orcc.df.Actor;
import net.sf.orcc.df.Connection;
import net.sf.orcc.df.Network;
import net.sf.orcc.df.Port;
import net.sf.orcc.df.transform.Instantiator;
import net.sf.orcc.df.transform.NetworkFlattener;
import net.sf.orcc.graph.Vertex;
import net.sf.orcc.ir.ExprInt;
import net.sf.orcc.util.Attribute;
import net.sf.orcc.util.OrccLogger;
import net.sf.orcc.util.util.EcoreHelper;
import turnus.common.io.Logger;
import turnus.common.util.MapUtils;
import turnus.common.util.Pair;
import turnus.model.mapping.io.XmlBufferSizeReader;
import turnus.orcc.profiler.ui.Activator;
import turnus.orcc.profiler.util.OrccBufferSizer;
import turnus.ui.Icon;
import turnus.ui.widget.Widget;
import turnus.ui.widget.WidgetSelectFile;
import turnus.ui.widget.launch.AbstractLaunchTab;
import turnus.ui.widget.launch.ILaunchWidget;
import turnus.ui.widget.launch.LaunchWidgetSpinnerInteger;

/**
 * 
 * @author Simone Casale Brunet
 *
 */
public class BufferSizeOptionsTab extends AbstractLaunchTab {

	private static class BufferSizeTableWidget extends Widget<String>implements ILaunchWidget<String> {

		/**
		 * This class defines the cell modifier
		 * 
		 * @author Simone Casale Brunet
		 * 
		 */
		private class CellModifier implements ICellModifier {

			@Override
			public boolean canModify(Object element, String property) {
				return property.equals(columnNames[SIZE]);
			}

			@Override
			public Object getValue(Object element, String property) {
				// Find the index of the column
				int columnIndex = Arrays.asList(columnNames).indexOf(property);

				Object result = "";
				Connection fifo = (Connection) element;

				switch (columnIndex) {
				case SRC_ACTOR:
					result = ((Actor) fifo.getSource()).getName();
					break;
				case SRC_PORT:
					result = ((Port) fifo.getSourcePort()).getName();
					break;
				case TGT_ACTOR:
					result = ((Actor) fifo.getTarget()).getName();
					break;
				case TGT_PORT:
					result = ((Port) fifo.getTargetPort()).getName();
					break;
				case SIZE:
					if (isMapped(fifo)) {
						result = getSize(fifo);
					}
					break;
				default:
					break;

				}

				return result;
			}

			@Override
			public void modify(Object element, String property, Object value) {
				// int columnIndex =
				// Arrays.asList(columnNames).indexOf(property);
				if (property.equals(columnNames[SIZE])) {
					String intString = (String) value;
					if (intString.matches("[+]?\\d+")) {
						TableItem item = (TableItem) element;
						Connection fifo = (Connection) item.getData();
						setSize(fifo, intString);
						setValue(MapUtils.asString(mapping), true);
						viewer.refresh();
						notifyListener();
					}
				}

			}

		}

		/**
		 * This class defines the content provider
		 * 
		 * @author Simone Casale Brunet
		 * 
		 */
		private class ContentProvider implements IStructuredContentProvider {

			@Override
			public void dispose() {
			}

			@Override
			@SuppressWarnings("unchecked")
			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof HashSet<?>) {
					return ((Collection<Connection>) inputElement).toArray(new Connection[0]);
				} else {
					return new Object[0];
				}
			}

			@Override
			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		}

		/**
		 * This class defines the label provider for the Instances Mapping Tab
		 * 
		 * @author Simone Casale Brunet
		 * 
		 */
		private class TableLabelProvider extends LabelProvider implements ITableLabelProvider {

			@Override
			public Image getColumnImage(Object element, int columnIndex) {
				return null;
			}

			@Override
			public String getColumnText(Object element, int columnIndex) {
				String result = "";
				Connection fifo = (Connection) element;
				switch (columnIndex) {
				case SRC_ACTOR:
					result = ((Actor) fifo.getSource()).getName();
					break;
				case SRC_PORT:
					result = ((Port) fifo.getSourcePort()).getName();
					break;
				case TGT_ACTOR:
					result = ((Actor) fifo.getTarget()).getName();
					break;
				case TGT_PORT:
					result = ((Port) fifo.getTargetPort()).getName();
					break;
				case SIZE:
					if (isMapped(fifo)) {
						result = getSize(fifo);
					}
					break;
				default:
					break;
				}
				return result;
			}
		}

		/**
		 * This class defines a content comparator for sorting instances by the
		 * selected column values.
		 * 
		 * @author Simone Casale Brunet
		 * 
		 */
		private class ContentComparator extends ViewerComparator {
			private int propertyIndex;
			private static final int DESCENDING = 1;
			private int direction = DESCENDING;

			public ContentComparator() {
				this.propertyIndex = 0;
				direction = DESCENDING;
			}

			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				Connection f1 = (Connection) e1;
				Connection f2 = (Connection) e2;
				int rc = 0;
				switch (propertyIndex) {
				case SRC_ACTOR:
					rc = ((Actor) f1.getSource()).getName().compareTo(((Actor) f2.getSource()).getName());
					break;
				case SRC_PORT:
					rc = ((Port) f1.getSourcePort()).getName().compareTo(((Port) f2.getSourcePort()).getName());
					break;
				case TGT_ACTOR:
					rc = ((Actor) f1.getTarget()).getName().compareTo(((Actor) f2.getTarget()).getName());
					break;
				case TGT_PORT:
					rc = ((Port) f1.getTargetPort()).getName().compareTo(((Port) f2.getTargetPort()).getName());
					break;
				case SIZE:
					int size1 = isMapped(f1) ? Integer.parseInt(getSize(f1)) : 0;
					int size2 = isMapped(f2) ? Integer.parseInt(getSize(f2)) : 0;
					rc = size1 - size2;
					break;
				default:
					rc = 0;
				}
				// If descending order, flip the direction
				if (direction == DESCENDING) {
					rc = -rc;
				}
				return rc;
			}

			public int getDirection() {
				return direction == 1 ? SWT.DOWN : SWT.UP;
			}

			public void setColumn(int column) {
				if (column == this.propertyIndex) {
					// Same sorting column: toggle the direction
					direction = 1 - direction;
				} else {
					// New sorting column: ascending sort
					this.propertyIndex = column;
					direction = DESCENDING;
				}
			}
		}

		private class LoadBxdfDialog extends TitleAreaDialog implements ModifyListener {

			private WidgetSelectFile fileChooser;
			private File file;
			private Map<String, String> bufferMap;

			public LoadBxdfDialog(Shell parentShell) {
				super(parentShell);
			}

			@Override
			public void create() {
				super.create();
				setTitle("Load the buffer size configuration file from a BXDF file");
				setMessage("Select a BXDF from where the buffer size configuration will be loaded",
						IMessageProvider.INFORMATION);
				getButton(IDialogConstants.OK_ID).setEnabled(false);
			}

			@Override
			public void modifyText(ModifyEvent e) {
				file = fileChooser.getValue();
				if (file != null && file.exists()) {
					// reset the error message
					setErrorMessage(null);
					getButton(IDialogConstants.OK_ID).setEnabled(true);
					return;
				}

				file = null;
				setErrorMessage("no input file selected");
				getButton(IDialogConstants.OK_ID).setEnabled(false);

			}

			@Override
			protected Control createDialogArea(Composite parent) {
				Composite area = (Composite) super.createDialogArea(parent);
				Composite container = new Composite(area, SWT.NONE);
				container.setLayoutData(new GridData(GridData.FILL_BOTH));
				GridLayout layout = new GridLayout(1, false);
				container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
				container.setLayout(layout);

				GridData dataFirstName = new GridData();
				dataFirstName.grabExcessHorizontalSpace = true;
				dataFirstName.horizontalAlignment = GridData.FILL;

				fileChooser = new WidgetSelectFile("File", "Select the buffer size configuration file", BUFFER_SIZE,
						null, container);
				fileChooser.addModifyListener(this);

				setErrorMessage("no input file selected");

				return container;
			}

			@Override
			protected boolean isResizable() {
				return true;
			}

			@Override
			protected void okPressed() {
				loadFile();
				super.okPressed();
			}

			private void loadFile() {
				try {
					bufferMap = new HashMap<>();
					com.google.common.collect.Table<Pair<String, String>, Pair<String, String>, Integer> table = new XmlBufferSizeReader()
							.load(file).asTable();
					for (Entry<Pair<String, String>, Map<Pair<String, String>, Integer>> actorEntry : table.rowMap()
							.entrySet()) {
						Pair<String, String> source = actorEntry.getKey();
						for (Entry<Pair<String, String>, Integer> actionEntry : actorEntry.getValue().entrySet()) {
							Pair<String, String> target = actionEntry.getKey();
							String size = Integer.toString(actionEntry.getValue());
							String srcActor = source.v1;
							String srcPort = source.v2;
							String tgtActor = target.v1;
							String tgtPort = target.v2;

							String key = OrccBufferSizer.getKey(srcActor, srcPort, tgtActor, tgtPort);
							bufferMap.put(key, size);
						}
					}

				} catch (Exception e) {
					Logger.error(e.getMessage());
					bufferMap = null;
				}
			}

			public Map<String, String> getBufferSizeMap() {
				return bufferMap;
			}

		}

		private class LoadBxdfButtonListener implements SelectionListener {

			@Override
			public void widgetSelected(SelectionEvent e) {
				LoadBxdfDialog page = new LoadBxdfDialog(getShell());
				page.open();

				Map<String, String> map = page.getBufferSizeMap();
				if (map != null) {
					Set<String> keys = new HashSet<>();
					for (Connection connection : connections) {
						String key = OrccBufferSizer.getKey(connection);
						keys.add(key);
					}

					mapping = new HashMap<String, String>();
					Set<String> unmapped = new HashSet<>();
					for (Entry<String, String> entry : map.entrySet()) {
						String key = entry.getKey();
						if (keys.contains(key)) {
							mapping.put(key, entry.getValue());
						} else {
							String buffer[] = OrccBufferSizer.split(key);
							String srcActor = "";
							String srcPort = "";
							String tgtActor = "";
							String tgtPort = "";

							try {
								srcActor = buffer[0];
							} catch (Exception exc) {
								srcActor = "";
							}
							try {
								srcPort = buffer[1];
							} catch (Exception exc) {
								srcPort = "";
							}
							try {
								tgtActor = buffer[2];
							} catch (Exception exc) {
								tgtActor = "";
							}
							try {
								tgtPort = buffer[3];
							} catch (Exception exc) {
								tgtPort = "";
							}
							String name = srcActor + "[" + srcPort + "] -> " + tgtActor + "[" + tgtPort + "]";
							unmapped.add(name);
							OrccLogger.warnln("Buffer \"" + name
									+ "\"is not available in this network and it will not be mapped");
						}
					}

					if (!unmapped.isEmpty()) {
						List<Status> childStatuses = new ArrayList<Status>();
						for (String buffer : unmapped) {
							Status status = new Status(IStatus.WARNING, Activator.PLUGIN_ID, buffer);
							childStatuses.add(status);
						}

						MultiStatus ms = new MultiStatus(Activator.PLUGIN_ID, IStatus.WARNING,
								childStatuses.toArray(new Status[] {}), "See the list of buffers that are not found",
								null);

						ErrorDialog.openError(getShell(), "Warning",
								"There are some buffers defined in the BXDF file that are not defined in this project",
								ms);
					}

					setValue(MapUtils.asString(mapping), false);
					viewer.refresh();
					notifyListener();
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {

			}

		}

		private class RealoadXdfBuffonListener implements SelectionListener {

			@Override
			public void widgetSelected(SelectionEvent e) {
				loadXdfMapping();
				viewer.refresh();
				notifyListener();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}

		}

		private class ClearButtonListener implements SelectionListener {

			@Override
			public void widgetSelected(SelectionEvent e) {
				setValue("", true);
				viewer.refresh();
				notifyListener();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
			}

		}

		private static final String LOADED_CONFIGURATION = CONFIGURATION_UNDEFINED_OPTION
				+ "turnus.profiler.orcc.bufferSize.widget.configuration";

		private static final int SRC_ACTOR = 0;
		private static final int SRC_PORT = 1;
		private static final int TGT_ACTOR = 2;
		private static final int TGT_PORT = 3;
		private static final int SIZE = 4;

		/** the columns names */
		private static final String[] columnNames = new String[] { "Src Actor", "Src Port", "Tgt Actor", "Tgt Port",
				"Size (tokens)" };

		private Table table;
		private TableViewer viewer;
		private ContentComparator comparator;
		private Collection<ModifyListener> listeners = new HashSet<>();

		private String config = "";

		private Map<String, String> mapping;
		private Collection<Connection> connections;

		public BufferSizeTableWidget(Composite parent) {
			super("Buffer Size Mapping Table", "Define the buffer size for each connection", "", 1, parent);

		}

		/**
		 * Create a new {@link SelectionAdapter} for adding the sorting facility
		 * to the column
		 * 
		 * @param column
		 *            the table column
		 * @param index
		 *            the column number index
		 * @return
		 */
		private SelectionAdapter getSelectionAdapter(final TableColumn column, final int index) {
			SelectionAdapter selectionAdapter = new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					comparator.setColumn(index);
					int dir = comparator.getDirection();
					viewer.getTable().setSortDirection(dir);
					viewer.getTable().setSortColumn(column);
					viewer.refresh();
				}
			};
			return selectionAdapter;
		}

		@Override
		public boolean isValid() {
			return true;
		}

		private void notifyListener() {
			for (ModifyListener listener : listeners) {
				Event event = new Event();
				event.data = mapping;
				event.widget = table;
				event.type = SWT.Modify;
				listener.modifyText(new ModifyEvent(event));
			}
		}

		@Override
		public void redraw() {
		}

		@Override
		public void initializeFrom(ILaunchConfiguration configuration) throws CoreException {

			setValue(configuration.getAttribute(getId(), getValue()), true);

			String newProject = configuration.getAttribute(CAL_PROJECT.longName(), "");
			String newXdf = configuration.getAttribute(CAL_XDF.longName(), "");
			loadConnections(newProject, newXdf);
			viewer.setInput(connections);

		}

		public void setValue(String value, boolean redraw) {
			super.setValue(value, redraw);
			mapping = new HashMap<>(MapUtils.asMap(value));
		}

		@Override
		public void performApply(ILaunchConfigurationWorkingCopy configuration) {
			try {
				{// check if a new table must be created
					String newProject = configuration.getAttribute(CAL_PROJECT.longName(), "");
					String newXdf = configuration.getAttribute(CAL_XDF.longName(), "");
					String newConfig = configStamp(newProject, newXdf);

					config = configuration.getAttribute(LOADED_CONFIGURATION, "");

					if (!newConfig.equals(config)) {
						config = newConfig;
						configuration.setAttribute(LOADED_CONFIGURATION, config);
						loadConnections(newProject, newXdf);
						loadXdfMapping();
						viewer.setInput(connections);
					}
				}

				configuration.setAttribute(getId(), getValue());

			} catch (CoreException e) {
				e.printStackTrace();
			}

		}

		private void loadXdfMapping() {
			mapping = new HashMap<String, String>();
			for (Connection connection : connections) {
				Attribute attribute = connection.getAttribute("bufferSize");
				if (attribute != null) {
					int size = ((ExprInt) attribute.getContainedValue()).getIntValue();
					setSize(connection, Integer.toString(size));
				}
			}
			setValue(MapUtils.asString(mapping), false);

		}

		private void loadConnections(String project, String xdf) {
			connections.clear();

			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			if (!root.getFullPath().isValidSegment(project)) {
				return;
			}

			IProject iproject = root.getProject(project);
			if (!iproject.exists()) {
				return;
			}

			IFile ixdf = iproject.getFile(xdf);
			if (!ixdf.exists()) {
				return;
			}

			ResourceSet set = new ResourceSetImpl();
			Network network = EcoreHelper.getEObject(set, ixdf);
			new Instantiator(true).doSwitch(network);
			new NetworkFlattener().doSwitch(network);

			// parse fifos
			for (Connection buffer : network.getConnections()) {
				Vertex srcVertex = buffer.getSource();
				Vertex tgtVertex = buffer.getTarget();
				if (srcVertex instanceof net.sf.orcc.df.Actor && tgtVertex instanceof net.sf.orcc.df.Actor) {
					// get the communication end-points
					Port srcPort = buffer.getSourcePort();
					Port tgtPort = buffer.getTargetPort();

					// get source and target actors
					if ((srcPort != null) && (tgtPort != null)) {
						connections.add(buffer);
					}
				}
			}

		}

		private boolean setSize(Connection buffer, String size) {
			if (size.matches("[+]?\\d+")) {
				String strBuffer = OrccBufferSizer.getKey(buffer);
				mapping.put(strBuffer, size);
				return true;
			} else {
				return false;
			}
		}

		private String getSize(Connection buffer) {
			String strBuffer = OrccBufferSizer.getKey(buffer);
			return mapping.get(strBuffer);
		}

		private boolean isMapped(Connection buffer) {
			String strFifo = OrccBufferSizer.getKey(buffer);
			return mapping.containsKey(strFifo);
		}

		private String configStamp(String project, String xdf) {
			return project + xdf;
		}

		@Override
		public void addModifyListener(ModifyListener listener) {
			listeners.add(listener);
		}

		@Override
		public String getId() {
			return BUFFER_SIZE_MAP.longName();
		}

		@Override
		protected void createWidgets(String text, String toolTip, String initialValue) {
			Label lbl = new Label(this, SWT.NONE);
			lbl.setText("Custum buffer size configuration:");
			lbl.setToolTipText(toolTip);
			lbl.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

			this.value = initialValue;
			mapping = MapUtils.asMap(initialValue);
			connections = new HashSet<Connection>();

			table = new Table(this, SWT.BORDER | SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
			viewer = new TableViewer(table);
			comparator = new ContentComparator();
			viewer.setComparator(comparator);

			GridData gridData = new GridData(GridData.FILL_BOTH);
			gridData.grabExcessVerticalSpace = true;
			gridData.horizontalSpan = 3;
			gridData.heightHint = 300;
			table.setLayoutData(gridData);

			table.setLinesVisible(true);
			table.setHeaderVisible(true);

			// column: source actor
			TableColumn column = new TableColumn(table, SWT.LEFT, SRC_ACTOR);
			column.setText(columnNames[SRC_ACTOR]);
			column.setWidth(100);
			column.addSelectionListener(getSelectionAdapter(column, SRC_ACTOR));

			// column: source port
			column = new TableColumn(table, SWT.LEFT, SRC_PORT);
			column.setText(columnNames[SRC_PORT]);
			column.setWidth(100);
			column.addSelectionListener(getSelectionAdapter(column, SRC_PORT));

			// column: target actor
			column = new TableColumn(table, SWT.LEFT, TGT_ACTOR);
			column.setText(columnNames[TGT_ACTOR]);
			column.setWidth(100);
			column.addSelectionListener(getSelectionAdapter(column, TGT_ACTOR));

			// column: target port
			column = new TableColumn(table, SWT.LEFT, TGT_PORT);
			column.setText(columnNames[TGT_PORT]);
			column.setWidth(100);
			column.addSelectionListener(getSelectionAdapter(column, TGT_PORT));

			// column: size
			column = new TableColumn(table, SWT.LEFT, SIZE);
			column.setText(columnNames[SIZE]);
			column.setWidth(100);
			column.addSelectionListener(getSelectionAdapter(column, SIZE));

			// create the table viewer
			viewer.setUseHashlookup(true);
			viewer.setColumnProperties(columnNames);
			viewer.setContentProvider(new ContentProvider());
			viewer.setLabelProvider(new TableLabelProvider());

			// Create the cell editors
			CellEditor[] editors = new CellEditor[columnNames.length];
			editors[SIZE] = new TextCellEditor(table);
			// set the editors
			viewer.setCellEditors(editors);
			viewer.setCellModifier(new CellModifier());

			// reload from XDF and clear mapping buttons
			Composite composite = new Composite(this, SWT.SHADOW_NONE);
			composite.setLayout(new GridLayout(3, false));
			composite.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));

			Button realodXdfMappingButton = new Button(composite, SWT.PUSH);
			realodXdfMappingButton.setFont(getFont());
			realodXdfMappingButton.setImage(Icon.getImage(Icon.ARROW_090));
			realodXdfMappingButton.setLayoutData(new GridData(SWT.BORDER, SWT.CENTER, false, false));
			realodXdfMappingButton.setText("Load from XDF");
			realodXdfMappingButton.addSelectionListener(new RealoadXdfBuffonListener());

			Button loadBxdfButton = new Button(composite, SWT.PUSH);
			loadBxdfButton.setFont(getFont());
			loadBxdfButton.setImage(Icon.getImage(Icon.ARROW_090));
			loadBxdfButton.setLayoutData(new GridData(SWT.BORDER, SWT.CENTER, false, false));
			loadBxdfButton.setText("Load from BXDF");
			loadBxdfButton.addSelectionListener(new LoadBxdfButtonListener());

			Button clearButton = new Button(composite, SWT.PUSH);
			clearButton.setFont(getFont());
			clearButton.setImage(Icon.getImage(Icon.ARROW_CIRCLE));
			clearButton.setLayoutData(new GridData(SWT.BORDER, SWT.CENTER, false, false));
			clearButton.setText("Clear All");
			clearButton.addSelectionListener(new ClearButtonListener());

		}

		@Override
		public String getValueAsString() {
			return getValue();
		}

		@Override
		public void setRawValue(String value) {
			setValue(value);

		}
	}

	public BufferSizeOptionsTab() {
		super("Buffer size Options", Icon.BLOCK);
	}

	@Override
	protected void createOptionWidgets(Composite composite) {

		ILaunchWidget<?> w = new LaunchWidgetSpinnerInteger(BUFFER_SIZE_DEFAULT, 1, Integer.MAX_VALUE, 1, 512,
				composite);
		w.setText("Default buffer size");
		w.setTooltip("Select a defualt buffer size configuration");
		addWidget(w);

		addWidget(new BufferSizeTableWidget(composite));

	}

	@Override
	protected void updateComposableOptions() {
	}

}
