// Copyright 2007 Konrad Twardowski
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

package org.makagiga.todo;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SortOrder;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

import org.makagiga.commons.BooleanProperty;
import org.makagiga.commons.ClipboardException;
import org.makagiga.commons.Config;
import org.makagiga.commons.Kiosk;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MArrayList;
import org.makagiga.commons.MCalendar;
import org.makagiga.commons.MClipboard;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDataAction;
import org.makagiga.commons.MDataTransfer;
import org.makagiga.commons.MDate;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.StringList;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.annotation.InvokedFromConstructor;
import org.makagiga.commons.annotation.Obsolete;
import org.makagiga.commons.category.CategoryListEditor;
import org.makagiga.commons.category.CategoryListRenderer;
import org.makagiga.commons.category.CategoryManager;
import org.makagiga.commons.icons.ShapeIcon;
import org.makagiga.commons.mv.BooleanRenderer;
import org.makagiga.commons.mv.MRenderer;
import org.makagiga.commons.mv.MV;
import org.makagiga.commons.swing.ActionGroup;
import org.makagiga.commons.swing.Input;
import org.makagiga.commons.swing.MButton;
import org.makagiga.commons.swing.MCellTip;
import org.makagiga.commons.swing.MCheckBox;
import org.makagiga.commons.swing.MComboBox;
import org.makagiga.commons.swing.MComponent;
import org.makagiga.commons.swing.MDateSpinner;
import org.makagiga.commons.swing.MDialog;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MList;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MStatusBar;
import org.makagiga.commons.swing.MTable;
import org.makagiga.commons.swing.MTextField;
import org.makagiga.commons.swing.MUndoManager;
import org.makagiga.commons.swing.event.MMenuAdapter;

/**
 * @since 2.0
 */
public class TaskTable extends MTable<TaskModel> {

	// public

	/**
	 * @since 3.0
	 */
	public static final MActionInfo DELETE_COMPLETED = new MActionInfo(_("Delete Completed"), "ui/complete", VK_D, CTRL_MASK | SHIFT_MASK);

	/**
	 * @since 3.0
	 */
	public static final MActionInfo DELETE_SELECTED = new MActionInfo(_("Delete Selected"), "ui/delete", VK_DELETE);

	// private

	private ActionGroup editActionGroup = new ActionGroup();
	private boolean showCompletedTasks = true;
	private boolean toolTipTextEnabled;
	private CategoryListRenderer<String> categoryListRenderer;
	private ChangeListener categoryChangeListener;
	private static Dimension lastNewTaskDialogSize;
	private int fontSize = -1;
	private ListSelectionListener listSelectionListener;
	private final MUndoManager undoManager;
	private final Sorter<TaskModel> sorter;
	private TableCellEditor categoryEditor;
	private TableCellEditor circleEditor;
	private TableCellEditor completeEditor;
	private TableCellEditor dateTimeEditor;
	private TableCellEditor doneEditor;
	private TableCellEditor priorityEditor;
	private TableCellEditor summaryEditor;
	
	// public
	
	public TaskTable() {
		super(new TaskModel());
		getModel().repeatRulesEnabled = true;
		getColumnManager().setDefaultColumnOrder(
			Column.CIRCLE,
			Column.DONE,
			Column.SUMMARY,
			Column.DATE_TIME,
			Column.PRIORITY,
			Column.CATEGORY
		);
		getColumnManager().updateProperties();

		setAutoResizeMode(AUTO_RESIZE_OFF);
		setSurrendersFocusOnKeystroke(true);

		// setup editors and renderers

		// done column
		
		TableColumn column = getTaskColumn(Column.DONE);
		column.setCellRenderer(new BooleanRenderer());
		
		// summary column
		
		column = getTaskColumn(Column.SUMMARY);
		column.setCellRenderer(new SummaryRenderer());
		
		// priority column
		
		column = getTaskColumn(Column.PRIORITY);
		column.setCellRenderer(new PriorityRenderer());
		
		// complete column

		column = getTaskColumn(Column.COMPLETE);
		column.setCellRenderer(new CompleteRenderer());
		
		// date/time columns

		DateTimeRenderer dateTimeRenderer = new DateTimeRenderer();
		column = getTaskColumn(Column.DATE_TIME);
		column.setCellRenderer(dateTimeRenderer);
		
		column = getTaskColumn(Column.START_DATE_TIME);
		column.setCellRenderer(dateTimeRenderer);
		
		column = getTaskColumn(Column.COMPLETE_DATE_TIME);
		// no cell editor - read only
		column.setCellRenderer(dateTimeRenderer);

		// category column

		column = getTaskColumn(Column.CATEGORY);
		categoryListRenderer = new CategoryListRenderer<>(CategoryManager.getSharedInstance());
		column.setCellRenderer(categoryListRenderer);

		// circle column
		
		column = getTaskColumn(Column.CIRCLE);
		column.setCellRenderer(new CircleRenderer());

		// init sorter and filter
		sorter = new Sorter<>(getModel());
		setRowSorter(sorter);
		
		initDragAndDrop();

		// repaint rows on category properties change
		categoryChangeListener = new CategoryManager.AutoRepaint(this);
		CategoryManager.getSharedInstance().addChangeListener(categoryChangeListener);

		undoManager = new MUndoManager(this, MUndoManager.NO_LIMIT) {
			@Override
			public void updateUserActions() {
				JComponent owner = this.getOwner();
				if (owner instanceof TaskTable)
					TaskTable.class.cast(owner).getEditActionGroup().update();
			}
		};
		getModel().addUndoableEditListener(undoManager);

		editActionGroup = new EditActionGroup(this);
		editActionGroup.add("undo", undoManager.getUndoAction())
			.setShowTextInToolBar(true);
		editActionGroup.add("redo", undoManager.getRedoAction());
		editActionGroup.addSeparator();
		editActionGroup.add("cut", new CutAction(this));
		editActionGroup.add("copy", new CopyAction(this))
			.setShowTextInToolBar(true);
		editActionGroup.add("paste", new PasteAction(this))
			.setShowTextInToolBar(true);
		editActionGroup.addSeparator();
		editActionGroup.add("add", new AddAction(this))
			.setVisibleInToolBar(false);
		editActionGroup.add("delete-completed", new DeleteCompletedAction(this))
			.setShowTextInToolBar(true);
		editActionGroup.add("delete", new DeleteAction(this))
			.setVisibleInToolBar(false);
		editActionGroup.addSeparator();
		editActionGroup.add("select-all", new SelectAllAction(this))
			.setVisibleInToolBar(false);
		editActionGroup.add("unselect-all", new UnselectAllAction(this))
			.setVisibleInToolBar(false);

		// bind keyboard shortcuts
		editActionGroup.connect("undo", this, WHEN_FOCUSED);
		editActionGroup.connect("redo", this, WHEN_FOCUSED);
		editActionGroup.connect("add", this, WHEN_FOCUSED);
		editActionGroup.connect("delete-completed", this, WHEN_FOCUSED);
		editActionGroup.connect("unselect-all", this, WHEN_FOCUSED);

		listSelectionListener = new ListSelectionListener() {
			@Override
			public void valueChanged(final ListSelectionEvent e) {
				editActionGroup.update();
			}
		};
		getSelectionModel().addListSelectionListener(listSelectionListener);
	}

	/**
	 * @since 4.4
	 */
	public boolean addTask(final Window owner, final TaskPropertiesPanel panel) {
		MDialog dialog = new MDialog(owner, _("Add Task"), ShapeIcon.PLUS, MDialog.STANDARD_DIALOG | MDialog.FORCE_STANDARD_BORDER);
		dialog.addCenter(panel);
		if (lastNewTaskDialogSize != null) {
			Dimension pref = dialog.getPreferredSize();
			lastNewTaskDialogSize.width = Math.max(pref.width, lastNewTaskDialogSize.width);
			lastNewTaskDialogSize.height = Math.max(pref.height, lastNewTaskDialogSize.height);
			dialog.setSize(lastNewTaskDialogSize);
		}
		else
			dialog.pack();
		
		boolean ok = dialog.exec(panel);
		lastNewTaskDialogSize = dialog.getSize();
		if (ok) {
			panel.apply();
			addTask(panel.getTask(), true);
		}
		
		return ok;
	}

	/**
	 * (MODEL) Adds a new summary to the todo list.
	 * @param text A summary text
	 * @param select If @c true the new task will be selected
	 */
	public Task addTask(final String text, final boolean select) {
		return addTask(text, Priority.DEFAULT, 0, 0, MDate.currentTime(), 0, select);
	}

	/**
	 * (MODEL)
	 */
	@Obsolete
	public void addTask(final Task task) {
		getModel().addRow(task);
	}

	/**
	 * (MODEL/VIEW)
	 *
	 * @since 4.4
	 */
	public void addTask(final Task task, final boolean select) {
		getModel().addRow(task);

		if (select)
			selectRow(convertRowIndexToView(getModel().getRowCount() - 1));
	}

	/**
	 * (MODEL) Adds a new item to the todo list.
	 * @param summary A summary text
	 * @param priority A priority
	 * @param complete A complete status (0-100)
	 * @param dateTime A date/time value
	 * @param startDateTime A start date/time value
	 * @param select If @c true the new task will be selected
	 */
	public Task addTask(
		final String summary,
		final Priority priority,
		final int complete,
		final long dateTime,
		final long startDateTime,
		final long completeDateTime,
		final boolean select
	) {
		Task task = new Task(summary, priority, complete, dateTime, startDateTime, completeDateTime);
		addTask(task, select);
		
		return task;
	}

	/**
	 * (VIEW) Copies selected tasks to the clipboard.
	 * @param cut If @c true the selected tasks will be deleted after copy
	 * @return @c true if successful; otherwise @c false
	 */
	public boolean copy(final boolean cut) {
		TaskSelection selection = getTaskSelection();
		
		if (selection == null)
			return false;
		
		try {
			MClipboard.setContents(selection);

			if (cut)
				deleteSelected();

			return true;
		}
		catch (ClipboardException exception) {
			MLogger.exception(exception);
			
			return false;
		}
	}

	/**
	 * @since 4.0
	 */
	public MMenu createSetValueMenu(final int[] selectedViewRows, final boolean lazyInitialization) {
		MMenu menu = new MMenu(_("Task Properties"), MActionInfo.PROPERTIES.getIconName());
		menu.setPopupMenuVerticalAlignment(MMenu.CENTER);
		if (getModel().isLocked()) {
			menu.setEnabled(false);
		}
		else if (selectedViewRows.length > 0) {
			if (lazyInitialization) {
				menu.addMenuListener(new MMenuAdapter(true) {
					@Override
					protected void onSelect(final MMenu menu) {
						menu.removeAll();
						TaskTable.this.createSetValueMenuItems(menu, selectedViewRows);
					}
				} );
			}
			else {
				createSetValueMenuItems(menu, selectedViewRows);
			}
		}
		else {
			menu.addTitle(_("No selection"));
		}

		return menu;
	}
	
	/**
	 * @since 4.0
	 */
	public void createSetValueMenuItems(final MMenu menu, final int[] selectedViewRows) {
		Task singleTask =
			(selectedViewRows.length == 1)
			? getModel().getRowAt(convertRowIndexToModel(selectedViewRows[0]))
			: null;

		if (singleTask == null) {
			int i = selectedViewRows.length;
			if (i == getModel().getRowCount())
				i = SELECTION_TEXT_ALL;
			menu.addTitle(getSelectionText(i));
		}

		boolean done = (singleTask != null) && singleTask.isDone();
		SetValueAction<Boolean> doneAction = new SetValueAction<>(
			this,
			selectedViewRows,
			new MActionInfo(Column.DONE.toString()),
			Column.DONE,
			!done // !done = new value
		);
		doneAction.setEnabled(singleTask != null);
		menu.addCheckBox(doneAction, done);

		MMenu completeMenu = new MMenu(Column.COMPLETE.toString());
		completeMenu.setPopupMenuVerticalAlignment(MMenu.CENTER);
		for (int i = 0; i < 110; i += 10) { // 0% to 100%
			completeMenu.addRadioButton(
				new SetCompleteAction(this, selectedViewRows, i),
				(singleTask != null) && (i == singleTask.getComplete())
			);
		}
		menu.add(completeMenu);

/* TODO: MMenuElement priorityPanel = new MMenuElement();
		MMenuElement priorityPanel = new MMenuElement();
		Insets margin = completeMenu.getInsets();
		if (margin != null)
			priorityPanel.setMargin(margin.top, margin.left, margin.bottom, margin.right);
		priorityPanel.setHBoxLayout();
		priorityPanel.setOpaque(false);
		priorityPanel.add(new MLabel(_("Priority:")));
		ButtonGroup priorityGroup = new ButtonGroup();
		for (Priority i : Priority.values()) {
			MToggleButton b = new MToggleButton(new SetPriorityAction(this, selectedViewRows, i));
			b.setMargin(UI.createInsets(0));
			b.setSelected((singleTask != null) && (i == singleTask.getPriority()));
			b.setToolTipText(b.getText());
			b.setText(null);
			priorityGroup.add(b);
			priorityPanel.addGap();
			priorityPanel.add(b);
		}
		menu.add(priorityPanel);
*/

		MMenu priorityMenu = new MMenu(Column.PRIORITY.toString());
		priorityMenu.setPopupMenuVerticalAlignment(MMenu.CENTER);
		for (Priority i : Priority.valuesReversed()) {
			priorityMenu.addDefaultRadioButton(
				new SetPriorityAction(this, selectedViewRows, i),
				(singleTask != null) && (i == singleTask.getPriority())
			);
		}
		menu.add(priorityMenu);
		
		menu.add(new SetColorAction(this, selectedViewRows));

		menu.addTitle(Column.DATE_TIME.toString());

		menu.add(new SetDateTimeAction(this, selectedViewRows, new MActionInfo(TaskState.TODAY.toString(), "ui/today"), TaskState.TODAY));
		menu.add(new SetDateTimeAction(this, selectedViewRows, new MActionInfo(TaskState.TOMORROW.toString()), TaskState.TOMORROW));
		menu.add(new SetDateTimeAction(this, selectedViewRows, MActionInfo.SET_DATE_TIME, TaskState.LATER));
		menu.add(new SetDateTimeAction(this, selectedViewRows, MActionInfo.NO_DATE_TIME, TaskState.UNKNOWN));

		MMenu nextMenu = new MMenu(_("Set Next Date/Time"));
		nextMenu.setPopupMenuVerticalAlignment(MMenu.CENTER);
		nextMenu.add(new SetDateTimeAction(this, selectedViewRows, _("Next Hour"), MCalendar.HOUR_OF_DAY, 1));
		nextMenu.add(new SetDateTimeAction(this, selectedViewRows, _("Next Day"), MCalendar.DAY_OF_MONTH, 1));
		nextMenu.addSeparator();
		nextMenu.add(new SetDateTimeAction(this, selectedViewRows, _("Next Week"), MCalendar.DAY_OF_MONTH, 7));
		nextMenu.add(new SetDateTimeAction(this, selectedViewRows, _("Next Month"), MCalendar.MONTH, 1));
		nextMenu.add(new SetDateTimeAction(this, selectedViewRows, _("Next Year"), MCalendar.YEAR, 1));
		menu.add(nextMenu);

		menu.addSeparator();

		SetNextDateAction setNextDateAction = new SetNextDateAction(this, selectedViewRows);
		if ((singleTask != null) && !singleTask.getDateTime().isValid())
			setNextDateAction.setEnabled(false);
		menu.add(setNextDateAction);
		
		menu.add(new SetRepeatRuleAction(this, selectedViewRows));
	}

	/**
	 * (MODEL) Deletes all the completed items.
	 */
	public void deleteCompletedItems() {
		doneEdit();
		int selectedRow = getSelectedRow();
		try {
			for (int i = getModel().getRowCount() - 1; i >= 0; --i) {
				if (getTaskAt(i).isDone())
					removeRow(i);
			}
		}
		catch (ArrayIndexOutOfBoundsException exception) {
			MLogger.exception(exception);
		}
		selectRow(selectedRow);
	}

	/**
	 * (MODEL)
	 *
	 * @since 3.0
	 */
	public boolean deleteCompletedItems(final Window owner) {
		int completedItems = getCompletedItemCount();
		if (completedItems == 0) {
			MStatusBar.warning(_("No completed tasks"));

			return false;
		}

		StringList selectedText = new StringList(completedItems);
		for (Task i : getTaskList()) {
			if (i.isDone())
				selectedText.add(i.getSummary());
		}

		if (!canCancelEdit(owner, _("Delete Completed Tasks")))
			return false;

		if (new MMessage.Builder()
			.icon("ui/complete")
			.ok(_("Delete Completed Tasks"), "ui/complete")
			.html(_("Are you sure you want to delete <b>all completed</b> tasks?<br><br>Completed Tasks: {0}", completedItems))
			.list(selectedText)
			.exec(owner)
		) {
			deleteCompletedItems();

			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * (VIEW)
	 *
	 * @since 3.0
	 */
	public boolean deleteSelectedItems(final Window owner) {
		return deleteSelected(
			owner,
			UI.makeHTML(
				_("Are you sure you want to delete <b>all selected</b> tasks?") +
				"<br><br>" +
				TK.escapeXML(getSelectionText())
			),
			Column.SUMMARY.ordinal()
		);
	}

	/**
	 * @inheritDoc
	 * 
	 * @since 4.0
	 */
	@Override
	public void dispose() {
		if (categoryChangeListener != null) {
			CategoryManager.getSharedInstance().removeChangeListener(categoryChangeListener);
			categoryChangeListener = null;
		}
		
		categoryListRenderer = null;

		if (listSelectionListener != null) {
			ListSelectionModel model = getSelectionModel();
			if (model != null)
				model.removeListSelectionListener(listSelectionListener);
			listSelectionListener = null;
		}

		TaskModel model = getModel();
		if (model != null)
			model.removeUndoableEditListener(undoManager);

		editActionGroup = TK.dispose(editActionGroup);

		super.dispose();
	}

	/**
	 * (VIEW)
	 *
	 * @since 4.2
	 */
	public void filterTasks(final Filter<TaskModel> filter) {
		getTaskSorter().setRowFilter(filter);
	}

	/**
	 * (VIEW)
	 */
	public void filterTasks(final String text) {
		filterTasks(TK.isEmpty(text) ? null : new Filter<TaskModel>(text)); // null - show all
	}

	@Override
	public TableCellEditor getCellEditor(final int row, final int column) {
		Column c = Column.values()[convertColumnIndexToModel(column)];
		switch (c) {
			case CATEGORY:
				if (categoryEditor == null)
					categoryEditor = new CategoryListEditor<>(CategoryManager.getSharedInstance());

				return categoryEditor;

			case CIRCLE:
				if (circleEditor == null)
					circleEditor = new CircleEditor();

				return circleEditor;

			case COMPLETE:
				if (completeEditor == null)
					completeEditor = new DefaultCellEditor(new CompleteEditor());

				return completeEditor;

			case DATE_TIME:
			case START_DATE_TIME:
				if (dateTimeEditor == null)
					dateTimeEditor = new DateTimeEditor();

				return dateTimeEditor;

			case DONE:
				if (doneEditor == null)
					doneEditor = new DefaultCellEditor(new BooleanRenderer());

				return doneEditor;

			case PRIORITY:
				if (priorityEditor == null)
					priorityEditor = new DefaultCellEditor(new PriorityEditor());

				return priorityEditor;

			case SUMMARY:
				if (summaryEditor == null)
					summaryEditor = new SummaryEditor();

				return summaryEditor;

			default:
				return super.getCellEditor(row, column);
		}
	}

	/**
	 * (MODEL) Returns a number of the completed items.
	 */
	public int getCompletedItemCount() {
		int result = 0;
		for (Task i : getTaskList()) {
			if (i.isDone())
				result++;
		}

		return result;
	}

	/**
	 * @since 3.2
	 */
	public ActionGroup getEditActionGroup() { return editActionGroup; }

	/**
	 * @since 4.6
	 */
	public List<Task> getSelectedTasks() {
		int[] selectedRows = getSelectedRows();

		if (selectedRows.length == 0)
			return Collections.emptyList();

		MArrayList<Task> result = new MArrayList<>(selectedRows.length);
		for (int i : selectedRows)
			result.add(getTaskAt(convertRowIndexToModel(i)));

		return result;
	}

	/**
	 * @since 4.2
	 */
	public boolean getShowCompletedTasks() { return showCompletedTasks; }

	/**
	 * @since 4.2
	 */
	public void setShowCompletedTasks(final boolean value) { showCompletedTasks = value; }

	/**
	 * (MODEL)
	 */
	public Task getTaskAt(final int row) {
		return getModel().getRowAt(row);
	}

	/**
	 * (MODEL)
	 */
	@InvokedFromConstructor
	public TableColumn getTaskColumn(final Column index) {
		int i = index.toView(this);
		
		if (i == -1)
			return null;
		
		return getColumnModel().getColumn(i);
	}
	
	/**
	 * (MODEL)
	 */
	public List<Task> getTaskList() {
		return getModel().getRows();
	}
	
	/**
	 * (VIEW)
	 */
	public TaskSelection getTaskSelection() {
		List<Task> selection = getSelectedTasks();
		
		if (selection.isEmpty())
			return null;
	
		TaskSelection taskSelection = new TaskSelection();
		for (Task i : selection)
			taskSelection.addTaskCopy(i);

		return taskSelection;
	}

	public Sorter<TaskModel> getTaskSorter() { return sorter; }
	
	@Override
	public Point getToolTipLocation(final MouseEvent e) {
		if (!getToolTipTextEnabled())
			return null;
	
		int row = rowAtPoint(e.getPoint());
		
		if (row == -1)
			return null;
		
		Rectangle r = getCellRect(row, 0, true);
		if (e.getX() > getWidth() / 3)
			r.x = 10;
		else
			r.x = getWidth() / 3;
		r.y += r.height - 4;
		
		return r.getLocation();
	}

	@Override
	public String getToolTipText(final MouseEvent e) {
		if (!getToolTipTextEnabled())
			return null;
		
		int row = rowAtPoint(e.getPoint());
		
		if (row == -1)
			return null;

		Task task = getTaskAt(convertRowIndexToModel(row));
		
		return task.getToolTipText();
	}
	
	/**
	 * @since 4.2
	 */
	public boolean getToolTipTextEnabled() { return toolTipTextEnabled; }

	/**
	 * @since 4.2
	 */
	public void setToolTipTextEnabled(final boolean value) { toolTipTextEnabled = value; }

	@Override
	public boolean isCellTipEnabled(final int row, final int column) {
		if (column == Column.COMPLETE.toView(this)) {
			TableColumn tableColumn = getColumnModel().getColumn(column);
			
			return tableColumn.getWidth() < 30;
		}

		return super.isCellTipEnabled(row, column);
	}

	/**
	 * (MODEL) Pastes tasks from the clipboard.
	 *
	 * @return {@code true} if successful; otherwise {@code false}
	 */
	public boolean paste() {
		return MDataTransfer.importFromClipboard(this);
	}
	
	/**
	 * Overriden to hide cell tip.
	 *
	 * (MODEL)
	 */
	@Override
	public Component prepareEditor(final TableCellEditor editor, final int row, final int column) {
		MCellTip.getInstance().setVisible(false);
		
		return super.prepareEditor(editor, row, column);
	}
	
	public void read(final Config global, final Config local, final Config.GlobalEntry globalEntry) {
		getColumnManager().readConfig(global, local, globalEntry);
		
		// font size
		fontSize = global.readInt(globalEntry.getGlobalEntry("Font.size"), -1);
		if (fontSize != -1) {
			fontSize = TK.limit(fontSize, 8/*TextUtils.MIN_ZOOM*/, 128/*TextUtils.MAX_ZOOM*/);
			setStyle("font-size: " + fontSize);
			updateFontSize(fontSize);
		}
		
		showCompletedTasks = (local != null) && local.read(globalEntry.getGlobalEntry("showCompletedTasks"), true);
		setGridVisible(global.read(globalEntry.getGlobalEntry("showGrid"), false));
		setToolTipTextEnabled(global.read(globalEntry.getGlobalEntry("showToolTips"), false));
	}

	/**
	 * (VIEW)
	 */
	public int selectTask(final Task task) {
		int modelRowCount = getModel().getRowCount();
		for (int modelRow = 0; modelRow < modelRowCount; modelRow++) {
			if (getTaskAt(modelRow).equals(task)) {
				int index = convertRowIndexToView(modelRow);

				if (index == -1)
					return -1;

				selectRow(index);

				return index;
			}
		}

		return -1;
	}
	
	@Override
	public void setRowHeight(final Font font) {
		if (categoryListRenderer != null)
			CategoryListRenderer.clearFontCache(categoryListRenderer.getView());

		setRowHeight(font.getSize() + 16);
	}

	public void updateFontSize(final int value) {
		fontSize = value;
		if (fontSize != -1)
			setRowHeight(UI.getFont(this));
	}

	public void write(final Config global, final Config local, final Config.GlobalEntry globalEntry) {
		getColumnManager().writeConfig(global, local, globalEntry);
		
		// font size
		writeFontSize(global, globalEntry);

		if (local != null)
			local.write(globalEntry.getGlobalEntry("showCompletedTasks"), showCompletedTasks);
		global.write(globalEntry.getGlobalEntry("showGrid"), isGridVisible());
		global.write(globalEntry.getGlobalEntry("showToolTips"), getToolTipTextEnabled());
	}

	/**
	 * @since 3.0
	 */
	public void writeFontSize(final Config config, final Config.GlobalEntry globalEntry) {
		if (fontSize != -1)
			config.write(globalEntry.getGlobalEntry("Font.size"), fontSize);
	}
	
	// protected
	
	@Override
	protected boolean onEnterPress(final KeyEvent e) {
		if (isEditing()) {
			doneEdit();
		}
		else {
			editCellAt(getSelectedRow(), getSelectedColumn());
			Component editor = getEditorComponent();
			// auto toggle value
			if (editor instanceof MCheckBox) {
				MCheckBox cb = (MCheckBox)editor;
				cb.setSelected(!cb.isSelected());
				doneEdit();
				requestFocusInWindow();
			}
			// auto show list
			else if (editor instanceof MComboBox) {
				MComboBox.class.cast(editor).setPopupVisible(true);
			}
		}

		return true;
	}

	@Override
	protected boolean processKeyBinding(final KeyStroke ks, final KeyEvent e, final int condition, final boolean pressed) {
		// override default Cut/Copy/Paste actions
		if (pressed) {
			Object actionKey = getInputMap(condition).get(ks);
			if ("copy".equals(actionKey) || "cut".equals(actionKey) || "paste".equals(actionKey)) {
				editActionGroup.fire(actionKey.toString(), this);
				
				return true;
			}
		}
		
		return super.processKeyBinding(ks, e, condition, pressed);
	}

	private void initDragAndDrop() {
		setDragEnabled(Kiosk.actionDragDrop.get());
		setDropMode(DropMode.INSERT_ROWS);
		setTransferHandler(new TransferHandler() {
			@Override
			public boolean canImport(final TransferHandler.TransferSupport support) {
				boolean taskFlavor = support.isDataFlavorSupported(TaskSelection.TASK_FLAVOR);

// TODO: 2.0: allow drag and drop sorted rows
				if (taskFlavor && support.isDrop() && (TaskTable.this.getSortOrder() != SortOrder.UNSORTED))
					return false;

				if (taskFlavor) {
					if (support.isDrop())
						support.setShowDropLocation(true);

					return true;
				}

				if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
					if (support.isDrop())
						support.setShowDropLocation(false);

					return true;
				}

				return false;
			}
			@Override
			public int getSourceActions(final JComponent c) {
				return MOVE;
			}
			@Override
			@SuppressWarnings("unchecked")
			public boolean importData(final TransferHandler.TransferSupport support) {
				int oldCount = TaskTable.this.getModel().getRowCount();
				List<Task> taskList = null;
				String[] lines = null;
				Transferable transferable = MDataTransfer.getTransferable(support);
				try {
					Object data =
						transferable.isDataFlavorSupported(TaskSelection.TASK_FLAVOR)
						? transferable.getTransferData(TaskSelection.TASK_FLAVOR)
						: null;
					if (data instanceof List<?>) {
						taskList = (List<Task>)data;

						if (taskList.isEmpty())
							return false;
					}
					else {
						data = transferable.getTransferData(DataFlavor.stringFlavor);
						if (data instanceof String) {
							String text = (String)data;
							if (!text.isEmpty())
								lines = text.split("\n"); // do not use TK.fastSplit
						}
					}
				}
				catch (IOException | UnsupportedFlavorException exception) {
					MLogger.exception(exception);

					return false;
				}

				// task list
				if (taskList != null) {
					if (support.isDrop()) {
						MTable.DropLocation dropLocation = (MTable.DropLocation)support.getDropLocation();
						int row = dropLocation.getRow();

						if (row == -1)
							return false;

						for (Task i : taskList) {
							if (row >= TaskTable.this.getRowCount())
								TaskTable.this.getModel().addRow(i);
							else
								TaskTable.this.getModel().insertRow(TaskTable.this.convertRowIndexToModel(row), i);
							row++;
						}
						TaskTable.this.deleteSelected();
					}
					else {
						for (Task i : taskList)
							TaskTable.this.addTask(i.copy()); // paste deep copy

						TaskTable.this.selectFrom(oldCount);
					}
				}
				// plain text
				else if (lines != null) {
					return TaskTable.this.pastePlainText(oldCount, lines);
				}
				
				return true;
			}
			@Override
			protected Transferable createTransferable(final JComponent c) {
				TaskSelection selection = TaskTable.this.getTaskSelection();
				
				int i = selection.getData().size();
				if (i == TaskTable.this.getModel().getRowCount())
					i = SELECTION_TEXT_ALL;
				MLabel label = new MLabel(TaskTable.getSelectionText(i));
				label.setSize(label.getPreferredSize());
				setDragImage(MComponent.createScreenshot(label, null));
				setDragImageOffset(new Point(-16, 0));
	
				return selection;
			}
		} );
	}

	private void pasteLines(final int oldCount, final StringList nonEmptyLines) {
		for (String i : nonEmptyLines)
			addTask(i.replace('\t', ' '), false);

		selectFrom(oldCount);
	}

	private boolean pastePlainText(final int oldCount, final String[] allLines) {
		StringList nonEmptyLines = new StringList(allLines.length);
		for (String i : allLines) {
				if (!i.trim().isEmpty())
					nonEmptyLines.add(i);
		}

		if (nonEmptyLines.isEmpty())
			return false;

		if (nonEmptyLines.size() == 1) {
			pasteLines(oldCount, nonEmptyLines);

			return true;
		}

		final BooleanProperty singlePaste = new BooleanProperty();
		MDialog dialog = new MDialog(UI.windowFor(this), MActionInfo.PASTE, MDialog.MODAL | MDialog.CANCEL_BUTTON);

		MButton pasteSingle = new MButton(_("Paste as single task"), MActionInfo.PASTE.getIconName()) {
			@Override
			protected void onClick() {
				singlePaste.yes();
				MDialog dialog = (MDialog)this.getWindowAncestor();
				dialog.accept();
			}
		};

		MButton pasteMulti = new MButton(_("Paste as {0} tasks", nonEmptyLines.size()), MActionInfo.PASTE.getIconName()) {
			@Override
			protected void onClick() {
				singlePaste.no();
				MDialog dialog = (MDialog)this.getWindowAncestor();
				dialog.accept();
			}
		};

		MList<String> lineList = new MList<>();
		lineList.setCellRenderer(MRenderer.getSharedInstance());
		lineList.setSingleSelectionMode();
		lineList.addAllItems(nonEmptyLines);

		dialog.getMainPanel().setGroupLayout(true)
			.beginRows()
				.addComponent(pasteSingle)
				.addSeparator()
				.addComponent(pasteMulti)
				.addScrollable(lineList, _("Tasks:"))
			.end();

		dialog.packFixed();
		
		if (!dialog.exec())
			return false;

		if (singlePaste.get()) {
			addTask(TK.toString(allLines, "\n"), true);
		}
		else {
			pasteLines(oldCount, nonEmptyLines);
		}

		return true;
	}

	private void selectFrom(final int index) {
		clearSelection();
		int max = getModel().getRowCount();
		int row;
		for (int i = index; i < max; i++) {
			row = convertRowIndexToView(i);
			if (row != -1)
				addRowSelectionInterval(row, row);
		}
		// scroll to the first selected task
		scrollToRow(convertRowIndexToView(index));
	}

	// package

	void setupCellEditor(final JComponent c) {
		JTextField textField;
		if (c instanceof MDateSpinner)
			textField = MDateSpinner.class.cast(c).getTextField();
		else if (c instanceof MTextField)
			textField = (MTextField)c;
		else
			textField = null;

		if (UI.isMetal()) {
			c.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		}
		else if (UI.isNimbus()) {
			if (textField != null) {
				UI.NimbusOverrides d = new UI.NimbusOverrides();
				d.put("TextField.contentMargins", new Insets(0, 5, 0, 5));
				d.install(textField, true);
			}
		}

		if (textField != null)
			textField.setFont(getFont());
	}

	// public classes

	/**
	 * @since 4.2
	 */
	public static class SetColorAction extends SetValueAction<Color> {

		// public

		public SetColorAction(final TaskTable table, final int[] selectedViewRows) {
			super(table, selectedViewRows, MActionInfo.SET_COLOR);
		}

		@Override
		public void onAction() {
// TODO: popup menu instead of dialog
			Color initialColor = (singleSelection != null) ? singleSelection.getColor() : null;
			if (initialColor == null)
				initialColor = MColor.HOT_ORANGE;
			Color color = Input.getColor(
				getSourceWindow(),
				initialColor,
				null, // title
				MColor.DEFAULT
			);
			
			if (color == null)
				return;
			
			if (color == MColor.DEFAULT)
				color = null;
		
			TaskTable table = get();
			TaskModel model = table.getModel();
			for (int i : table.getSelectedRows()) {
				int modelRow = table.convertRowIndexToModel(i);

				Task task = model.getRowAt(modelRow);
				Task undo = task.copy();
				
				Color oldColor = task.getColor();
				task.setProperty(Task.COLOR_PROPERTY, color);
				
				if (!Objects.equals(oldColor, color))
					model.updateRowsAndUndo(undo, task, modelRow);
			}
		}

	}

	/**
	 * @since 3.8.11
	 */
	public static class SetCompleteAction extends SetValueAction<Integer> {

		// public

		public SetCompleteAction(final TaskTable table, final int[] selectedViewRows, final int value) {
			super(table, selectedViewRows, new MActionInfo(value + " %" /* with Space separator */), Column.COMPLETE, value);
		}

	}

	/**
	 * @since 3.8.11
	 */
	public static class SetDateTimeAction extends SetValueAction<TaskState> {

		// private

		private boolean setTime;
		private final int calendarField;
		private final int calendarFieldChange;
		private MCalendar newValue;

		// public

		/**
		 * @since 4.2
		 */
		public SetDateTimeAction(final TaskTable table, final int[] selectedViewRows, final String name, final int calendarField, final int calendarFieldChange) {
			super(table, selectedViewRows, new MActionInfo(name), Column.DATE_TIME, TaskState.LATER);
			this.calendarField = calendarField;
			this.calendarFieldChange = calendarFieldChange;
		}

		public SetDateTimeAction(final TaskTable table, final int[] selectedViewRows, final MActionInfo info, final TaskState value) {
			super(table, selectedViewRows, info, Column.DATE_TIME, value);
			this.calendarField = Integer.MIN_VALUE;
			this.calendarFieldChange = Integer.MIN_VALUE;
		}

		@Override
		public void onAction() {
			// use convertValue
			if ((calendarField != Integer.MIN_VALUE) && (calendarFieldChange != Integer.MIN_VALUE)) {
				super.onAction();
			
				return;
			}
		
			if (value == TaskState.UNKNOWN) {
				newValue = null;
				super.onAction();

				return;
			}

			if (value == TaskState.TODAY) {
				newValue = MCalendar.now();
				// set time from task
				if ((singleSelection != null) && singleSelection.getDateTime().isValid()) {
					newValue.setTime(MCalendar.of(singleSelection.getDateTime()));
				}
				else {
					if (newValue.getHour() < 23) // do not set 00:mm hour (next day)
						newValue.addHour(1);
				}
			}
			else if (value == TaskState.TOMORROW) {
				newValue = MCalendar.tomorrow();
				// set time from task
				if ((singleSelection != null) && singleSelection.getDateTime().isValid())
					newValue.setTime(MCalendar.of(singleSelection.getDateTime()));
			}
			else if (value == TaskState.LATER) {
				// set time from task
				if ((singleSelection != null) && singleSelection.getDateTime().isValid())
					newValue = MCalendar.of(singleSelection.getDateTime());
				else
					newValue = MCalendar.now();
			}
			else {
				throw new WTFError(value);
			}

			if ((selectedViewRows.length == 1) || (value == TaskState.LATER)) {
				MCalendar newDateTime = Input.getDateTime(
					getSourceWindow(),
					newValue,
					getName(),
					Task.DATE_FORMAT, Task.TIME_FORMAT
				);

				if (newDateTime == null)
					return;
				
				newValue = newDateTime;
				setTime = true;
			}
			else {
				setTime = false;
			}

			super.onAction();
		}

		// protected

		@Override
		protected Object convertValue(final TaskModel model, final int modelRow) {
			// change calendar field
			if ((calendarField != Integer.MIN_VALUE) && (calendarFieldChange != Integer.MIN_VALUE)) {
				MDate currentTaskDate = (MDate)model.getValueAt(modelRow, column.ordinal());
				if (!currentTaskDate.isValid())
					currentTaskDate = MDate.now();
				MCalendar newTaskDate = MCalendar.of(currentTaskDate);
				newTaskDate.add(calendarField, calendarFieldChange);

				return newTaskDate.toDate();
			}

			if ((column == Column.DATE_TIME) && (newValue == null))
				return MDate.invalid();

			MDate currentTaskDate = (MDate)model.getValueAt(modelRow, column.ordinal());
			MCalendar newTaskDate = MCalendar.of(currentTaskDate);

			// assign new date
			newTaskDate.setDate(newValue);
			
			// assign new time (if applicable)
			if (
				(selectedViewRows.length == 1) ||
				!currentTaskDate.isValid() ||
				setTime
			) {
				newTaskDate.setTime(newValue);
			}

			return newTaskDate.toDate();
		}

	}

	/**
	 * @since 4.4
	 */
	public static class SetNextDateAction extends SetValueAction<Object> {

		// public

		public SetNextDateAction(final TaskTable table, final int[] selectedViewRows) {
			super(table, selectedViewRows, RepeatRule.APPLY_REPEAT_RULE_ACTION_INFO);

			if (singleSelection != null) {
				RepeatRule repeatRule = new RepeatRule(singleSelection);
				setEnabled(repeatRule.isValid());
			}
		}

		@Override
		public void onAction() {
			TaskTable table = get();
			TaskModel model = table.getModel();

			List<Task> selectedTasks = table.getSelectedTasks();
			StringList selectedText = new StringList(selectedTasks.size());
			for (Task i : selectedTasks)
				selectedText.add(i.getSummary());

			if (!new MMessage.Builder()
				.title(this.getName())
				.icon(this.getIcon())
				.ok(RepeatRule.APPLY_REPEAT_RULE_ACTION_INFO)
				.cancel(RepeatRule.DO_NOT_REPEAT_ACTION_INFO)
				.simpleText()
				.list(selectedText)
				.exec(this)
			)
				return;

			int[] selection = table.getSelectedRows();
			for (int i : selection) {
				int modelRow = table.convertRowIndexToModel(i);
				Task task = model.getRowAt(modelRow);
				Task undo = task.copy();
				RepeatRule repeatRule = new RepeatRule(task);
				if (repeatRule.apply(task))
					model.updateRowsAndUndo(undo, task, modelRow);
			}
		}

	}

	/**
	 * @since 3.8.11
	 */
	public static class SetPriorityAction extends SetValueAction<Priority> {

		// public

		public SetPriorityAction(final TaskTable table, final int[] selectedViewRows, final Priority value) {
			super(table, selectedViewRows, new MActionInfo(value.toString()), Column.PRIORITY, value);
			setSmallIcon(value.getSmallColorIcon());
		}

	}

	/**
	 * @since 4.4
	 */
	public static class SetRepeatRuleAction extends SetValueAction<RepeatRule> {

		// public

		public SetRepeatRuleAction(final TaskTable table, final int[] selectedViewRows) {
			super(table, selectedViewRows, RepeatRule.REPEAT_RULE_ACTION_INFO.noIcon());
		}

		@Override
		public void onAction() {
			TaskTable table = get();
			TaskModel model = table.getModel();
			int[] selection = table.getSelectedRows();
			
			MCalendar calendar;
			RepeatRule repeatRule;
			if (selection.length == 0) {
				calendar = MCalendar.now();
				repeatRule = new RepeatRule();
			}
			else {
				int modelRow = table.convertRowIndexToModel(selection[0]);
				Task task = model.getRowAt(modelRow);
				calendar = task.getDateTime().isValid() ? task.getDateTime().toCalendar() : MCalendar.now();
				repeatRule = new RepeatRule(task);
			}
		
			RepeatPanel panel = new RepeatPanel(repeatRule, calendar);
			repeatRule = panel.showDialog(getSourceWindow());
			panel.dispose();

			if (repeatRule == null)
				return;
			
			for (int i : selection) {
				int modelRow = table.convertRowIndexToModel(i);
				Task task = model.getRowAt(modelRow);
				Task undo = task.copy();
				if (repeatRule.set(task))
					model.updateRowsAndUndo(undo, task, modelRow);
			}
		}

	}

	/**
	 * @since 3.8.11
	 */
	public static class SetValueAction<T> extends MDataAction.Weak<TaskTable> {

		// protected

		protected final Column column;
		protected final int[] selectedViewRows;
		protected final T value;
		
		/**
		 * @since 4.4
		 */
		protected final Task singleSelection;

		// public

		/**
		 * @since 4.4
		 */
		public SetValueAction(final TaskTable table, final int[] selectedViewRows, final MActionInfo info) {
			this(table, selectedViewRows, info, null, null);
		}

		public SetValueAction(final TaskTable table, final int[] selectedViewRows, final MActionInfo info, final Column column, final T value) {
			super(table, info);
			this.selectedViewRows = selectedViewRows.clone();
			this.singleSelection =
				(selectedViewRows.length == 1)
				? table.getModel().getRowAt(table.convertRowIndexToModel(selectedViewRows[0]))
				: null;
			this.column = column;
			this.value = value;
		}

		@Override
		public void onAction() {
			MDate after = null;
			MDate before = null;
			
			TaskTable table = get();
			TaskModel model = table.getModel();
			int[] selection = table.getSelectedRows();
			for (int i : selection) {
				int modelRow = table.convertRowIndexToModel(i);

				Object o = model.getValueAt(modelRow, column.ordinal());
				if ((o instanceof MDate) && (before == null))
					before = new MDate((MDate)o);

				Object convertedValue = convertValue(model, modelRow);
				
				if ((convertedValue instanceof MDate) && (after == null))
					after = (MDate)convertedValue;
				
				model.setValueAt(convertedValue, modelRow, column);
			}
			
			if ((after != null) && (before != null) && !Objects.equals(before, after)) {
				MCalendar beforeCal = before.toCalendar();
				MCalendar afterCal = after.toCalendar();
				boolean sameDate = beforeCal.isSameDate(afterCal);
				boolean sameTime = beforeCal.isSameTime(afterCal, false);
				boolean sameWeek = (beforeCal.getYear() == afterCal.getYear()) && (beforeCal.getWeek() == afterCal.getWeek());
				MStatusBar.info(
					formatDateTime(before, sameDate, sameTime, sameWeek) +
					" -> " +
					formatDateTime(after, sameDate, sameTime, sameWeek)
				);
			}
		}

		// protected

		/**
		 * @since 3.8.12
		 */
		protected Object convertValue(final TaskModel model, final int modelRow) { return value; }
		
		// private
		
		private String formatDateTime(final MDate date, final boolean sameDate, final boolean sameTime, final boolean sameWeek) {
			if (!date.isValid())
				return _("No Date/Time");

			if (sameDate && !sameTime)
				return Task.formatTime(date);
			
			if (sameWeek && sameTime)
				return date.format("EEEE");

			return date.fancyFormat(sameTime ? 0 : MDate.FANCY_FORMAT_APPEND_TIME);
		}

	}
	
	// private classes

	private static final class AddAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			TaskTable table = get();
			TaskPropertiesPanel panel = new TaskPropertiesPanel(new Task());
			table.addTask(getSourceWindow(), panel);
		}

		// private

		private AddAction(final TaskTable table) {
			super(table, _("Add Task"));
			ShapeIcon.PLUS.set(this);
			setAcceleratorKey(VK_INSERT, 0);
			setEnabled(false);
		}

	}

	private static final class CopyAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			if (!get().copy(false))
				showErrorMessage();
		}

		// private

		private CopyAction(final TaskTable table) {
			super(table, MActionInfo.COPY);
			setEnabled(false);
		}

	}

	private static final class CutAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			TaskTable table = get();

			if (!table.canCancelEdit(getSourceWindow(), getName()))
				return;

			if (!table.copy(true))
				showErrorMessage();
		}

		// private

		private CutAction(final TaskTable table) {
			super(table, MActionInfo.CUT);
			setEnabled(false);
		}

	}

// TODO: 2.0: group "small" undoable edits
	private static final class DeleteAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			TaskTable table = get();
			table.deleteSelectedItems(getSourceWindow());
		}

		// private

		private DeleteAction(final TaskTable table) {
			super(table, TaskTable.DELETE_SELECTED);
			setEnabled(false);
			setHTMLHelp(_("Deletes all the selected tasks."));
		}

	}

	private static final class DeleteCompletedAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			TaskTable table = get();
			table.deleteCompletedItems(getSourceWindow());
		}

		// private

		private DeleteCompletedAction(final TaskTable table) {
			super(table, TaskTable.DELETE_COMPLETED);
			setEnabled(false);
			setHTMLHelp(_("Deletes all the completed tasks."));
		}

	}

	private static final class EditActionGroup extends ActionGroup {

		// private

		private final WeakReference<TaskTable> taskTableRef;

		// public

		@Override
		public void update() {
			TaskTable taskTable = taskTableRef.get();
			boolean isSelection = taskTable.isSelection();
			boolean locked = taskTable.getModel().isLocked();
			taskTable.undoManager.updateUndoRedoActions(!locked);
			setEnabled("add", !locked);
			setEnabled("copy", isSelection);
			setEnabled("cut", isSelection && !locked);
			setEnabled("delete", isSelection && !locked);
			setEnabled("delete-completed", !taskTable.isEmpty(MV.MODEL) && !locked);
			setEnabled("paste", !locked);
			setEnabled("select-all", !taskTable.isEmpty(MV.VIEW));
			setEnabled("unselect-all", isSelection);
		}

		// private

		private EditActionGroup(final TaskTable taskTable) {
			taskTableRef = new WeakReference<>(taskTable);
		}

	}
	
	private static final class PasteAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			if (!get().paste())
				showErrorMessage();
		}

		// private

		private PasteAction(final TaskTable table) {
			super(table, MActionInfo.PASTE);
			setEnabled(false);
		}

	}

	private static final class SelectAllAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			get().selectAll();
		}

		// private

		private SelectAllAction(final TaskTable table) {
			super(table, MActionInfo.SELECT_ALL);
			setEnabled(false);
		}

	}

	private static final class UnselectAllAction extends MDataAction.Weak<TaskTable> {

		// public

		@Override
		public void onAction() {
			get().clearSelection();
		}

		// private

		private UnselectAllAction(final TaskTable table) {
			super(table, MActionInfo.UNSELECT_ALL);
			setEnabled(false);
		}

	}

}
