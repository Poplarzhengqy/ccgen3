// Copyright 2005 Konrad Twardowski
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.makagiga.editors.todo;

import static org.makagiga.commons.UI._;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.makagiga.MainWindow;
import org.makagiga.Tabs;
import org.makagiga.chart.ChartModel;
import org.makagiga.chart.ChartPainter;
import org.makagiga.commons.MAction;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDataTransfer;
import org.makagiga.commons.MDate;
import org.makagiga.commons.MProperties;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.color.MColorIcon;
import org.makagiga.commons.preview.DefaultPreview;
import org.makagiga.commons.preview.Preview;
import org.makagiga.commons.swing.Input;
import org.makagiga.commons.swing.MStatusBar;
import org.makagiga.desktop.Widget;
import org.makagiga.editors.Editor;
import org.makagiga.editors.EditorPlugin;
import org.makagiga.fs.MetaInfo;
import org.makagiga.plugins.PluginException;
import org.makagiga.todo.Priority;
import org.makagiga.todo.Task;
import org.makagiga.todo.TaskList;
import org.makagiga.todo.TaskModel;
import org.makagiga.todo.TaskSelection;
import org.makagiga.tree.Tree;

/** The "Todo" editor plugin. */
public final class TodoEditorPlugin extends EditorPlugin {

	// public
	
	/**
	 * @since 2.4
	 */
	public TodoEditorPlugin() {
		setEncryptionSupported(true);
	}
	
	/**
	 * @since 4.4
	 */
	public static TaskList getPreloadedTaskList(final MetaInfo metaInfo) {
		for  (Editor<?> tab : Tabs.getInstance()) {
			if ((tab.getMetaInfo() == metaInfo) && (tab instanceof TodoEditor) && !tab.isLoading()) {
				TodoEditorCore core = (TodoEditorCore)tab.getCore();
				TaskList list = new TaskList(core.getModel().getRowCount());
				for (Task task : core.getModel())
					list.add(task.copy());
			
				return list;
			}
		}
		
		return null;
	}

	@Override
	public void importData(final Object source, final DataFlavor flavor, final Map<DataFlavor, Object> cachedData, final File toFile) throws Exception {
		@SuppressWarnings("unchecked")
		List<Task> taskList = (List<Task>)MDataTransfer.getData(source, flavor);

		if (taskList == null)
			throw new IOException();

		TaskModel taskModel = new TaskModel();
		taskModel.setEventsEnabled(false);
		for (Task i : taskList)
			taskModel.addRow(i);
		taskModel.saveToXML(toFile);
	}

	@Override
	public boolean isDataFlavorSupported(final Object source, final DataFlavor flavor, final Map<DataFlavor, Object> cachedData) {
		return flavor.equals(TaskSelection.TASK_FLAVOR);
	}

	@Override
	public Editor<?> create() {
		return new TodoEditor();
	}
	
	/**
	 * @since 3.0
	 */
	public static void newTask(final MetaInfo metaInfo, final MDate date) {
		TodoEditorCore core = getTodoEditor(metaInfo);

		if (core == null)
			return;
			
		String summary = new Input.GetTextBuilder()
			.text("")
			.label(_("Summary"))
			.title(_("Add a New Task for This Day"))
			.icon("ui/newfile")
			.autoCompletion("newtask")
		.exec(core);
		
		if (summary != null)
			core.addTask(summary, Priority.DEFAULT, 0, date.getTime(), MDate.currentTime(), 0, true);
	}

	@Override
	public void onInit() throws PluginException {
		super.onInit();

		setFileTypes(
			new FileType("mgtodo")
		);

		setImportTypes(
			new FileType("mgtodo", getName(), "application/x-makagiga-todo")
		);
		
		setExportTypes(
			new FileType("mgtodo", getName()),
			new FileType("html", "HTML"),
			new FileType("opml", "OPML"),
			new FileType("xhtml", "XHTML"),
			new FileType("ics", "iCalendar Tasks"),
			new FileType("csv", "Comma Separated Values (CSV)"),
			new FileType("txt", _("Plain Text")),
			new FileType("xml", "XML"),
			new FileType("vcs", "vCalendar Tasks")
		);

		DefaultPreview.getInstance().addHandler("mgtodo", new Preview(false) {
// TODO: getPreloadedTaskList, need EDT
			@Override
			public Image getImage(final File file, final int width, final MProperties properties) throws Exception {
				TaskModel taskModel = new TaskModel();
				taskModel.loadFromXML(file, false);
				StringBuilder text = new StringBuilder();
				if (taskModel.isEmpty()) {
					text
						.append('<')
						.append(_("Empty"))
						.append('>');
				}
				else {
					for (Task i : taskModel) {
						if (!i.getSummary().isEmpty()) {
							text
								.append(i.isDone() ? "[X] " : "[ ] ")
								.append(i.getSummary())
								.append('\n');
						}
					}
				}
				
				if (text.length() > 0) {
					BufferedImage image = DefaultPreview.getTextImage(text.toString(), width);
					
					// paint translucent stats chart over the text
					if (!taskModel.isEmpty()) {
						Graphics2D g = image.createGraphics();
						g.setComposite(AlphaComposite.SrcOver.derive(0.2f));
						ChartModel chartModel = createChartModel(taskModel);
						ChartPainter<ChartModel> chartPainter = new ChartPainter<>(chartModel);
						chartPainter.chartSize.set((int)(width / 1.2f));
						chartPainter.shadowVisible.no();
						chartPainter.textVisible.no();
						chartPainter.paint(g, width, width, null, null);
						g.dispose();
					}
					
					return image;
				}
				
				return null;
			}
		} );
	}

	/**
	 * @since 3.0
	 */
	public static void showTask(final MetaInfo metaInfo, final Task task) {
		// todo widget
		if (metaInfo.getFS() == null) {
			Object owner = metaInfo.getProperty(MetaInfo.OWNER, null);
			if (owner instanceof Widget) {
				Widget widget = (Widget)owner;
				// HACK: task "parameter" handled by the To-Do Widget plugin
				widget.putClientProperty(task.getClass().getName(), task);

				MainWindow.getInstance().showWidget(widget, false);
			}
		}
		// regular todo file
		else {
			TodoEditorCore core = getTodoEditor(metaInfo);
			if (core != null)
				core.selectTask(task);
		}
	}
	
	// private
	
	private static TodoEditorCore getTodoEditor(final MetaInfo metaInfo) {
		if (!metaInfo.getFile().exists()) {
			MStatusBar.warning(_("File does not exist: {0}", metaInfo));

			return null;
		}

		if (Tree.getInstance().open(metaInfo)) {
			final Tabs tabs = Tabs.getInstance();
			Editor<?> editor = tabs.getTabAt(metaInfo);
			if (editor instanceof TodoEditor) {
				tabs.setSelectedComponent(editor);
					
				return (TodoEditorCore)editor.getCore();
			}
		}

		return null;
	}

	// package

	static ChartModel createChartModel(final TaskModel taskModel) {
		ChartModel chartModel = new ChartModel();
		if (taskModel.isEmpty()) {
			chartModel.addEmptyInfo(Color.BLACK);
		}
		else {
			chartModel.setFormat("${text} (${percent-float})");

			long done = 0;
			long total = taskModel.getRowCount();

			long percentDone = 0;
			long totalPercent = total * 100;
			for (Task i : taskModel) {
				if (i.isDone())
					done++;
				percentDone += i.getComplete();
			}

			if (percentDone > 0) {
				chartModel.addItem(
					_("Done - {0}", done),
					percentDone,
					new Color(0x77b753), // ~green
					"ui/complete"
				);
			}

			totalPercent -= percentDone;
			if (totalPercent > 0) {
				chartModel.addItem(
					_("To-Do - {0}", total - done),
					totalPercent,
					MColor.SKY_BLUE
				);
			}
		}

		return chartModel;
	}

	// public classes

	/**
	 * @since 4.0
	 */
	public static class ShowTaskAction extends MAction {

		// private

		private final MetaInfo metaInfo;
		private final Task task;

		// public

		public ShowTaskAction(final MetaInfo metaInfo, final Task task) {
			this(metaInfo, task, true);
		}

		public ShowTaskAction(final MetaInfo metaInfo, final Task task, final boolean metaInfoToolTipText) {
			super(task.formatSummary(50));
			setHTMLEnabled(false);
			if (metaInfoToolTipText)
				setHelpText(UI.makeHTML(TK.escapeXML(metaInfo.toString())));
			this.metaInfo = metaInfo;
			this.task = task;

			Color color = task.getColor();
			if (color != null)
				setIcon(MColorIcon.smallRectangle(color));
		}

		@Override
		public void onAction() {
			MainWindow.getInstance().restore();
			TodoEditorPlugin.showTask(metaInfo, task);
		}

	}

}
