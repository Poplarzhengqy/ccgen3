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

import java.util.Objects;
import java.util.Set;

import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MDate;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.swing.MCalendarPanel;
import org.makagiga.commons.swing.MEditorPane;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MScrollPane;

/**
 * @since 4.4
 */
public class TaskPropertiesPanel extends MPanel {

	// private
	
	private final MCalendarPanel dateTimeField;
	private final MEditorPane summaryField;
	private final PriorityEditor priorityField;
	private final Set<Column> columns;
	private final Task task;

	// public
	
	public TaskPropertiesPanel(final Task task, final Column... columns) {
		super(UI.VERTICAL);
		this.task = Objects.requireNonNull(task);
		this.columns = TK.newEnumSet(columns);
		
		dateTimeField = new MCalendarPanel();
		dateTimeField.setClearTime(false);
		dateTimeField.setShowInfo(false);//!!!common code
		dateTimeField.getDateSpinner().setDateTimeFormat(MDate.LONG, MDate.SHORT);
		dateTimeField.getTodayButton().setToolTipText(MActionInfo.CURRENT_DATE_AND_TIME.getText());

		dateTimeField.setValue(task.getDateTime());
		
		priorityField = new PriorityEditor();
		priorityField.setSelectedItem(task.getPriority());
		
		summaryField = new MEditorPane();
		summaryField.setText(task.getSummary());
		
		for (Column i : columns) {
			if (getComponentCount() > 0)
				addContentGap();
		
			switch (i) {
				case DATE_TIME: {
					add(MLabel.createFor(dateTimeField.getDateSpinner(), i.toString()));
					addGap();
					add(dateTimeField);
				} break;

				case PRIORITY: {
					add(MLabel.createFor(priorityField, i.toString()));
					addGap();
					add(priorityField);
				} break;

				case SUMMARY: {
					add(MLabel.createFor(summaryField, i.toString()));
					MScrollPane scrollPane = new MScrollPane(summaryField);
					scrollPane.setMaximumHeight(100);
					addGap();
					add(scrollPane);
				} break;
				
				default:
					throw new WTFError(i);
			}
		}
	}
	
	public void apply() {
		if (columns.contains(Column.DATE_TIME))
			task.setDateTime(dateTimeField.getValue());

		if (columns.contains(Column.PRIORITY))
			task.setPriority(priorityField.getSelectedItem());

		if (columns.contains(Column.SUMMARY))
			task.setSummary(summaryField.getText());
	}
	
	public Task getTask() { return task; }

}
