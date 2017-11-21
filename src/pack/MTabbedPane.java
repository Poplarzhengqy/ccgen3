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

package org.makagiga.commons.swing;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

import org.makagiga.commons.AbstractIterator;
import org.makagiga.commons.Config;
import org.makagiga.commons.MGraphics2D;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.swing.border.MLineBorder;

/**
 * A tabbed pane.
 * 
 * @since 4.0 (org.makagiga.commons.swing package)
 */
public class MTabbedPane<T extends JComponent> extends JTabbedPane
implements
	Config.IntRange,
	Iterable<T>,
	MouseWheelListener
{

	// private

	private static Insets oldInsets;
	private Insets currentBorderContentInsets;
	private MTimer paintShortcutTimer;
	
	// public

	/**
	 * Constructs a tabbed pane.
	 */
	public MTabbedPane() {
		addMouseWheelListener(this);
		setDropTarget(new TabDropTarget());
	}

	/**
	 * @since 3.8.8
	 */
	public void addTab(final MTab tab) {
		addTab(tab.tabTitle, tab);
		tab.tabTitle = null;
	}

	/**
	 * @since 3.8
	 */
	public void addTab(final String title, final T tab) {
		super.addTab(title, tab);
	}

	/**
	 * @since 3.8
	 */
	public void addTab(final String title, final Icon icon, final T tab) {
		super.addTab(title, icon, tab);
	}

	/**
	 * Adds a new tab.
	 * @param title A title
	 * @param iconName An icon name
	 * @param tab A component
	 */
	public void addTab(final String title, final String iconName, final T tab) {
		addTab(title, MIcon.stock(iconName), tab);
	}

	/**
	 * Adds a @c null tab.
	 * @param title A title
	 * @param iconName An icon name
	 */
	public void addTab(final String title, final String iconName) {
		addTab(title, MIcon.stock(iconName), null);
	}
	
	/**
	 * Returns the selected tab, or @c null if no selection.
	 */
	@SuppressWarnings("unchecked")
	public T getSelectedTab() {
		return (T)getSelectedComponent();
	}

	/**
	 * Returns tab at @p index, or @c null if no tab at that index.
	 */
	@SuppressWarnings("unchecked")
	public T getTabAt(final int index) {
		try {
			return (T)getComponentAt(index);
		}
		catch (IndexOutOfBoundsException exception) { // quiet
			return null;
		}
	}

	/**
	 * @deprecated As of 3.8, replaced by @ref setTabAt(final int, final T)
	 */
	@Deprecated
	@Override
	public void setComponentAt(final int index, final Component c) {
		super.setComponentAt(index, c);
	}

	/**
	 * @since 3.8
	 */
	public void setTabAt(final int index, final T tab) {
		super.setComponentAt(index, tab);
	}

	public int indexAtLocation(final Point p) {
		return indexAtLocation(p.x, p.y);
	}

	/**
	 * @since 3.8
	 */
	public void insertTab(final String title, final Icon icon, final T tab, final int index) {
		super.insertTab(title, icon, tab, null, index);
	}

	/**
	 * Returns @c true if no tabs.
	 */
	public boolean isEmpty() {
		return getTabCount() == 0;
	}

	public boolean isTabHeaderVisible() {
		return !(ui instanceof HiddenTabsUI);
	}
	
	public void setTabHeaderVisible(final boolean value) {
		if (value) {
			if (currentBorderContentInsets != null)
				setBorderContentInsets(currentBorderContentInsets);
			updateUI(); // reset UI
			if (currentBorderContentInsets != null)
				restoreBorderContentInsets();
		}
		else {
			if (ui instanceof HiddenTabsUI)
				return;
			
			setUI(new HiddenTabsUI());
		}
	}

	/**
	 * Returns the iterator.
	 */
	@Override
	public Iterator<T> iterator() {
		if (isEmpty())
			return Collections.emptyIterator();
	
		return new AbstractIterator<T>() {
			@Override
			public T getObjectAt(final int index) {
				return MTabbedPane.this.getTabAt(index);
			}
			@Override
			public int getObjectCount() {
				return MTabbedPane.this.getTabCount();
			}
		};
	}

	/**
	 * A mouse wheel event handler.
	 * It selects the next/previous tab.
	 * @param e A mouse wheel event
	 */
	public void mouseWheelMoved(final MouseWheelEvent e) {
		int index = indexAtLocation(e.getPoint());
		
		if (index == -1)
			return;

		if (e.getWheelRotation() < 0)
			selectPreviousTab();
		else
			selectNextTab();
		
		e.consume();
	}

	@Override
	public void setTabLayoutPolicy(final int tlp) {
		// HACK: A03: custom tab components are not supported
		//       GTK: NPE bug in older versions (?)
		super.setTabLayoutPolicy(
			(tlp == SCROLL_TAB_LAYOUT) && (UI.isA03() || UI.isGTK())
			? WRAP_TAB_LAYOUT
			: tlp
		);
	}

	/**
	 * @inheritDoc
	 *
	 * @mg.note This overriden method will automatically force {@code TOP} placement
	 * if the <code>LEFT/RIGHT</code> placement is not correctly supported
	 * by the current Look And Feel.
	 *
	 * @param tp the tab placement
	 */
	@Override
	public void setTabPlacement(final int tp) {
		if (UI.isA03()) {
			super.setTabPlacement(TOP);
		}
		else if (UI.isSubstance() && ((tp == LEFT) || (tp == RIGHT))) {
			super.setTabPlacement(TOP);
		}
		else {
			super.setTabPlacement(tp);
		}
	}

	// Config.IntRange

	/**
	 * @since 3.0
	 */
	public Config.IntInfo getIntInfo() {
		return new Config.IntInfo(0, getTabCount() - 1);
	}
	
	// protected

	/**
	 * CREDITS: http://www.jroller.com/page/santhosh?entry=get_rid_of_ugly_jtabbedpane
	 */
	protected static Insets createBorderContentInsetsForPlacement(final int placement) {
		final int SIZE = 4;
		switch (placement) {
			case LEFT: return new Insets(0, SIZE, 0, 0);
			case RIGHT: return new Insets(0, 0, 0, SIZE);
			case TOP: return new Insets(SIZE, 0, 0, 0);
			case BOTTOM: return new Insets(0, 0, SIZE, 0);
			default: throw new IllegalArgumentException("Invalid \"placement\" value: " + placement);
		}
	}

	@Override
	protected boolean processKeyBinding(final KeyStroke ks, final KeyEvent e, final int condition, final boolean pressed) {
		paintShortcutTimer = TK.dispose(paintShortcutTimer);

		if (e.getKeyCode() == VK_ALT) {
			int[] keys = Mnemonic.getTabKeys();
			int count = Math.min(keys.length, getTabCount());
			if (count > 3) {
				boolean needTimer = false;
				String alt = KeyEvent.getKeyModifiersText(KeyEvent.ALT_MASK);

				for (int i = 0; i < count; i++) {
					Component c = getTabComponentAt(i);
					if (c instanceof TabComponent) {
						String paintShortcut;
						if (pressed) {
							paintShortcut = KeyEvent.getKeyText(keys[i]);
							if (i == 0)
								paintShortcut = alt + "+" + paintShortcut;
						}
						else {
							paintShortcut = null;
						}
						TabComponent.class.cast(c).setPaintShortcut(paintShortcut);
						needTimer = true;
					}
				}

				if (needTimer && pressed) {
					paintShortcutTimer = new MTimer(TimeUnit.SECONDS, 2) {
						@Override
						protected boolean onTimeout() {
							int count = MTabbedPane.this.getTabCount();
							for (int i = 0; i < count; i++) {
								Component c = MTabbedPane.this.getTabComponentAt(i);
								if (c instanceof TabComponent)
									TabComponent.class.cast(c).setPaintShortcut(null);
							}

							return false;
						}
					};
					paintShortcutTimer.start();
				}
			}
		}

		return super.processKeyBinding(ks, e, condition, pressed);
	}

	protected synchronized void restoreBorderContentInsets() {
		if (!UI.isMetal())
			return;
		
		Insets insets = (Insets)UIManager.get("TabbedPane.contentBorderInsets");
		if (insets != null)
			currentBorderContentInsets = (Insets)insets.clone();
		synchronized (MTabbedPane.class) {
			if (oldInsets != null)
				UIManager.put("TabbedPane.contentBorderInsets", oldInsets);
		}
	}

	protected synchronized static void setBorderContentInsets(final Insets value) {
		if (!UI.isMetal())
			return;

		oldInsets = UIManager.getInsets("TabbedPane.contentBorderInsets");
		UIManager.put("TabbedPane.contentBorderInsets", value);
	}

	// private

	private void selectNextTab() {
		int count = getTabCount();

		if (count < 2)
			return;

		int current = getSelectedIndex();
		if (current == -1)
			current = 0;

		int index = current + 1;
		while (true) {
			if (index > count - 1)
				index = 0;

			if (index == current)
				return;

			if (isEnabledAt(index))
				break; // while

			index++;
		}
		setSelectedIndex(index);
	}

	private void selectPreviousTab() {
		int count = getTabCount();

		if (count < 2)
			return;

		int current = getSelectedIndex();
		if (current == -1)
			current = 0;

		int index = current - 1;
		while (true) {
			if (index < 0)
				index = count - 1;

			if (index == current)
				return;

			if (isEnabledAt(index))
				break; // while

			index--;
		}
		setSelectedIndex(index);
	}
	
	// public classes
	
	/**
	 * @since 3.0
	 */
	public static class TabComponent extends MPanel {

		// private

		private final MLabel text;
		private MLineBorder.Style colorStyle;
		private String paintShortcut;
		private final TabIcon icon;
		private final WeakReference<MTabbedPane<?>> tabs;

		// public

		public TabComponent(final MTabbedPane<?> tabs) {
			this.tabs = new WeakReference<MTabbedPane<?>>(tabs);
			setColorPosition(MLineBorder.Position.TOP);
			setOpaque(false);

			icon = new TabIcon(this);
			addWest(icon);

			text = new MLabel();
			text.setStyle("margin: 1 5 1 5");
			for (MouseListener i : text.getMouseListeners())
				text.removeMouseListener(i);
			addCenter(text);
		}
		
		@Override
		public Dimension getPreferredSize() {
			Dimension d = super.getPreferredSize();

			MTabbedPane<?> t = tabs.get();

			if (t == null)
				return d;

			int count = t.getTabCount();
			int defaultWidth = Math.min(Math.max(t.getWidth() / 2, 200), 300);
			int w = defaultWidth;
			int maxWidth = t.getWidth() - defaultWidth / 2;
			if (count * defaultWidth > maxWidth) {
				if (count > 0)
					w = Math.max(maxWidth / count, defaultWidth / 4);
			}

			return new Dimension(w, d.height);
		}

		/**
		 * @since 3.8.7
		 */
		public TabIcon getTabIcon() { return icon; }

		/**
		 * @since 3.4
		 */
		public MLabel getTextLabel() { return text; }

		@Override
		public void paint(final Graphics _graphics) {
			MTabbedPane<?> t = tabs.get();
			if ((t != null) && t.isTabHeaderVisible()) {
				super.paint(_graphics);

				if (paintShortcut != null) {
					MGraphics2D g = MGraphics2D.copy(_graphics);
					
					g.setAntialiasing(true);
					g.setFont(UI.deriveFontStyle(UI.getFont(this), Font.BOLD));
					g.setTextAntialiasing(null);

					g.setColor(new Color(50, 50, 50, 180));
					int padding = 5;
					FontMetrics fm = g.getFontMetrics();
					int x = 0;
					int y = 0;
					int w = fm.stringWidth(paintShortcut);
					int h = getHeight();
					g.fillRoundRect(x, y, w + (padding * 2), h, 16);

					g.setColor(Color.WHITE);
					g.drawString(
						paintShortcut,
						x + padding,
						y + (h / 2) - (fm.getHeight() / 2) + fm.getAscent()
					);

					g.dispose();
				}
			}
		}

		/**
		 * @since 3.8.7
		 */
		public void setColor(final Color color) {
			if ((colorStyle != null) && !Objects.equals(colorStyle.getColor(), color)) {
				colorStyle.setColor(color);
				repaint();
			}
		}

		/**
		 * @since 4.4
		 */
		public void setColorPosition(final MLineBorder.Position value) {
			MLineBorder border = new MLineBorder(value);
			colorStyle = border.getStyle(value);
			colorStyle.setColor(null);
			colorStyle.setSize(((value == MLineBorder.Position.TOP) || (value == MLineBorder.Position.BOTTOM)) ? 2 : 3);
			setBorder(border);
		}

		public void setIcon(final Icon value) {
			icon.setIcon(value);
		}

		public void setText(final String value) {
			text.setText(value);
		}

		// protected
		
		protected int getIndex() {
			MTabbedPane<?> t = tabs.get();
			
			return (t == null) ? -1 : t.indexOfTabComponent(this);
		}
		
		protected void onClick() { }

		// private

		private void setPaintShortcut(final String value) {
			if (!Objects.equals(value, paintShortcut)) {
				paintShortcut = value;
				repaint();
			}
		}

	}
	
	/**
	 * @since 3.0
	 */
	public static class TabIcon extends MButton {

		// private

		private final TabComponent tabComponent;

		// public

		public TabIcon(final TabComponent tabComponent) {
			this.tabComponent = Objects.requireNonNull(tabComponent);
			setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
			setContentAreaFilled(false);
			setFocusable(false);
			setOpaque(false);
			setRolloverEffectEnabled(false);
			setToolTipText(_("Close"));
			setSafeAction(true);
		}

		@Override
		public void updateUI() {
			setUI((ButtonUI)BasicButtonUI.createUI(this));
		}

		// protected

		@Override
		protected final void onClick() {
			tabComponent.onClick();
		}

	}
	
	// private classes
	
	private static final class HiddenTabsUI extends BasicTabbedPaneUI {
		
		// protected

		@Override
		protected int calculateMaxTabHeight(final int tabPlacement) {
			return 0;
		}

		@Override
		protected int calculateTabAreaHeight(final int tabPlacement, final int horizRunCount, final int maxTabWidth) {
			return 0;
		}
		
		@Override
		protected int calculateTabHeight(final int tabPlacement, final int tabIndex, final int fontHeight) {
			return 0;
		}

		@Override
		protected int calculateTabWidth(final int tabPlacement, final int tabIndex, final FontMetrics metrics) {
			return 0;
		}

		@Override
		protected Insets getContentBorderInsets(final int tabPlacement) {
			return UI.createInsets(0);
		}

		@Override
		protected Insets getSelectedTabPadInsets(final int tabPlacement) {
			return UI.createInsets(0);
		}

		@Override
		protected Insets getTabAreaInsets(final int tabPlacement) {
			return UI.createInsets(0);
		}

		@Override
		protected Insets getTabInsets(final int tabPlacement, final int tabIndex) {
			return UI.createInsets(0);
		}

	}

	private static final class TabDropTarget extends DropTarget {

		// public

		@Override
		public synchronized void dragEnter(final DropTargetDragEvent e) {
			super.dragEnter(e);
			doSelectTab(e);
		}
		
		@Override
		public synchronized void dragOver(final DropTargetDragEvent e) {
			super.dragOver(e);
			doSelectTab(e);
		}

		// private

		private TabDropTarget() { }

		private void doSelectTab(final DropTargetDragEvent e) {
			// select a tab under mouse cursor
			MTabbedPane<?> tabs = (MTabbedPane<?>)getComponent();
			int i = tabs.indexAtLocation(e.getLocation());
			if ((i != -1) && (tabs.getSelectedIndex() != i))
				tabs.setSelectedIndex(i);

			e.rejectDrag();
		}

	}

}
