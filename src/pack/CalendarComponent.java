// Copyright 2011 Konrad Twardowski
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

package org.makagiga.tools.summary;

import static org.makagiga.commons.UI._;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.makagiga.commons.MAction;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.MCalendar;
import org.makagiga.commons.MDate;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.ValueEvent;
import org.makagiga.commons.ValueListener;
import org.makagiga.commons.swing.MCalendarPanel;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.event.MMenuAdapter;
import org.makagiga.editors.todo.TodoEditorPlugin;
import org.makagiga.fs.FSException;
import org.makagiga.fs.MetaInfo;
import org.makagiga.fs.MetaInfoAction;
import org.makagiga.fs.tree.TreeFS;
import org.makagiga.search.Query;
import org.makagiga.search.SortMethod;
import org.makagiga.todo.Task;
import org.makagiga.web.wiki.WikiPanel;

/**
 * @since 4.0
 */
public class CalendarComponent extends MCalendarPanel {
	
	// private
	
	private boolean idle;
	
	// public

	public CalendarComponent() {
		SummaryData summary = SummaryData.getInstance();
		idle = summary.isIdle();
		
		setAutoUpdate(true);
		
		addValueListener(new ValueListener<MDate>() {
			public void valueChanged(final ValueEvent<MDate> e) {
				if (e.isReason(CalendarComponent.ValueChangeReason.AUTO_UPDATE))
					CalendarComponent.this.onTitleUpdate();
			}
		} );
		
		summary.addValueListener(new ValueListener<Object>() {
			@Override
			public void valueChanged(final ValueEvent<Object> e) {
				// Hide "Please Wait..." message if Task Summary is idle
				SummaryData data = (SummaryData)e.getSource();
				if (idle != data.isIdle()) {
					data.removeValueListener(this);
					idle = true;
					onTitleUpdate();
				}
			}
		} );
	}
	
	public String createTitle(final String date) {
		StringBuilder title = new StringBuilder();

		if (!idle) {
			title
				.append('[')
				.append(_("Please Wait..."))
				.append("] ");
		}

		if (date != null)
			title.append(_("{0} - Calendar", date));
		else
			title.append(_("Calendar"));
		
		return title.toString();
	}

	// protected

	@Override
	protected MMenu onClick(final int day) {
		MDate date = getValue();
		String monthNames2 = MApplication.getResourceString("x.Calendar.monthNames2", null);

		MMenu menu = new MMenu();
		menu.addTitle(date.fancyFormat(0));
		menu.add(new DateAction(
			_("Show Information for This Day"), "ui/info", date,
			MApplication.getResourceString("x.Calendar.onThisDay", null),
			monthNames2
		));
		menu.add(new DateAction(
			_("News"), null, date,
			MApplication.getResourceString("x.Calendar.news", null),
			monthNames2
		));
		
		menu.addSeparator();

		SummaryData data = SummaryData.getInstance();
		SummaryData.Day dayKey = new SummaryData.Day(date.toCalendar());
		menu.add(data.getSummaryCategory().createMenu(dayKey));

		menu.add(createTaskMenu());

		boolean titleAdded = false;
		MCalendar nowCalendar = date.toCalendar();
		for (SummaryData.Node taskStateNode : data.getTaskStateMap().values()) {
			for (SummaryData.Node metaInfoNode : taskStateNode) {
				for (SummaryData.Node taskNode : metaInfoNode) {
					Task task = taskNode.getTask();
					MDate taskDate = task.getDateTime();
					if (taskDate.isValid()) {
						MCalendar taskCalendar = taskDate.toCalendar();
						if (
							(taskCalendar.getDay() == day) &&
							(taskCalendar.isSameField(nowCalendar, MCalendar.MONTH, MCalendar.YEAR))
						) {
							if (!titleAdded) {
								menu.addTitle(_("Tasks for This Day"));
								titleAdded = true;
							}
							menu.add(new TodoEditorPlugin.ShowTaskAction(metaInfoNode.getMetaInfo(), task));
						}
					}
				}
			}
		}

		return menu;
	}
	
	protected void onTitleUpdate() { }
	
	// private
	
	private MMenu createTaskMenu() {
		MMenu menu = new MMenu(_("Add a New Task for This Day"), "ui/newfile");
		menu.setPopupMenuVerticalAlignment(MMenu.CENTER);
		menu.addMenuListener(new MMenuAdapter(true) {
			@Override
			protected void onSelect(final MMenu sourceMenu) {
				Query query = new Query(SortMethod.PATH);
				query.add(Query.TYPE, "mgtodo");
				Query.Hits hits = query.start();

				sourceMenu.removeAll();
				sourceMenu.addTitle(_("Todo Lists"));
				
				MDate date = getValue();
				for (MetaInfo i : hits) {
					if (i.canModify())
						sourceMenu.add(new AddTaskAction(i, date));
				}

				sourceMenu.addSeparator(false);

				MetaInfo metaInfo = MetaInfo.createDummy(_("New File"), MIcon.small("ui/newfile"));
				sourceMenu.add(new AddTaskAction(metaInfo, date));
			}
		} );
		
		return menu;
	}

	// private classes

	private static final class AddTaskAction extends MetaInfoAction {
		
		// private
		
		private final MDate date;

		// public
		
		@Override
		public void onAction() {
			MetaInfo metaInfo = getData();
			
			// create a new file
			if (metaInfo.isDummy()) {
				TreeFS fs = TreeFS.getInstance();
				try {
					metaInfo = fs.createUniqueFile(fs.getRoot(), _("New File"), "mgtodo");
				}
				catch (FSException exception) {
					MMessage.error(getSourceWindow(), exception);
				}
			}
			
			// open todo list and add task
			TodoEditorPlugin.newTask(metaInfo, date);
		}

		// private
		
		private AddTaskAction(final MetaInfo metaInfo, final MDate date) {
			super(metaInfo, true);
			this.date = date;
		}
		
	}

	private static final class DateAction extends MAction {

		// private

		private final MDate date;
		private final String monthNames2;
		private final String template;
		
		// public
		
		@Override
		public void onAction() {
			if (TK.isEmpty(template))
				return;

			// alternate month names
			String[] fullMonthNames2;
			if (monthNames2 == null) {
				fullMonthNames2 = DateFormatSymbols.getInstance(Locale.US).getMonths();
			}
			else {
				fullMonthNames2 = monthNames2.split(" ");
				if (fullMonthNames2.length != 12)
					fullMonthNames2 = DateFormatSymbols.getInstance(Locale.US).getMonths();
			}

			SimpleDateFormat format;
			if (monthNames2 == null)
				format = new SimpleDateFormat("", Locale.US);
			else
				format = new SimpleDateFormat("");

			String url = template;

			String[][] formats = {
				{ "d", "{@day-of-month}" },
				{ "dd", "{@2-digit-day-of-month}" },
				{ "H", "{@hour}" },
				{ "MM", "{@2-digit-month}" },
				{ "MMMM", "{@full-month-name}" },
				{ "yyyy", "{@4-digit-year}" }
				// TEST: { "", "{@test}" }
			};

			// TEST: url="/{@test}/{@full-month-name-2}/{@day-of-month}/{@2-digit-day-of-month}/{@full-month-name}/{@lower-case-full-month-name}/";

			MDate now = MDate.now();
			String datePattern;
			String formattedDate;
			String urlPattern;
			for (int i = 0; i < formats.length; i++) {
				datePattern = formats[i][0];
				urlPattern = formats[i][1];
				format.applyPattern(datePattern);
				if (datePattern.equals("H"))
					formattedDate = format.format(now);
				else
					formattedDate = format.format(date);
				url = url.replace(urlPattern, TK.escapeURL(formattedDate));
				if (urlPattern.endsWith("-name}")) {
					urlPattern = "{@lower-case-" + urlPattern.substring(2);
					url = url.replace(urlPattern, TK.escapeURL(formattedDate.toLowerCase()));
				}
			}

			url = url.replace(
				"{@full-month-name-2}",
				TK.escapeURL(fullMonthNames2[format.getCalendar().get(MCalendar.MONTH)])
			);

			// TEST: MMessage.info(null, url);
			// TEST: MApplication.openURI(url);

			WikiPanel.openURI(getSourceWindow(), url, getName());
		}
		
		// private
		
		private DateAction(final String name, final String iconName, final MDate date, final String template, final String monthNames2) {
			super(name, iconName);
			this.date = date;
			this.template = template;
			this.monthNames2 = monthNames2;
			
			try {
				URL url = new URL(template);
				setHTMLHelp(UI.getLinkToolTipText(url.getHost()));
			}
			catch (MalformedURLException exception) {
				MLogger.warning("plugin", exception.toString());
			}
		}

	}

}
