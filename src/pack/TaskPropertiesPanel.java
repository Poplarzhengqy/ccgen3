// Copyright 2012 Konrad Twardowski
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

import static org.makagiga.commons.UI.i18n;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import java.util.Set;

import org.makagiga.commons.MDate;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.swing.Focusable;
import org.makagiga.commons.swing.MCalendarPanel;
import org.makagiga.commons.swing.MEditorPane;
import org.makagiga.commons.swing.MGroupLayout;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MRadioButton;
import org.makagiga.commons.swing.MScrollPane;

/**
 * @since 4.4
 */
public class TaskPropertiesPanel extends MPanel implements Focusable {

	// private
	
	private final CompleteEditor completeField;
	private final MCalendarPanel dateTimeField;
	private final MEditorPane summaryField;
	private final MRadioButton noDateTime;
	private final MRadioButton selectDateTime;
	private final PriorityEditor priorityField;
	private final Set<Column> columns;
	private final Task task;

	// public
	
	public TaskPropertiesPanel(final Task task) {
		this(task, Column.SUMMARY, Column.DATE_TIME, Column.PRIORITY, Column.COMPLETE);
	}
	
	public TaskPropertiesPanel(final Task task, final Column... columns) {
		super(true);
		this.task = Objects.requireNonNull(task);
		this.columns = TK.newEnumSet(columns);
		
		Dimension d = UI.WindowSize.LARGE.getDimension();
		Dimension minSize = new Dimension(d.width / 2, d.height / 2);
		
		completeField = new CompleteEditor();
		completeField.setSelectedItem(task.getComplete());
		// not found in default list; create new item
		if (!completeField.getSelectedItem().equals(task.getComplete())) {
			completeField.addItem(task.getComplete());
			completeField.setSelectedItem(task.getComplete());
		}
		
		dateTimeField = new MCalendarPanel();
		dateTimeField.setClearTime(false);
		dateTimeField.setShowInfo(false);
		dateTimeField.getDateSpinner().setDateTimeFormat(Task.DATE_FORMAT, Task.TIME_FORMAT);

		priorityField = new PriorityEditor();
		priorityField.setSelectedItem(task.getPriority());
		
		summaryField = new MEditorPane();
		summaryField.setText(task.getSummary());
		
		ActionListener actionListener = new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if ((e.getSource() == noDateTime) || (e.getSource() == selectDateTime)) {
					dateTimeField.setEditable(selectDateTime.isSelected());
					if (selectDateTime.isSelected())
						dateTimeField.getDateSpinner().makeDefault();
				}
			}
		};
		
		noDateTime = new MRadioButton(i18n("No Date/Time"));
		noDateTime.addActionListener(actionListener);
		
		selectDateTime = new MRadioButton(i18n("Choose Date/Time:"));
		selectDateTime.addActionListener(actionListener);
		
		UI.group(noDateTime, selectDateTime);
		if (task.getDateTime().isValid()) {
			selectDateTime.setSelected(true);
			dateTimeField.setEditable(true);
			dateTimeField.setValue(task.getDateTime());
		}
		else {
			noDateTime.setSelected(true);
			dateTimeField.setEditable(false);
			dateTimeField.setValue(MDate.now());
		}
		
		MGroupLayout l = getGroupLayout();
		l.beginRows();
		
		for (Column i : columns) {
			if (getComponentCount() > 0)
				addContentGap();
		
			switch (i) {
				case COMPLETE:
					l.addComponent(completeField, i.toString());
					break;

				case PRIORITY:
					l.addComponent(priorityField, i.toString());
					break;

				case SUMMARY: {
					l.addComponent(MLabel.createFor(summaryField, i.toString()));
					MScrollPane scrollPane = new MScrollPane(summaryField);
					scrollPane.setMaximumHeight(100);
					l.addComponent(scrollPane, null, minSize, null);
				} break;
				
				default:
					if (!this.columns.contains(i))
						throw new WTFError(i);
			}
		}
		
		l.end(); // rows
		
		if (this.columns.contains(Column.DATE_TIME)) {
			l.beginRows();
			l.addComponent(noDateTime);
			l.addComponent(selectDateTime);
			l.addGap();
			l.addComponent(dateTimeField, null, minSize, null);
			l.end();
		}
	}
	
	public void apply() {
		if (columns.contains(Column.COMPLETE))
			task.setComplete(completeField.getSelectedItem());

		if (columns.contains(Column.DATE_TIME)) {
			MDate newDateTime = dateTimeField.getValue();
			if (noDateTime.isSelected()) {
				task.setDateTime(MDate.invalid());
			}
			else if (selectDateTime.isSelected() && newDateTime.isValid()) {
				task.setDateTime(newDateTime);
			}
		}

		if (columns.contains(Column.PRIORITY))
			task.setPriority(priorityField.getSelectedItem());

		if (columns.contains(Column.SUMMARY))
			task.setSummary(summaryField.getText());
	}
	
	public Task getTask() { return task; }
	
	// Focusable
	
	@Override
	public void focus() {
		if (columns.contains(Column.SUMMARY))
			summaryField.requestFocusInWindow();
		else if (columns.contains(Column.DATE_TIME))
			dateTimeField.focus();
		else
			requestFocusInWindow();
	}

}
