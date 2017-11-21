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

package org.makagiga.todo;

import static org.makagiga.commons.UI.i18n;

import java.awt.Color;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.makagiga.commons.ColorProperty;
import org.makagiga.commons.Copyable;
import org.makagiga.commons.MCalendar;
import org.makagiga.commons.MDate;
import org.makagiga.commons.MObject;
import org.makagiga.commons.Searchable;
import org.makagiga.commons.MStringBuilder;
import org.makagiga.commons.TK;
import org.makagiga.commons.Tuple;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.annotation.Important;
import org.makagiga.commons.category.Category;
import org.makagiga.commons.category.CategoryList;
import org.makagiga.commons.category.CategoryManager;
import org.makagiga.commons.html.HTMLBuilder;
import org.makagiga.pim.PIMData;
import org.makagiga.pim.calendar.ICalendar;
import org.makagiga.pim.calendar.ICalendarTodo;
import org.makagiga.pim.calendar.VCalendarTodo;

/**
 * @since 2.0
 */
public final class Task
implements
	Cloneable,
	Comparable<Task>,
	Copyable<Task>,
	Searchable<String>,
	Serializable
{

	// public
	
	/**
	 * @since 4.8
	 */
	public static final int NO_NUMBER = 0;
	
	/**
	 * @since 4.6
	 */
	public static final int ORG_MODE_NO_DATE_TIME = 1;
	
	/**
	 * @since 4.6
	 */
	public static final int ORG_MODE_NO_SUMMARY_PREFIX = 1 << 1;

	/**
	 * @since 4.6
	 */
	public static final int ORG_MODE_MAKAGIGA_DATE_FORMAT = 1 << 2;
	
	/**
	 * @since 4.10
	 */
	public static final int UNKNOWN_DURATION = 0;

	/**
	 * @since 4.6
	 */
	public static final int ORG_MODE_MULTILINE_SUMMARY = 1 << 3;

	/**
	 * @since 4.2
	 */
	public static final String COLOR_PROPERTY = "color";

	// private
	
	private HashMap<String, Object> properties;
	private int complete;
	private MDate completeDateTime = MDate.invalid();
	private MDate dateTime = MDate.invalid();
	private MDate startDateTime = MDate.invalid();
	private Priority priority = Priority.DEFAULT;
	private static SimpleDateFormat orgModeFormat;
	private String category = "";
	private static final String DURATION_PROPERTY = "duration";
	private static final String NUMBER_PROPERTY = "number";
	private String summary = "";
	
	// package
	
	static final int DATE_FORMAT = MDate.LONG;
	static final int TIME_FORMAT = MDate.SHORT;

	// public
	
	public Task() { }

	public Task(
		final String summary,
		final Priority priority,
		final int complete,
		final long dateTime,
		final long startDateTime,
		final long completeDateTime
	) {
		this.priority = priority;
		this.complete = TK.limit(complete, 0, 100);
		setSummary(summary);

		setDateTime(dateTime);
		setStartDateTime(startDateTime);
		setCompleteDateTime(completeDateTime);
	}

	@Override
	public boolean equals(final Object other) {
		switch (MObject.equalsFinal(this, other)) {
			case YES: return true;
			case NO: return false;
			default:
				Task task = (Task)other;

				return (
					(this.complete == task.complete) &&
					(this.priority == task.priority) &&
					this.category.equals(task.category) &&
					this.summary.equals(task.summary) &&

					this.dateTime.equals(task.dateTime) &&
					this.startDateTime.equals(task.startDateTime) &&
					this.completeDateTime.equals(task.completeDateTime) &&
					this.getProperties().equals(task.getProperties())
				);
		}
	}
	
	public String formatComplete() {
		return formatComplete(complete);
	}
	
	public static String formatComplete(final int value) {
		return value + "%";
	}
	
	public String formatDate() {
		return formatDate(getDateTimeState());
	}

	public String formatDate(final TaskState state) {
		return formatDate(dateTime, state);
	}

	public static String formatDate(final MDate value, final TaskState state) {
		if (!value.isValid())
			return "";
		
		if ((state == null) || (state == TaskState.UNKNOWN))
			return value.fancyFormat(MDate.FANCY_FORMAT_ABSOLUTE | MDate.FANCY_FORMAT_APPEND_TIME | MDate.FANCY_FORMAT_APPEND_YEAR);
		
		return value.fancyFormat(MDate.FANCY_FORMAT_APPEND_TIME);
	}
	
	/**
	 * @since 4.10
	 */
	public String formatDuration() {
		return formatDuration(getDuration());
	}
	
	/**
	 * @since 4.10
	 */
	public static String formatDuration(final int value) {
		if (value == UNKNOWN_DURATION)
			return "";
		
		return MDate.formatPeriod(
			TimeUnit.SECONDS.toMillis(value),
			MDate.FORMAT_PERIOD_APPEND_TIME | MDate.FORMAT_PERIOD_SHORT_TIME_SUFFIX
		);
	}
	
	/**
	 * @since 4.2
	 */
	public String formatSummary(final int maxLength) {
		return formatSummary(summary, maxLength);
	}

	/**
	 * @since 4.2
	 */
	public static String formatSummary(final String summary, final int maxLength) {
		StringBuilder s = new StringBuilder(
			(maxLength == -1)
			? Math.max(summary.length(), 50)
			: maxLength
		);
	
		if (summary.isEmpty()) {
			s.append('<').append(i18n("No Summary")).append('>');
		}
		else {
			s.append((maxLength == -1) ? summary : TK.rightSqueeze(summary, maxLength));
			
			// new line -> space
			int len = s.length();
			for (int i = 0; i < len; i++) {
				if (s.charAt(i) == '\n')
					s.setCharAt(i, ' ');
			}
		}
		
		return s.toString();
	}

	/**
	 * @since 4.0
	 */
	public String formatTime() {
		return formatTime(dateTime);
	}

	/**
	 * @since 4.4
	 */
	public static String formatTime(final java.util.Date date) {
		if (!MDate.isValid(date))
			return "";
	
		return MDate.formatTime(date, MDate.SHORT);
	}

	/**
	 * Returns a non-null category.
	 *
	 * @since 3.0
	 */
	public String getCategory() { return category; }

	/**
	 * @since 3.0
	 */
	public void setCategory(final String value) {
		category = Objects.toString(value, "");
	}
	
	/**
	 * @since 4.2
	 */
	public Color getColor() {
		Object o = getProperty(COLOR_PROPERTY, null);
		
		return (o instanceof Color) ? (Color)o : null;
	}

	@SuppressWarnings("ReturnOfDateField")
	public MDate getCompleteDateTime() { return completeDateTime; }

	public void setCompleteDateTime(final long value) {
		completeDateTime.setTime(value);
	}
	
	public void setCompleteDateTime(final MDate value) {
		completeDateTime.setTime(value);
	}

	@SuppressWarnings("ReturnOfDateField")
	public MDate getDateTime() { return dateTime; }

	public void setDateTime(final long value) {
		dateTime.setTime(value);
	}
	
	public void setDateTime(final MDate value) {
		dateTime.setTime(value);
	}

	/**
	 * @since 4.4
	 */
	@Deprecated
	public TaskState getDateTimeState() {
		return TaskState.of(dateTime, MCalendar.now());
	}
	
	/**
	 * @since 3.0
	 *
	 * @deprecated Since 4.4
	 */
	@Deprecated
	public TaskState getDateTimeStateNow(final MDate now) {
		return TaskState.of(dateTime, now.toCalendar());
	}

	/**
	 * @since 4.4
	 */
	public TaskState getDateTimeState(final MCalendar now) {
		return TaskState.of(dateTime, now);
	}

	/**
	 * @since 4.10
	 */
	public int getDuration() {
		Object o = getProperty(DURATION_PROPERTY, null);
		
		return (o instanceof Integer) ? (Integer)o : UNKNOWN_DURATION;
	}

	/**
	 * @since 4.10
	 */
	public void setDuration(final int value) {
		setProperty(DURATION_PROPERTY, (value == UNKNOWN_DURATION) ? null : value);
	}

	/**
	 * @since 4.8
	 */
	public int getNumber() {
		Object o = getProperty(NUMBER_PROPERTY, null);
		
		return (o instanceof Integer) ? (Integer)o : NO_NUMBER;
	}

	/**
	 * @since 4.8
	 */
	public void setNumber(final int value) {
		setProperty(NUMBER_PROPERTY, (value == NO_NUMBER) ? null : value);
	}
	
	/**
	 * @since 4.8
	 */
	public long getPeriod(final long now) {
		if (!dateTime.isValid())
			return 0L;
	
		return dateTime.getTime() - now;
	}

	/**
	 * @since 4.2
	 */
	public synchronized Map<String, Object> getProperties() {
		return (properties == null) ? Collections.<String, Object>emptyMap() : properties;
	}
	
	/**
	 * @since 4.2
	 */
	@SuppressWarnings("unchecked")
	public synchronized <T> T getProperty(final String name, final T defaultValue) {
		if (properties == null)
			return defaultValue;
		
		T o = (T)properties.get(name);
		
		return TK.get(o, defaultValue);
	}

	/**
	 * @since 4.2
	 */
	public synchronized Object setProperty(final String name, final Object value) {
		if (properties == null)
			properties = new HashMap<>();
		
		return properties.put(name, value);
	}

	@SuppressWarnings("ReturnOfDateField")
	public MDate getStartDateTime() { return startDateTime; }
	
	public void setStartDateTime(final long value) {
		startDateTime.setTime(value);
	}
	
	public void setStartDateTime(final MDate value) {
		startDateTime.setTime(value);
	}
	
	public int getComplete() { return complete; }
	
	public void setComplete(final int value) { complete = value; }

	public Priority getPriority() { return priority; }
	
	public void setPriority(final Priority value) { priority = value; }

	/**
	 * Returns a non-null summary.
	 */
	public String getSummary() { return summary; }
	
	public void setSummary(final String value) {
		summary = Objects.toString(value, "");
	}
	
	/**
	 * @since 3.0
	 */
	public String getToolTipTable(final boolean includeSummary) {
		HTMLBuilder html = new HTMLBuilder();
		html.beginTag(
			"table",
			"border", 0,
			"cellpadding", 0,
			"cellspacing", 5
		);
		
		// summary

		if (includeSummary) {
			html.beginTag("tr");
			html.doubleTag("td", html.escape(formatSummary(100)), "colspan", 2);
			html.endTag("tr");
		}

		// date/time

		long time = dateTime.getTime();
		MCalendar now = null;
		
		if (time != 0) {
			now = MCalendar.now();
			TaskState state = getDateTimeState(now);
			
			String dateStyle =
				"background-color: " + ColorProperty.toString(state.getColorForPaint()) +
				"; color: " + ColorProperty.toString(Color.BLACK);
			html.beginTag("tr", "style", dateStyle);
			String s =
				(state == TaskState.LATER) || (state == TaskState.OVERDUE)
				? (state + " - ")
				: "";
			s += formatDate(state);
			html.doubleTag("td", "&nbsp;" + html.escape(s) + "&nbsp;", "align", "center", "colspan", 2);
			html.endTag("tr");

			if (state.isFuture()) {
				long period = getPeriod(now.getTimeInMillis());
				if (period > 0) {
					html.beginTag("tr");
					html.doubleTag("td", html.escape(i18n("Time To:")));
					int options = MDate.FORMAT_PERIOD_APPEND_TIME | MDate.FORMAT_PERIOD_SHORT_TIME_SUFFIX;
					html.doubleTag("td", html.escape(MDate.formatPeriod(period, options)));
					html.endTag("tr");
				}
			}
		}

		// duration

		String duration = formatDuration();
		if (!duration.isEmpty()) {
			html.beginTag("tr");
			html.doubleTag("td", html.escape(i18n("Duration:")));
			int options = MDate.FORMAT_PERIOD_APPEND_TIME | MDate.FORMAT_PERIOD_SHORT_TIME_SUFFIX;
			html.doubleTag("td", html.escape(duration));
			html.endTag("tr");
		}

		// complete
		
		html.beginTag("tr");
		html.doubleTag("td", html.escape(Column.COMPLETE.toString()));
		html.doubleTag("td", html.escape(formatComplete()));
		html.endTag("tr");

		// start date/time
		
		if (startDateTime.isValid()) {
			if (now == null)
				now = MCalendar.now();
			TaskState state = TaskState.of(startDateTime, now);
			html.beginTag("tr");
			html.doubleTag("td", html.escape(Column.START_DATE_TIME.toString()));
			html.doubleTag("td", html.escape(formatDate(startDateTime, state)));
			html.endTag("tr");
		}

		// complete date/time
		
		if (completeDateTime.isValid()) {
			if (now == null)
				now = MCalendar.now();
			TaskState state = TaskState.of(completeDateTime, now);
			html.beginTag("tr");
			html.doubleTag("td", html.escape(Column.COMPLETE_DATE_TIME.toString()));
			html.doubleTag("td", html.escape(formatDate(completeDateTime, state)));
			html.endTag("tr");
		}

		// priority

		Priority p = getPriority();
		if (p.compareTo(Priority.NORMAL) > 0) {
			Color priorityColor = p.getColor();
			String priorityStyle = String.format(
				"background-color: %s; color: %s; text-align: center",
				(priorityColor == null) ? "white" : ColorProperty.toString(priorityColor),
				ColorProperty.toString(Color.BLACK)
			);
			html.beginTag("tr");
			html.doubleTag("td", html.escape(Column.PRIORITY.toString()));
			html.doubleTag("td", "&nbsp;" + html.escape(p.toString()) + "&nbsp;", "style", priorityStyle);
			html.endTag("tr");
		}

		// category

		if (!category.isEmpty()) {
			html.beginTag("tr");
			html.doubleTag("td", html.escape(Column.CATEGORY.toString()));
			html.doubleTag("td", html.escape(category));
			html.endTag("tr");
		}
		
		// repeat rule
		
		RepeatRule repeatRule = new RepeatRule(this);
		if (repeatRule.isValid()) {
			html.beginTag("tr");
			html.doubleTag("td", html.escape(i18n("Repeat Rule")));
			html.doubleTag("td", html.escape(repeatRule.toDisplayString()));
			html.endTag("tr");
		}

		html.endTag("table");
		
		return html.toString();
	}
	
	public String getToolTipText() {
		HTMLBuilder html = new HTMLBuilder();
		html.beginHTML();
		html.beginDoc();
		html.setIndentLevel(0);
		html.appendLine(getToolTipTable(false));
		html.endDoc();
		
		return html.toString();
	}

	@Override
	public int hashCode() { return 0; }

	public boolean isDone() {
		return (complete == 100);
	}

	/**
	 * @since 4.4
	 */
	public boolean matches(final Pattern pattern) {
		if (!getSummary().isEmpty() && pattern.matcher(getSummary()).find())
			return true;

		if (!getCategory().isEmpty() && pattern.matcher(getCategory()).find())
			return true;

		if (
			matches(pattern, getDateTime()) ||
			matches(pattern, getCompleteDateTime()) ||
			matches(pattern, getStartDateTime())
		)
			return true;
		
		if (pattern.matcher(getPriority().toString()).find())
			return true;

		if (pattern.matcher(formatComplete()).matches())
			return true;

		// match "Today", etc.
		if (getDateTime().isValid()) {
			String fancyFormat = formatDate();
			
			if (pattern.matcher(fancyFormat).find())
				return true;
		}

		for (Map.Entry<String, Object> i : getProperties().entrySet()) {
			String key = i.getKey();
			Object value = i.getValue();
			
			String s;
			if (value instanceof Color) {
				s = ColorProperty.toString((Color)value);
			}
			else if ((value instanceof Integer) && DURATION_PROPERTY.equals(key)) {
				s = formatDuration((Integer)value);
			}
			else
				s = Objects.toString(value, null);

			if (!TK.isEmpty(s) && pattern.matcher(s).find())
				return true;
		}

		return false;
	}

	public static long parseDateTime(final String value) {
		if (TK.isEmpty(value))
			return 0;
		
		// 0.9.4+
		if (value.indexOf('T') != -1) {
			java.util.Date result = MDate.parseRFC3339(value);
		
			return (result == null) ? 0 : result.getTime();
		}
		
		// 0.9.3-
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException exception) {
			return 0;
		}
	}

	/**
	 * @since 4.6
	 */
	public String toOrgMode() {
		return toOrgMode(0);
	}

	/**
	 * @since 4.6
	 */
	public String toOrgMode(final int options) {
		MStringBuilder s = new MStringBuilder();
		if ((options & ORG_MODE_NO_SUMMARY_PREFIX) == 0) {
			if (isDone())
				s.append("* DONE");
			else
				s.append("* TODO");
		}

		switch (getPriority()) {
			case VERY_HIGH:
			case HIGH:
				s.append(" [#A]");
				break;
			case LOW:
			case VERY_LOW:
				s.append(" [#C]");
				break;
			default: // NORMAL, [#B]
				break;
		}
		
		if (!getSummary().isEmpty()) {
			s.append(' ');
			if ((options & ORG_MODE_MULTILINE_SUMMARY) != 0)
				s.append(getSummary());
			else
				s.append(formatSummary(-1)); // single line
		}
		
		if ((getComplete() > 0) && (getComplete() < 100))
			s.append(" [").append(getComplete()).append("%]");

		if (!getCategory().isEmpty()) {
			s.append("\t:");
			CategoryList categoryList = new CategoryList(CategoryManager.getSharedInstance(), getCategory());
			for (Category category : categoryList) {
				String name = category.toString();
				int len = name.length();
				for (int i = 0; i < len; i++) {
					char c = name.charAt(i);
					if ((c == '_') || (c == '@') || Character.isLetter(c) || Character.isDigit(c))
						s.append(c);
					else
						s.append('_');
				}
				s.append(':');
			}
		}
		
		s.n();
		
		MDate date = getDateTime();
		if (((options & ORG_MODE_NO_DATE_TIME) == 0) && date.isValid()) {
			s.append("  <");
			
			String dateString;
			if ((options & ORG_MODE_MAKAGIGA_DATE_FORMAT) != 0) {
				dateString = formatDate(TaskState.UNKNOWN);
			}
			else {
				synchronized (Task.class) {
					if (orgModeFormat == null)
						orgModeFormat = new SimpleDateFormat(MDate.YYYY_MM_DD_FORMAT + " EEE " + MDate.HH_MM_FORMAT, Locale.ENGLISH);
					dateString = orgModeFormat.format(date);
				}
			}
			s.append(dateString);
		
			RepeatRule repeatRule = new RepeatRule(this);
			if (repeatRule.isValid()) {
				switch (repeatRule.getType()) {
					case DAILY:
						s.append(" +").append(repeatRule.getEvery()).append('d');
						break;
					case WEEKLY:
						s.append(" +").append(repeatRule.getEvery()).append('w');
						break;
					case MONTHLY:
						s.append(" +").append(repeatRule.getEvery()).append('m');
						break;
					case YEARLY:
						s.append(" +").append(repeatRule.getEvery()).append('y');
						break;
				}
			}
			
			s.append('>');

			s.n();
		}
		
		return s.toString();
	}
	
	@Important
	@Override
	public String toString() {
		return toString(-1);
	}

	/**
	 * @since 3.0
	 */
	public String toString(final int maxSummaryLength) {
		String format =
			// -1 - do not use "maxSummaryLength" argument
			// 0  - all task summaries are empty
			(maxSummaryLength < 1)
			? "%s\t%s\t%s%s%s\n"
			: ("%-" + maxSummaryLength + "s\t%s\t%s%s%s\n");
		
		return String.format(
			format,
			summary,
			priority,
			formatComplete(),
			dateTime.isValid() ? ('\t' + formatDate(TaskState.UNKNOWN)) : "",
			!category.isEmpty() ? ('\t' + category) : ""
		);
	}

	/**
	 * @since 3.8.4
	 */
	public VCalendarTodo toVCalendarTodo(final PIMData<?> data) {
		VCalendarTodo todo =
			(data instanceof ICalendar)
			? new ICalendarTodo()
			: new VCalendarTodo();

		int multilineIndex = summary.indexOf('\n');
		if (multilineIndex != -1) { // multiline?
			// no summary
			todo.setTextValue(VCalendarTodo.SUMMARY_PROPERTY, summary.substring(0, multilineIndex));

			// use multiline description
			todo.setTextValue(VCalendarTodo.DESCRIPTION_PROPERTY, summary);
		}
		else {
			todo.setTextValue(VCalendarTodo.SUMMARY_PROPERTY, summary);
			//todo.setValue(VCalendarTodo.DESCRIPTION_PROPERTY, "");
		}

		if (dateTime.isValid())
			todo.setDateValue(VCalendarTodo.DUE_PROPERTY, dateTime);
		todo.setDateValue(VCalendarTodo.LAST_MODIFIED_PROPERTY, MDate.now());
		
		int duration = getDuration();
		if (duration > 0) {
			Tuple.Three<Integer, Integer, Integer> dhm = getDurationDHM(duration);
			StringBuilder s = new StringBuilder("P");
			int d = dhm.get1();
			int h = dhm.get2();
			int m = dhm.get3();
			if (d > 0)
				s.append(d).append('D');
			s.append('T');
			s.append(h).append('H');
			s.append(m).append('M');
			todo.setValue(VCalendarTodo.DURATION_PROPERTY, s.toString());
		}

		todo.setValue(VCalendarTodo.PRIORITY_PROPERTY, getPriority().toICalendar());
		todo.setValue(
			VCalendarTodo.STATUS_PROPERTY,
			isDone()
				? "COMPLETED"
				: (data instanceof ICalendar) ? "NEEDS-ACTION" : "NEEDS ACTION"
		);

		/*
		if (todo instanceof ICalendarTodo) {
			CategoryList categoryList = new CategoryList(CategoryManager.getSharedInstance(), getCategory());
			String categories = categoryList
				.toString()
				.replace(';', '_')
				.replace(',', ';');//!!!multi values
			todo.setValue(VCalendarTodo.CATEGORIES_PROPERTY, categories);
		}
		 */

		return todo;
	}

	// Cloneable

	/**
	 * @since 3.8
	 *
	 * @deprecated As of 4.4, replaced by {@link #copy()}
	 */
	@Deprecated
	@Override
	@SuppressWarnings("unchecked")
	public Object clone() {
		try {
			Task task = (Task)super.clone();
			// deep copy
			task.completeDateTime = (MDate)this.completeDateTime.clone();
			task.dateTime = (MDate)this.dateTime.clone();
			task.startDateTime = (MDate)this.startDateTime.clone();
			synchronized (this) {
				if (this.properties != null)
					task.properties = (HashMap<String, Object>)this.properties.clone();
			}

			return task;
		}
		catch (CloneNotSupportedException exception) {
			throw new WTFError(exception);
		}
	}
	
	// Comparable
	
	/**
	 * @since 4.0
	 */
	@Override
	public int compareTo(final Task other) {
		int n1 = this.getNumber();
		if (n1 == Task.NO_NUMBER)
			n1 = Integer.MAX_VALUE;

		int n2 = other.getNumber();
		if (n2 == Task.NO_NUMBER)
			n2 = Integer.MAX_VALUE;

		int i = Integer.compare(n1, n2);

		if (i == 0) {
			i = Integer.compare(this.complete, other.complete);
			if (i == 0) {
				i = this.priority.compareTo(other.priority) * -1;
			}
		}
		
		return i;
	}

	// Copyable
	
	/**
	 * @since 4.4
	 */
	@Override
	public Task copy() {
		return (Task)clone();
	}

	// Searchable

	/**
	 * @inheritDoc
	 *
	 * @since 3.8
	 *
	 * @deprecated As of 4.6, replaced by {@link #matches(java.util.regex.Pattern)}
	 */
	@Deprecated
	@Override
	public boolean matches(final String filter, final Set<Searchable.Matches> options) {
		if (TK.isEmpty(filter))
			return true;

		boolean caseSensitive = options.contains(Searchable.Matches.CASE_SENSITIVE);
		if (caseSensitive) {
			if (
				getSummary().contains(filter) ||
				getCategory().contains(filter)
			)
				return true;
		}
		else {
			if (
				TK.containsIgnoreCase(getSummary(), filter) ||
				TK.containsIgnoreCase(getCategory(), filter)
			)
				return true;
		}

		MDate date = getDateTime();
		MDate completeDate = getCompleteDateTime();
		MDate startDate = getStartDateTime();
		if (caseSensitive) {
			if (
				(date.isValid() && formatDate(date, TaskState.UNKNOWN).contains(filter)) ||
				(completeDate.isValid() && formatDate(completeDate, TaskState.UNKNOWN).contains(filter)) ||
				(startDate.isValid() && formatDate(startDate, TaskState.UNKNOWN).contains(filter))
			)
				return true;
		}
		else {
			if (
				(date.isValid() && TK.containsIgnoreCase(formatDate(date, TaskState.UNKNOWN), filter)) ||
				(completeDate.isValid() && TK.containsIgnoreCase(formatDate(completeDate, TaskState.UNKNOWN), filter)) ||
				(startDate.isValid() && TK.containsIgnoreCase(formatDate(startDate, TaskState.UNKNOWN), filter))
			)
				return true;
		}
		
		for (Object o : getProperties().values()) {
			String s;
			if (o instanceof Color)
				s = ColorProperty.toString((Color)o);
			else
				s = Objects.toString(o, null);
			if (s != null) {
				if (caseSensitive) {
					if (s.contains(filter))
						return true;
				}
				else {
					if (TK.containsIgnoreCase(s, filter))
						return true;
				}
			}
		}

		return false;
	}
	
	// private
	
	private boolean matches(final Pattern pattern, final MDate d) {
		return d.isValid() && pattern.matcher(formatDate(d, TaskState.UNKNOWN)).find();
	}
	
	// package
	
	static Tuple.Three<Integer, Integer, Integer> getDurationDHM(final int duration) {
		long seconds = duration;
		long minutes = (seconds / 60L) % 60L;
		long hours = TimeUnit.SECONDS.toHours(seconds);
		long days = TimeUnit.HOURS.toDays(hours);
		hours -= (days * 24L);

		return Tuple.of((int)days, (int)hours, (int)minutes);
	}

}
