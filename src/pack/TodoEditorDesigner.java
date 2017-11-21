// Copyright 2008 Konrad Twardowski
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

import java.awt.BorderLayout;
import java.util.List;

import org.makagiga.commons.MColor;
import org.makagiga.commons.MDisposable;
import org.makagiga.commons.TK;
import org.makagiga.commons.html.HTMLBuilder;
import org.makagiga.commons.html.Linkify;
import org.makagiga.commons.html.MHTMLViewer;
import org.makagiga.commons.swing.MTimer;
import org.makagiga.editors.Designer;
import org.makagiga.todo.Task;

/**
 * @since 3.0
 */
public final class TodoEditorDesigner extends Designer implements MDisposable {

	// private
	
	private MHTMLViewer summaryViewer;
	private MTimer updateTimer;

	// public
	
	public TodoEditorDesigner() {
		super(new BorderLayout(5, 5));
		
		summaryViewer = new MHTMLViewer();
		addCenter(summaryViewer);
	}

	@Override
	public void setLocked(final boolean value) {
		super.setLocked(value);
		//summaryEditButton.setEnabled(!value && hasValidSelection);
	}

	// MDisposable

	/**
	 * @inheritDoc
	 *
	 * @since 4.0
	 */
	@Override
	public void dispose() {
		updateTimer = TK.dispose(updateTimer);
	}

	// private
	
/*
	private void editSummary() {
		TodoEditorCore core = Editor.getCurrentCore(TodoEditorCore.class);
		
		if (core == null)
			return;
		
		int modelRow = core.convertRowIndexToModel(core.getSelectedRow());
		Task task = core.getModel().getRowAt(modelRow);
		String summary = SummaryEditor.edit(getWindowAncestor(), task.getSummary());
		if (summary != null) {
			core.getModel().setValueAt(summary, modelRow, Column.SUMMARY);
			updateComponents(core);
		}
	}
*/

	private void updateComponents(final TodoEditorCore core) {
		HTMLBuilder html = new HTMLBuilder();
		html.beginHTML();
		html.beginStyle();
			html.beginRule("a");
				html.addAttr("color", MColor.getLinkForeground(summaryViewer.getBackground()));
			html.endRule();
			html.beginRule("p, pre");
				html.addAttr("margin-left", "0px");
				html.addAttr("margin-right", "0px");
				html.addAttr("margin-top", "5px");
				html.addAttr("margin-bottom", "5px");
				html.addAttr("padding", "0px");
			html.endRule();
		html.endStyle();
		html.beginDoc();
		
		html.addHeader(2, html.escape(core.getSelectionText()));

		List<Task> selection = core.getSelectedTasks();
		for (Task task : selection) {
			//summaryEditButton.setEnabled(!isLocked());

			if (selection.size() == 1) {
				html.appendLine(task.getToolTipTable(false));
			}

			html.singleTag("hr");
			
			String summary = task.getSummary();
			if (selection.size() > 1)
				summary = TK.rightSqueeze(summary, 256);
			
			html.beginTag("p");

			// Linkify summary text
			Linkify linkify = Linkify.getInstance();
			StringBuilder s = linkify.applyToHTML(html.escape(summary));
			TK.fastReplace(s, "\n", "<br>");

			html.append(s);
			html.appendLine();

			html.endTag("p");
		}
/*
		else {
			summaryEditButton.setEnabled(false);
		}
*/
		
		html.endDoc();

		summaryViewer.setHTML(html);
	}

	// package
	
	void updateComponentsLater(final TodoEditorCore core) {
		if (updateTimer == null) {
			updateTimer = new MTimer(500) {
				@Override
				protected boolean onTimeout() {
					TodoEditorDesigner.this.updateComponents(core);
					
					return false;
				}
			};
		}
		updateTimer.restart();
	}

}
