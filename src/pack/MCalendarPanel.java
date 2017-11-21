// Copyright 2006 Konrad Twardowski
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

package org.makagiga.commons.swing;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.ConstructorProperties;
import java.text.DateFormatSymbols;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.makagiga.commons.Item;
import org.makagiga.commons.Loop;
import org.makagiga.commons.MAction;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.MArrayList;
import org.makagiga.commons.MCalendar;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDataAction;
import org.makagiga.commons.MDate;
import org.makagiga.commons.MDisposable;
import org.makagiga.commons.MGraphics2D;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.ValueListener;
import org.makagiga.commons.annotation.InvokedFromConstructor;
import org.makagiga.commons.mv.MRenderer;
import org.makagiga.commons.transition.MoveTransition;
import org.makagiga.commons.transition.Transition;
import org.makagiga.commons.transition.TransitionPanel;
import org.makagiga.commons.swing.border.MLineBorder;
import org.makagiga.commons.swing.event.MMouseAdapter;

/**
 * @since 3.0, 4.0 (org.makagiga.commons.swing package)
 */
public class MCalendarPanel extends MPanel
implements
	Focusable,
	MDisposable,
	ValueChooser<MDate>
{

	// public

	public enum ValueChangeReason { AUTO_UPDATE, UPDATE };
	
	// private

	private boolean animationEnabled = true;
	private boolean clearTime = true;
	transient private boolean inUpdate;
	transient private boolean sameMonthAndYear;
	private final Border defaultBorder = UI.createEmptyBorder(2);
	private final Border selectedBorder;
	private final Border todayBorder;
	private static Font monthMenuItemFont;
	transient private int selectedDay;
	transient private int todayDay;
	private static MArrayList<MCalendarPanel> calendarPanelList = new MArrayList<>();
	private final MArrayList<DayButton> dayButtons = new MArrayList<>(31);
	private final MArrayList<DayLabel> dayLabels = new MArrayList<>(7);
	private MCalendar calendar;
	private final MCalendar lastAutoUpdateDate = MCalendar.now();
	private MDateSpinner dateSpinner;
	private MMessageLabel infoLabel;
	private MPanel daysPanel;
	private MPanel topPanel;
	private MSmallButton monthMenuButton;
	private MSmallButton todayButton;
	private MTimer autoUpdateTimer;
	private static Renderer globalRenderer;
	private Renderer renderer;
	private TransitionPanel transitionPanel;
	private static Updater globalUpdater;

	// public

	public MCalendarPanel() {
		this(MDate.now());
	}

	@ConstructorProperties("selectedDate")
	public MCalendarPanel(final MDate date) {
		todayBorder = BorderFactory.createLineBorder(MColor.RED, 2);
	
		Color brandColor = MApplication.getLightBrandColor();
		selectedBorder = BorderFactory.createLineBorder(brandColor, 2);

		setBackground(Color.WHITE);
		setForeground(Color.BLACK);
		setMargin(5);
		calendar = MCalendar.now();
		MApplication.firstDayOfWeek.get().set(calendar);
		int fdow = calendar.getFirstDayOfWeek();
		if ((fdow != MCalendar.MONDAY) && (fdow != MCalendar.SUNDAY))
			calendar.setFirstDayOfWeek(MCalendar.MONDAY);

		// info
		infoLabel = new MMessageLabel(MIcon.stock("ui/calendar"));
		infoLabel.setColor(brandColor, Color.BLACK);
		infoLabel.setCursor(Cursor.HAND_CURSOR);
		infoLabel.setTextAntialiasing(true);
		infoLabel.setToolTipText(_("Today"));
		infoLabel.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(final KeyEvent e) {
				if (MAction.isTrigger(e)) {
					setTodayDate();
					doUserUpdate();
					e.consume();
				}
			}
		} );
		infoLabel.addMouseListener(new MMouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				setTodayDate();
				doUserUpdate();
				e.consume();
			}
		} );
		addNorth(infoLabel);

		addCenter(createMainPanel());

		if (date != null)
			setValue(date);
	}
	
	/**
	 * @since 4.2
	 */
	public void addCalendarField(final int field, final int value) {
		MDate oldDate = getValue();
		calendar.add(field, value);
		update(oldDate, false);
	}

	public void addMonth(final int value) {
		addCalendarField(MCalendar.MONTH, value);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		
		synchronized (MCalendarPanel.class) {
			calendarPanelList.add(this);
			
			// ensure the view is up to date
			if (globalUpdater != null)
				globalUpdater.onUpdate(this);
		}
	}

	@Override
	public void removeNotify() {
		super.removeNotify();

		synchronized (MCalendarPanel.class) {
			calendarPanelList.remove(this);
		}
	}
	
	/**
	 * @since 4.4
	 */
	public static void applyGlobals() {
		Updater updater = getGlobalUpdater();
		for (MCalendarPanel i : getCalendarPanelList()) {
			if (updater != null)
				updater.onUpdate(i);
		}
	}

	/**
	 * @since 3.8
	 */
	public synchronized static List<MCalendarPanel> getCalendarPanelList() {
		return Collections.unmodifiableList(calendarPanelList);
	}
	
	/**
	 * @since 3.8.12
	 */
	public boolean getClearTime() { return clearTime; }

	/**
	 * @since 3.8.12
	 */
	public void setClearTime(final boolean value) { clearTime = value; }

	@Override
	public Dimension getMinimumSize() {
		// override minimum based on preferred day button size
		return new Dimension(1, 1);
	}

	public int getMonth() {
		return calendar.getMonth();
	}

	public void setMonth(final int value) {
		if (value != getMonth()) {
			MDate oldDate = getValue();
			calendar.setMonth(value);
			update(oldDate, false);
		}
	}

	@Override
	public void addValueListener(final ValueListener<MDate> l) {
		listenerList.add(ValueListener.class, l);
	}

	@Override
	@SuppressWarnings("unchecked")
	public ValueListener<MDate>[] getValueListeners() {
		return listenerList.getListeners(ValueListener.class);
	}

	@Override
	public void removeValueListener(final ValueListener<MDate> l) {
		listenerList.remove(ValueListener.class, l);
	}

	public MCalendar getCalendar() {
		if (clearTime)
			calendar.clearTime();
		
		return calendar;
	}
	
	public MDateSpinner getDateSpinner() { return dateSpinner; }
	
	public MPanel getDaysPanel() { return daysPanel; }

	public static Renderer getGlobalRenderer() { return globalRenderer; }
	
	public static void setGlobalRenderer(final Renderer value) { globalRenderer = value; }

	/**
	 * @since 3.8
	 */
	public static Updater getGlobalUpdater() { return globalUpdater; }

	/**
	 * @since 3.8
	 */
	public static void setGlobalUpdater(final Updater value) { globalUpdater = value; }

	public MMessageLabel getInfoLabel() { return infoLabel; }
	
	public Renderer getRenderer() { return renderer; }
	
	public void setRenderer(final Renderer value) { renderer = value; }

	public boolean getShowInfo() {
		return infoLabel.isVisible();
	}
	
	public void setShowInfo(final boolean value) {
		infoLabel.setVisible(value);
	}
	
	/**
	 * @since 4.4
	 */
	public MButton getTodayButton() { return todayButton; }
	
	/**
	 * @since 4.2
	 */
	public MPanel getTopPanel() { return topPanel; }

	/**
	 * @since 3.8.11
	 */
	public boolean isAnimationEnabled() { return animationEnabled; }

	/**
	 * @since 3.8.11
	 */
	public void setAnimationEnabled(final boolean value) { animationEnabled = value; }

	public boolean isAutoUpdate() {
		return autoUpdateTimer != null;
	}

	public void setAutoUpdate(final boolean value) {
		if (value) {
			if (autoUpdateTimer == null) {
				autoUpdateTimer = new MTimer(TimeUnit.MINUTES, 1) {
					@Override
					protected boolean onTimeout() {
						MCalendarPanel.this.doAutoUpdate();
						
						return true;
					}
				};
				autoUpdateTimer.start();
			}
		}
		else {
			if (autoUpdateTimer != null) {
				autoUpdateTimer.stop();
				autoUpdateTimer = null;
			}
		}
	}
	
	public boolean isMonthMenuButtonVisible() {
		return monthMenuButton.isVisible();
	}

	public void setMonthMenuButtonVisible(final boolean value) {
		monthMenuButton.setVisible(value);
	}

	public boolean isTodayButtonVisible() {
		return todayButton.isVisible();
	}

	public void setTodayButtonVisible(final boolean value) {
		todayButton.setVisible(value);
	}

	public void setTodayDate() {
		setValue(MDate.now());
	}

	/**
	 * @since 4.0
	 */
	public void updateRenderers(final int year, final int month) {
		int day = 0;
		for (DayButton i : dayButtons)
			applyRenderer(i, year, month, ++day);
	}
	
	// Focusable

	/**
	 * {@inheritDoc}
	 * 
	 * @since 4.0
	 */
	@Override
	public void focus() {
		if (dateSpinner.isEnabled()) {
			dateSpinner.requestFocusInWindow();
		}
		else {
			for (DayButton i : dayButtons) {
				if (i.day == selectedDay) {
					i.requestFocusInWindow();
					
					break; // for
				}
			}
		}
	}
	
	// MDisposable
	
	/**
	 * {@inheritDoc}
	 * 
	 * @since 4.0
	 */
	@Override
	public void dispose() {
		autoUpdateTimer = TK.dispose(autoUpdateTimer);
	}

	// ValueChooser

	/**
	 * @since 3.8.12
	 */
	@Override
	public MDate getValue() {
		return getCalendar().toDate();
	}

	/**
	 * @since 3.8.12
	 */
	@InvokedFromConstructor
	@Override
	public void setValue(final MDate value) {
		MDate oldDate = getValue();
		calendar.setTime(Objects.requireNonNull(value));
		lastAutoUpdateDate.setDate(calendar);
		update(oldDate, false);
	}

	// protected

	protected void fireValueChanged(final MDate oldDate, final MDate newDate, final ValueChangeReason reason) {
		TK.fireValueChanged(this, getValueListeners(), oldDate, newDate, reason);
	}

	protected MMenu onClick(final int day) { return null; }

	protected void onUserUpdate() { }
	
	// private
	
	private void applyRenderer(final DayButton dayButton, final int year, final int month, final int day) {
		applyRenderer(globalRenderer, calendar, dayButton, year, month, day);
		applyRenderer(renderer, calendar, dayButton, year, month, day);
	}
	
	private void applyRenderer(final Renderer renderer, final MCalendar calendar, final DayButton dayButton, final int year, final int month, final int day) {
		if (renderer == null)
			return;
		
		renderer.day = day;
		renderer.month = month;
		renderer.year = year;
		
		renderer.onRender(calendar);

		boolean selected = (selectedDay == day);
		boolean today = isToday(day);
		
		MLabel l = renderer.getLabel();
		
		if (l.isBackgroundSet()) {
			Color bg = l.getBackground();
			dayButton.setBackground((bg == null) ? null : bg);
		}
		else {
			if (selected)
				dayButton.setBackground(MApplication.getLightBrandColor());
			else
				dayButton.setBackground(Color.WHITE);
		}
		
		if (l.isForegroundSet()) {
			dayButton.setForeground(l.getForeground());
		}
		else {
			dayButton.setForeground(MColor.BLACK);
		}

		if (today) {
			Border insideBorder = selected ? selectedBorder : defaultBorder;
			dayButton.setBorder(BorderFactory.createCompoundBorder(todayBorder, insideBorder));
		}
		else {
			dayButton.setBorder(selected ? selectedBorder : defaultBorder);
		}

		dayButton.setIcon(l.getIcon());
		
		dayButton.setToolTipText(l.getToolTipText());
		
		if (TK.isEmpty(dayButton.getToolTipText())) {
			if (today)
				dayButton.setToolTipText(_("Today"));
			else if (selected)
				dayButton.setToolTipText(_("Selected Date"));
			else
				dayButton.setToolTipText(null);
		}
		
		dayButton.itemList.clear();
		dayButton.itemList.addAll(renderer.itemList);
		dayButton.repaint();
	}

	private MPanel createHeaderPanel() {
		MPanel p = MPanel.createBorderPanel();
		p.setMargin(5);
		p.setOpaque(false);

		// today
		todayButton = new MSmallButton(MActionInfo.CURRENT_DATE_AND_TIME.getSmallIcon(), _("Today")) {
			@Override
			protected void onClick() {
				MCalendarPanel.this.setTodayDate();
				MCalendarPanel.this.doUserUpdate();
			}
		};
		p.addWest(todayButton);

		// date spinner
		dateSpinner = new MDateSpinner();
		dateSpinner.setSimpleFormat("MMMM yyyy");
		dateSpinner.setToolTipText(_("Date"));
		dateSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				if (!inUpdate) {
					try {
						inUpdate = true;
						setValue(dateSpinner.toDate());
						doUserUpdate();
					}
					finally {
						inUpdate = false;
					}
				}
			}
		} );
		p.addCenter(dateSpinner);

		MPanel previousNextMonthPanel = MPanel.createHBoxPanel();
		previousNextMonthPanel.setOpaque(false);

		// previous month
		MAction previousMonthAction = new MAction(_("Previous Month"), "ui/previous", VK_PAGE_UP) {
			@Override
			public void onAction() {
				MCalendarPanel.this.addMonth(-1);
				MCalendarPanel.this.doUserUpdate();
			}
		};
		previousMonthAction.connect(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		MSmallButton previousMonth = new MSmallButton(previousMonthAction, false);
		previousNextMonthPanel.add(previousMonth);
		
		// month menu
		monthMenuButton = new MSmallButton(MIcon.small("ui/down"), _("Select a Month")) {
			@Override
			protected MMenu onPopupMenu() {
				return MCalendarPanel.this.createMonthMenu();
			}
		};
		monthMenuButton.setPopupMenuArrowPainted(false);
		monthMenuButton.setPopupMenuEnabled(true);
		previousNextMonthPanel.add(monthMenuButton);

		// next month
		MAction nextMonthAction = new MAction(_("Next Month"), "ui/next", VK_PAGE_DOWN) {
			@Override
			public void onAction() {
				MCalendarPanel.this.addMonth(1);
				MCalendarPanel.this.doUserUpdate();
			}
		};
		nextMonthAction.connect(this, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
		MSmallButton nextMonth = new MSmallButton(nextMonthAction, false);
		previousNextMonthPanel.add(nextMonth);

		p.addEast(previousNextMonthPanel);
		p.limitHeight(dateSpinner);
		topPanel = p;

		return p;
	}

	private MPanel createMainPanel() {
		MPanel p = MPanel.createBorderPanel();
		p.setOpaque(false);
		p.addNorth(createHeaderPanel());

		MPanel monthPanel = MPanel.createBorderPanel();
		monthPanel.setOpaque(false);
		MLabel dayNameLabel;
		MPanel dayNamesPanel = MPanel.createGridPanel(1, 8);

		dayNamesPanel.add(new EmptyCell()); // left/top corner

		dayNamesPanel.setOpaque(false);
		DateFormatSymbols dateFormatSymbols = DateFormatSymbols.getInstance();
		String[] shortDayNames = dateFormatSymbols.getShortWeekdays();
		String[] longDayNames = dateFormatSymbols.getWeekdays();
		if (calendar.getFirstDayOfWeek() == MCalendar.MONDAY) {
			for (int i = 2; i < shortDayNames.length; i++)
				dayLabels.add(new DayLabel(shortDayNames, longDayNames, i));
			dayLabels.add(new DayLabel(shortDayNames, longDayNames, 1));
		}
		else {
			for (int i = 1; i < shortDayNames.length; i++)
				dayLabels.add(new DayLabel(shortDayNames, longDayNames, i));
		}
		for (DayLabel i : dayLabels)
			dayNamesPanel.add(i);
		monthPanel.addNorth(dayNamesPanel);

		daysPanel = new MPanel(null) {
			@Override
			public void setComponentOrientation(final ComponentOrientation value) {
				super.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
			}
		};
		Mnemonic.setExcluded(daysPanel, true);
		daysPanel.setBackground(Color.WHITE);
		daysPanel.setForeground(Color.BLACK);
		daysPanel.setOpaque(true);

		transitionPanel = new TransitionPanel();
		monthPanel.addCenter(transitionPanel);
		
		p.addCenter(monthPanel);

		return p;
	}
	
	private MMenu createMonthMenu() {
		MMenu menu = new MMenu();
		
		DateFormatSymbols dateFormatSymbols = DateFormatSymbols.getInstance();
		int currentMonth = getMonth();
		
		// "An array of 13 strings (some calendars have 13 months)..."
		for (Loop<String> i : Loop.each(dateFormatSymbols.getMonths())) {
			if (!TK.isEmpty(i.v())) { // 13th month can be empty, ignore
				int m = i.i() + 1; // 0-based -> 1-based
				menu.addRadioButton(new MonthAction(i.toString(), m), m == currentMonth);
			}
		}
		
		return menu;
	}
	
	private void doAutoUpdate() {
		if (MMenu.getCurrentPopup() != null)
			return;
		
		MCalendar now = MCalendar.now();
		// update only if date has changed
		if (!now.isSameDate(lastAutoUpdateDate)) {
			MLogger.debug("calendar", "Auto update");
			MDate oldDate = getValue();
			setValue(now.toDate());

			fireValueChanged(oldDate, getValue(), ValueChangeReason.AUTO_UPDATE);
			doUserUpdate();
		}
	}

	private void doUserUpdate() {
		if (globalUpdater != null)
			globalUpdater.onUpdate(this);
		onUserUpdate();
	}
	
	private boolean isToday(final int day) {
		return (todayDay == day) && sameMonthAndYear;
	}

	private void update(final MDate oldDate, final boolean restoreFocus) {
		MCalendar old = MCalendar.now();
		old.setFirstDayOfWeek(calendar.getFirstDayOfWeek());
		old.setTime(oldDate);

		// time changed, do not update all components
		if (
			(daysPanel.getComponentCount() > 0) &&
			old.isSameDate(calendar) &&
			!old.isSameTime(calendar, false)
		) {
			updateDateSpinner();

			return;
		}

		final int MAX_ROWS = 6;
		
		// save focus
		Component focus = UI.getFocusOwner();
		int focusedDay = (focus instanceof DayButton) ? DayButton.class.cast(focus).day : -1;
		if (restoreFocus)
			requestFocusInWindow();

		final int year = calendar.getYear();
		final int month1 = getMonth();

		MCalendar now = MCalendar.now();
		now.setFirstDayOfWeek(calendar.getFirstDayOfWeek());

		final boolean doTransition =
			animationEnabled &&
			UI.animations.get() &&
			(calendar.getMonth() != old.getMonth());
		sameMonthAndYear = (now.getMonth() == month1) && (now.getYear() == year);

		final MDate newDate = getValue();

		transitionPanel.removeAll();
		if (doTransition && (daysPanel.getWidth() > 0) && (daysPanel.getHeight() > 0)) {
			MLabel oldViewLabel = new MLabel(MComponent.createScreenshot(daysPanel, MColor.WHITE));
			transitionPanel.add(oldViewLabel, "old");//!!!opt
		}

		daysPanel.removeAll();
		daysPanel.setLayout(new GridLayout(MAX_ROWS, 8));

		MCalendar startOfMonth = MCalendar.date(year, month1, 1);
		startOfMonth.setFirstDayOfWeek(calendar.getFirstDayOfWeek());

		int dayShift = (calendar.getFirstDayOfWeek() == MCalendar.SUNDAY) ? 1 : 0;
		switch (startOfMonth.getDayOfWeek()) {
			case MCalendar.MONDAY:
				// dayShift += 0;
				break;
			case MCalendar.TUESDAY:
				dayShift += 1;
				break;
			case MCalendar.WEDNESDAY:
				dayShift += 2;
				break;
			case MCalendar.THURSDAY:
				dayShift += 3;
				break;
			case MCalendar.FRIDAY:
				dayShift += 4;
				break;
			case MCalendar.SATURDAY:
				dayShift += 5;
				break;
			case MCalendar.SUNDAY:
				dayShift += 6;
				break;
		}

		DayButton dayButton;
		int max = calendar.getActualMaximum(MCalendar.DAY_OF_MONTH);

		// MLogger.debug("core", "dayShift = %d", dayShift);

		int col = 1;
		int row = 1;

		WeekLabel weekLabel = new WeekLabel(startOfMonth.getWeek(), row - 1);
		daysPanel.add(weekLabel);

		if (dayShift == 0 || dayShift == 7) {
			for (int i = 0; i < 7; i++)
				daysPanel.add(new EmptyCell());
			col = 1;
			row++;
			weekLabel.setText(null); // do not hide; show separator border
		}
		else {
			for (int i = 0; i < dayShift; i++) {
				daysPanel.add(new EmptyCell());
				col++;
			}
		}
		selectedDay = calendar.getDay();
		todayDay = now.getDay();
		
		int todayDayOfWeek = now.getDayOfWeek();
		for (DayLabel dayLabel : dayLabels) {
			if (sameMonthAndYear && (dayLabel.day == todayDayOfWeek))
				dayLabel.setBackground(MColor.BRICK_RED);
			else
				dayLabel.setBackground(MColor.WHITE);
		}

		DayButton newFocus = null;
		dayButtons.clear();
		for (int day = 1; day <= max; day++) {
			if (col == 1) {
				MCalendar c = MCalendar.date(year, month1, day);
				daysPanel.add(new WeekLabel(c.getWeek(), row - 1));
			}

			dayButton = new DayButton(day, row - 1);
			dayButtons.add(dayButton);
			daysPanel.add(dayButton);

			if (restoreFocus && (focusedDay != -1) && (focusedDay == day))
				newFocus = dayButton;

			if (col == 7) {
				col = 1;
				row++;
			}
			else {
				col++;
			}
		}
		while (row < 7) {
			daysPanel.add(new EmptyCell());
			if (col == 7) {
				col = 1;
				row++;
			}
			else {
				col++;
			}
		}

		updateDateSpinner();

		MDate time = MDate.now();
		String pattern;
		if (UI.isRTL(infoLabel))
			pattern = "<font size=\"+1\">{1}</font>&nbsp;&nbsp;<font size=\"+10\">{0}</font>";
		else
			pattern = "<font size=\"+10\">{0}</font>&nbsp;&nbsp;<font size=\"+1\">{1}</font>";
		infoLabel.setHTML("<b>" + MessageFormat.format(pattern, time.format("d"), time.format("EEEE")) + "</b>");

		updateRenderers(year, month1);

		daysPanel.validate(); // this fixes popup menu position

		transitionPanel.add(daysPanel, "new");

		validate();
		repaint();

		fireValueChanged(oldDate, newDate, ValueChangeReason.UPDATE);

		if (!doTransition) {
			transitionPanel.setTransition(Transition.NO_TRANSITION);
			transitionPanel.showCard("new");
		}
		else {
			// HACK:
			if (globalUpdater != null)
				globalUpdater.onUpdate(this);

			transitionPanel.setTransition(new MoveTransition(
				newDate.after(oldDate)
				? MoveTransition.Direction.LEFT
				: MoveTransition.Direction.RIGHT
			));
			transitionPanel.showNextCard();
		}
		
		if (newFocus != null)
			newFocus.requestFocusInWindow();
	}

	private void updateDateSpinner() {
		if (!inUpdate) {
			try {
				inUpdate = true;
				dateSpinner.setDate(calendar.toDate());
			}
			finally {
				inUpdate = false;
			}
		}
	}

	// public classes
	
	public abstract static class Renderer extends MRenderer<MCalendar> {
		
		// private
		
		private int day;
		private int month;
		private int year;
		private MArrayList<Item<?>> itemList = new MArrayList<>();

		// public
		
		public int getDay() { return day; }
		
		public int getMonth() { return month; }
		
		public int getYear() { return year; }

		public void setSpecialColor(final Color bg, final Color fg) {
			MLabel l = getLabel();
			l.setBackground(bg);
			l.setForeground(fg);
		}

		// protected
		
		/**
		 * @since 4.0
		 */
		protected void addItem(final String text, final Icon icon) {
			Item<Object> item = new Item<>();
			item.setIcon(icon);
			item.setText(text);
			itemList.add(item);
		}
		
		protected abstract void onRender();

		@Override
		protected final void onRender(final MCalendar value) {
			MLabel l = getLabel();
			l.setBackground(null);
			l.setForeground(null);
			l.setIcon(null);
			l.setToolTipText(null);
			itemList.clear();
			
			onRender();
		}
		
	}

	/**
	 * @since 3.8
	 */
	public abstract static class Updater {

		// protected

		protected abstract void onUpdate(final MCalendarPanel calendarPanel);

	}

	// private classes

	private final class DayButton extends MButton {

		// private

		private boolean mouseHover;
		private final int day;
		private final int row;
		private final MArrayList<Item<?>> itemList = new MArrayList<>();//!!!opt

		// public

		@Override
		public Dimension getMinimumSize() {
			return getPreferredSize();
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(32, getFont().getSize() + 8/* include border size */);
		}

		@Override
		public void setBackground(final Color value) {
			Color c = value;
			if ((c != null) && (row % 2 != 0))
				c = MColor.getDarker(c);

			super.setBackground(c);
		}

		@Override
		public void updateUI() {
			setUI(UI.getSimpleButtonUI());
		}

		// protected

		@Override
		protected void onClick() {
			if (day == 0)
				return;

			MCalendarPanel calendarPanel = MCalendarPanel.this;
			MDate oldDate = calendarPanel.getValue();
			calendar.set(MCalendar.DAY_OF_MONTH, day);
			calendarPanel.update(oldDate, true);
			calendarPanel.doUserUpdate();
			
			MMenu menu = calendarPanel.onClick(day);
			if (menu != null) {
				DayButton dayButton = calendarPanel.dayButtons.get(day - 1);
				menu.showPopup(dayButton.getParent(), dayButton);
			}
		}

		@Override
		protected void paintBorder(final Graphics g) {
			if (!isPaintingForPrint())
				super.paintBorder(g);
		}

		@Override
		protected void paintComponent(final Graphics graphics) {
			boolean paintItemList = !itemList.isEmpty() && (getHeight() > 32);
			boolean print = this.isPaintingForPrint();
			Color bg = UI.getBackground(this);
			Font font = UI.getFont(this);
			MGraphics2D g = MGraphics2D.copy(graphics);
			g.setAntialiasing(true);
			Graphics2D g2d = g.getGraphics2D();
			if (print)
				UI.setBasicTextAntialiasing(g2d, true);
			else
				g.setTextAntialiasing(null);

			// background
			
			if (!print) {
				if (paintItemList) {
					LinearGradientPaint gradient = new LinearGradientPaint(
						0, getHeight() - 1, getWidth() - 1, 0,
						new float[] { 0.0f, 0.3f, 1.0f },
						new Color[] { bg, MColor.getBrighter(bg, 50), Color.WHITE }
					);
					g2d.setPaint(gradient);
				}
				else {
					g.setColor(bg);
				}
				g.fillRect(this);
			}

			// day number

			Color shadowColor = (paintItemList && !print) ? bg : null;
			Color textColor = Color.BLACK;
			if (paintItemList)
				g.setAlpha(mouseHover ? 0.40f : 0.20f);
			
			int dayStyle = !print && MCalendarPanel.this.isToday(day) ? (Font.ITALIC + Font.BOLD) : Font.PLAIN;
			g.setFont(font.deriveFont(dayStyle, TK.limit(getHeight() / 2, 12, 32)));
			
			FontMetrics fm = g.getFontMetrics();
			String s = getText();
			int x = getWidth() - fm.stringWidth(s) - 4;
			int y = fm.getAscent() + 2;

			if (shadowColor != null) {
				g.setColor(shadowColor);
				g.drawString(s, x + 2, y + 2);
			}

			g.setColor(textColor);
			g.drawString(s, x, y);
			
			// actual item list

			if (paintItemList) {
				int d = print ? 0 : -1;
				g.setFont(font.deriveFont(Font.PLAIN, (float)TK.limit(font.getSize() + d, 10, 14)));
				fm = g.getFontMetrics();
				y = 2;
				int h = fm.getHeight();

				// arrow if list contains more items

				if ((itemList.size() * (h + 1)) > getHeight()) {
					int paddingX = 5;
					int w = getWidth();
					int ch = getHeight();
					g.setColor(MColor.GRAY);
					g2d.fill(MGraphics2D.createTriangle(
						paddingX, ch - 8,
						w / 2, ch - 3,
						w - paddingX - 1, ch - 8
					));
				}
				
				// items
				
				g.setAlpha(1.0f);
				g.setColor(Color.BLACK);
				for (Item<?> i : itemList) {
					x = 2;
					
					Icon icon = i.getIcon();
					if (icon != null) {
						x = icon.getIconWidth() + 2;
						h = Math.max(h, icon.getIconHeight());
						g.drawIcon(icon, 0, y);
					}
					
					String text = i.getText();
					if (!TK.isEmpty(text))
						g.drawString(text, x, y + fm.getAscent());
					
					y += h + 1;
					
					if (y > getHeight())
						break; // for
				}
			}
			
			// focus
			
			if (!print && isFocusOwner())
				UI.paintFocus(this, g2d, MColor.deriveColor(bg, 0.5f), 2);
	
			g.dispose();
		}
		
		@Override
		protected void processKeyEvent(final KeyEvent e) {
			if (e.getID() == KeyEvent.KEY_PRESSED) {
				switch (e.getKeyCode()) {
					case VK_UP:
						moveFocus(-7);
						break;
					case VK_DOWN:
						moveFocus(7);
						break;
					case VK_LEFT:
						moveFocus(-1);
						break;
					case VK_RIGHT:
						moveFocus(1);
						break;
				}
			}
			super.processKeyEvent(e);
		}
		
		// private

		private DayButton(final int day, final int row) {
			this.day = day;
			this.row = row;
			setCursor(Cursor.HAND_CURSOR);
			setOpaque(true);
			
			if (day == 0)
				setText("");
			else
				setText(Integer.toString(day));
			
			addMouseListener(new MMouseAdapter() {
				@Override
				public void mouseEntered(final MouseEvent e) {
					DayButton b = DayButton.this;
					b.setMouseHover(true);
					b.setPopupMenuArrowPainted(true);
					b.setPopupMenuEnabled(true);
				}
				@Override
				public void mouseExited(final MouseEvent e) {
					DayButton b = DayButton.this;
					b.setMouseHover(false);
					b.setPopupMenuArrowPainted(false);
					b.setPopupMenuEnabled(false);
				}
				@Override
				public void popupTrigger(final MouseEvent e) {
					TK.fireActionPerformed(DayButton.this, DayButton.this.getActionListeners());
				}
			} );
		}
		
		private void moveFocus(final int numDays) {
			int newDay = TK.limit(day + numDays, 1, MCalendarPanel.this.dayButtons.size());
			for (DayButton i : MCalendarPanel.this.dayButtons) {
				if ((i != this) && (i.day == newDay)) {
					i.requestFocusInWindow();
				
					break; // for
				}
			}
		}
		
		private void setMouseHover(final boolean newMouseHover) {
			if (newMouseHover != this.mouseHover) {
				this.mouseHover = newMouseHover;
				this.repaint();
			}
		}

	}
	
	private static final class DayLabel extends Label {
	
		// private
		
		private final int day;
	
		// private

		private DayLabel(final String[] shortDayNames, final String[] longDayNames, final int day) {
			super(shortDayNames[day]);
			this.day = day;
			setToolTipText(longDayNames[day]);
		}

	}

	private static final class EmptyCell extends MLabel {

		// private

		private EmptyCell() {
			setBackground(Color.WHITE);
			setForeground(Color.BLACK);
			setOpaque(true);
		}

	}

	private static class Label extends MLabel {
	
		// protected

		@Override
		protected void paintBorder(final Graphics g) {
			if (!isPaintingForPrint())
				super.paintBorder(g);
		}

		// private
		
		private Label(final String text) {
			this(text, true, MLineBorder.Position.BOTTOM);
		}

		private Label(final String text, final boolean bold, final MLineBorder.Position borderPosition) {
			super(text);
			Font font = UI.getFont(this);
			setFont(font.deriveFont(
				bold ? Font.BOLD : Font.PLAIN,
				(float)(font.getSize() - 1) // smaller
			));
			setHorizontalAlignment(CENTER);

			setBorder(new SeparatorBorder(borderPosition));
			setOpaque(true);
			setBackground(MColor.WHITE);
			setForeground(MColor.BLACK);
		}

	}
	
	private final class MonthAction extends MDataAction<Integer> {
		
		// public
		
		@Override
		public void onAction() {
			MCalendarPanel.this.setMonth(getData());
			MCalendarPanel.this.doUserUpdate();
		}
		
		// private
		
		private MonthAction(final String name, final int month) {
			super(month, name);

			if (monthMenuItemFont == null)
				monthMenuItemFont = new Font(Font.DIALOG, Font.BOLD, UI.getDefaultFontSize());

			int iconSize = monthMenuItemFont.getSize();
			ItemStatus.Icon icon = new ItemStatus.Icon(
				Integer.toString(month),
				UI.getBackground(MCalendarPanel.this.infoLabel),
				new Dimension(iconSize * 2, iconSize + 2),
				monthMenuItemFont
			);
			setSmallIcon(icon);
		}
		
	}

	private static final class SeparatorBorder extends MLineBorder {

		// private

		private SeparatorBorder(final Position position) {
			super(position);
			getStyle(position).setColor(MColor.getDarker(MColor.WHITE));
		}

	}

	private static final class WeekLabel extends Label {
	
		// private

		private WeekLabel(final int week, final int row) {
			super(Integer.toString(week), false, MLineBorder.Position.RIGHT);
			setForeground(MColor.deriveColor(MColor.WHITE, 0.5f));
			setToolTipText(_("Week: {0}", getText()));
			
			if (row % 2 != 0) {
				SeparatorBorder b = (SeparatorBorder)getBorder();
				SeparatorBorder.Style right = b.getRight();
				SeparatorBorder.Style top = b.getTop();
				SeparatorBorder.Style bottom = b.getBottom();

				top.setColor(right.getColor());
				top.setVisible(true);

				bottom.setColor(right.getColor());
				bottom.setVisible(true);
			}
		}

	}

}
