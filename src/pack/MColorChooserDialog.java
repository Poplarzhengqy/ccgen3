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

package org.makagiga.commons.color;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.ParseException;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JColorChooser;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.undo.AbstractUndoableEdit;

import org.makagiga.commons.ColorProperty;
import org.makagiga.commons.Config;
import org.makagiga.commons.Kiosk;
import org.makagiga.commons.MAction;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MArrayList;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDataAction;
import org.makagiga.commons.MDisposable;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.TK;
import org.makagiga.commons.Tuple;
import org.makagiga.commons.UI;
import org.makagiga.commons.ValueEvent;
import org.makagiga.commons.ValueListener;
import org.makagiga.commons.annotation.InvokedFromConstructor;
import org.makagiga.commons.form.Field;
import org.makagiga.commons.form.Form;
import org.makagiga.commons.form.FormPanel;
import org.makagiga.commons.form.Info;
import org.makagiga.commons.swing.ActionGroup;
import org.makagiga.commons.swing.Input;
import org.makagiga.commons.swing.MButton;
import org.makagiga.commons.swing.MButtonPanel;
import org.makagiga.commons.swing.MDialog;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MLinkButton;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MMenuItem;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MSmallButton;
import org.makagiga.commons.swing.MText;
import org.makagiga.commons.swing.MTextField;
import org.makagiga.commons.swing.MTimer;
import org.makagiga.commons.swing.MToggleButton;
import org.makagiga.commons.swing.MUndoManager;
import org.makagiga.commons.swing.border.MLineBorder;

// TODO: 2.0: color history

/**
 * A color chooser dialog.
 * 
 * @since 4.0 (org.makagiga.commons.color.MColorChooserDialog name)
 */
public class MColorChooserDialog extends MDialog {

	// private

	private boolean canUpdateUndoManager;
	private ChangeListener changeListener;
	private Color color;
	private final Color defaultColor;
	private HTMLColorChooserPanel htmlColorChooserPanel;
	private JColorChooser colorChooser;
	private MArrayList <Tuple.Two<Color, String>> _bookmarks;
	private MColorPicker colorPicker;
	private final MLabel backgroundPreviewLabel;
	private final MLabel foregroundPreviewLabel;
	private final MLineBorder previewBorder;
	private MUndoManager undoManager;
	private PickerColorChooserPanel pickerColorChooserPanel;
	private ValueListener<Color> valueListener;

	// public

	/**
	 * Constructs a color chooser dialog.
	 *
	 * @param owner the owner window
	 * @param title A dialog title
	 * @param initialColor An initial color
	 * @param defaultColor A default color
	 */
	public MColorChooserDialog(final Window owner, final String title, final Color initialColor, final Color defaultColor) {
		super(owner, Objects.toString(title, _("Choose a Color")), STANDARD_DIALOG | USER_BUTTON | FORCE_STANDARD_BORDER);

		undoManager = new MUndoManager();
		undoManager.updateUserActions();

		MButtonPanel undoManagerPanel = new MButtonPanel(
			MButtonPanel.NO_REVERSE | MButtonPanel.NO_PAINTER,
			new MSmallButton(undoManager.getUndoAction(), false),
			new MSmallButton(undoManager.getRedoAction(), false)
		);
		getButtonsPanel().addContentGap();
		getButtonsPanel().add(undoManagerPanel);
		
		getButtonsPanel().addContentGap();
		MButton bookmarksButton = new MButton(_("Bookmarks")) {
			@Override
			protected MMenu onPopupMenu() {
				return MColorChooserDialog.this.createBookmarksMenu();
			}
		};
		bookmarksButton.setPopupMenuEnabled(true);
		getButtonsPanel().add(bookmarksButton);
		
		Icon defaultColorIcon =
			(defaultColor == null)
			? MActionInfo.RESTORE_DEFAULT_VALUES.getIcon()
			: (new MColorIcon(defaultColor));
		changeButton(getUserButton(), MActionInfo.RESTORE_DEFAULT_VALUES.getText(), defaultColorIcon);
		
		this.defaultColor = defaultColor;

		// init preview

		previewBorder = new MLineBorder();
		Border border = BorderFactory.createCompoundBorder(previewBorder, UI.createEmptyBorder(2));

		backgroundPreviewLabel = new MLabel(_("Preview"));
		backgroundPreviewLabel.setBorder(border);
		backgroundPreviewLabel.setOpaque(true);

		foregroundPreviewLabel = new MLabel(_("Preview"));
		foregroundPreviewLabel.setBackground(Color.WHITE);
		foregroundPreviewLabel.setBorder(border);
		foregroundPreviewLabel.setOpaque(true);

		colorPicker = new MColorPicker();
		colorPicker.setColorButtonVisible(false);

		if (colorPicker.colorPaletteComboBox != null) {
			Config config = getConfigPrivileged();
			colorPicker.readConfig(config, "ColorChooser");
		}
		
		addEast(colorPicker);

		MPanel previewPanel = MPanel.createHBoxPanel();
		previewPanel.add(backgroundPreviewLabel);
		previewPanel.add(foregroundPreviewLabel);

		// init color chooser
		UI.setWaitCursor(true);
		try {
			colorChooser = new JColorChooser();

			htmlColorChooserPanel = new HTMLColorChooserPanel();
			colorChooser.addChooserPanel(htmlColorChooserPanel);

			pickerColorChooserPanel = new PickerColorChooserPanel();
			colorChooser.addChooserPanel(pickerColorChooserPanel);

			colorChooser.setDragEnabled(Kiosk.actionDragDrop.get());
		}
		finally {
			UI.setWaitCursor(false);
		}
		colorChooser.setPreviewPanel(previewPanel);

		changeListener = new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				setValue(colorChooser.getSelectionModel().getSelectedColor());
			}
		};
		colorChooser.getSelectionModel().addChangeListener(changeListener);

		valueListener = new ValueListener<Color>() {
			@Override
			public void valueChanged(final ValueEvent<Color> e) {
				setValue(e.getNewValue());
			}
		};
		colorPicker.addValueListener(valueListener);
		
		addCenter(colorChooser);
		setValue(initialColor);
		if (initialColor == null)
			colorChooser.setColor(Color.BLACK);
		
		packFixed();

		canUpdateUndoManager = true;
	}

	/**
	 * Returns the selected color, or {@code null} if dialog has been cancelled.
	 * 
	 * @since 4.0
	 */
	public Color getValue() { return color; }

	/**
	 * Sets color to @p value.
	 * 
	 * @since 4.0
	 */
	@InvokedFromConstructor
	public void setValue(final Color newColor) {
		boolean updateModel = !Objects.equals(color, newColor);

		if (canUpdateUndoManager && updateModel) {
			undoManager.addEdit(new ColorEdit(color, newColor));
			undoManager.updateUserActions();
		}

		color = newColor;

		for (MLineBorder.Position i : MLineBorder.Position.values()) {
			MLineBorder.Style s = previewBorder.getStyle(i);
			s.setColor(color);
			s.setSize(2);
		}
		
		if (color == null) {
			backgroundPreviewLabel.setEnabled(false);
			foregroundPreviewLabel.setEnabled(false);
		}
		else {
			backgroundPreviewLabel.setEnabled(true);
			foregroundPreviewLabel.setEnabled(true);

			if (updateModel)
				colorChooser.setColor(color);
			
			backgroundPreviewLabel.setBackground(color);
			backgroundPreviewLabel.setForeground(MColor.getContrastBW(color));
			
			foregroundPreviewLabel.setForeground(color);
		}
		colorPicker.setValue(color);
	}

	// protected

	@Override
	protected void onClose() {
		if (colorPicker != null) {
			final String colorPaletteName = colorPicker.getSelectedColorPalette().toString();
			AccessController.doPrivileged(new PrivilegedAction<Void>() {
				@Override
				public Void run() {
					Config config = Config.getDefault();
					config.write("ColorChooser.colorPaletteName", colorPaletteName);
					config.sync();

					return null;
				}
			} );
		}
		
		if (colorChooser != null) {
			if (changeListener != null) {
				colorChooser.getSelectionModel().removeChangeListener(changeListener);
				changeListener = null;
			}

			if (valueListener != null) {
				colorPicker.removeValueListener(valueListener);
				valueListener = null;
			}

			if (htmlColorChooserPanel != null) {
				colorChooser.removeChooserPanel(htmlColorChooserPanel);
				htmlColorChooserPanel.actionGroup = null;
				htmlColorChooserPanel.value = null;
				htmlColorChooserPanel = null;
			}

			if (pickerColorChooserPanel != null) {
				pickerColorChooserPanel.dispose();
				colorChooser.removeChooserPanel(pickerColorChooserPanel);
				pickerColorChooserPanel = null;
			}

			colorChooser = null;
			colorPicker = null;
		}

		undoManager = TK.dispose(undoManager);
	}

	@Override
	protected void onUserClick() {
		color = defaultColor;
		accept();
	}

	// private
	
	private MMenu createBookmarksMenu() {
		MArrayList<Tuple.Two<Color, String>> bookmarks = getBookmarks();
	
		MMenu menu = new MMenu();
		
		Color currentColor = getValue();
		Tuple.Two<Color, String> currentBookmark = null;
		for (Tuple.Two<Color, String> i : bookmarks) {
			if (i.get1().equals(currentColor)) {
				currentBookmark = i;
				
				break; // for
			}
		}
		
		if (currentBookmark != null) {
			menu.add(new MDataAction<Tuple.Two<Color, String>>(currentBookmark, _("Remove Bookmark"), "ui/clearright") {
				@Override
				public void onAction() {
					MColorChooserDialog.this.getBookmarks().remove(this.getData());
					MColorChooserDialog.this.syncBookmarks();
				}
			} );
		}
		else {
			menu.add(new MDataAction<Color>(currentColor, _("Add Bookmark"), "ui/bookmark") {
				@Override
				public void onAction() {
					Color color = this.getData();
				
					ColorPalette palette;
					if (MColorChooserDialog.this.colorPicker != null)
						palette = MColorChooserDialog.this.colorPicker.getSelectedColorPalette();
					else
						palette = ColorPalette.getNamedColors();
				
					String bookmarkName = new Input.GetTextBuilder()
						.allowEmptyText(true)
						.autoCompletion("color-bookmark-name")
						.icon("ui/bookmark")
						.label(_("Name:"))
						.text(palette.getDisplayName(color))
						.title(this.getName())
						.exec(this);
					
					if (bookmarkName == null)
						return;
					
					MColorChooserDialog.this.getBookmarks().add(Tuple.of(color, bookmarkName));
					MColorChooserDialog.this.sortBookmarks();
					MColorChooserDialog.this.syncBookmarks();
				}
			} );
		}
		
		menu.addSeparator();
		
		for (Tuple.Two<Color, String> i : bookmarks) {
			Color color = i.get1();
			String name = i.get2();
			String toolTipText = TK.toUpperCase(ColorProperty.toString(color));
			if (name.isEmpty())
				name = toolTipText;
			
			MMenuItem item = menu.add(new MDataAction<Color>(color, name, MColorIcon.smallRectangle(color)) {
				@Override
				public void onAction() {
					MColorChooserDialog.this.setValue(this.getData());
				}
			} );
			item.setToolTipText(toolTipText);
			if (color.equals(currentColor))
				item.setStyle("font-weight: bold");
		}
		
		if (bookmarks.isEmpty())
			menu.addTitle(_("No Bookmarks"), "ui/info");

		return menu;
	}
	
	private MArrayList<Tuple.Two<Color, String>> getBookmarks() {
		if (_bookmarks != null)
			return _bookmarks;
	
		_bookmarks = new MArrayList<>();
		
		Config config = getConfigPrivileged();
		int bookmarkCount = config.readInt("ColorChooser.bookmarks.count", 0, 0, Integer.MAX_VALUE);
		for (int i = 0; i < bookmarkCount; i++) {
			Color color = config.readColor("ColorChooser.bookmarks.color." + (i + 1), null);
			String name = config.read("ColorChooser.bookmarks.name." + (i + 1), null);
			if ((color != null) && (name != null))
				_bookmarks.add(Tuple.of(color, name));
		}
		MColorChooserDialog.this.sortBookmarks();
		
		return _bookmarks;
	}

	private Config getConfigPrivileged() {
		SecurityManager sm = System.getSecurityManager();

		if (sm == null)
			return Config.getDefault();

		return AccessController.doPrivileged(new PrivilegedAction<Config>() {
			@Override
			public Config run() {
				return Config.getDefault();
			}
		} );
	}
	
	private void sortBookmarks() {
		_bookmarks.sort(new Comparator<Tuple.Two<Color, String>>() {
			@Override
			public int compare(final Tuple.Two<Color, String> t1, final Tuple.Two<Color, String> t2) {
				Color c1 = t1.get1();
				Color c2 = t2.get1();
				
				return Integer.compare(c1.getRGB(), c2.getRGB()) * -1;
			}
		} );
	}

	private void syncBookmarks() {
		AccessController.doPrivileged(new PrivilegedAction<Void>() {
			@Override
			public Void run() {
				Config config = Config.getDefault();
				config.write("ColorChooser.bookmarks.count", _bookmarks.size());
				int index = 0;
				for (Tuple.Two<Color, String> i : _bookmarks) {
					index++;
					config.write("ColorChooser.bookmarks.color." + index, i.get1());
					config.write("ColorChooser.bookmarks.name." + index, i.get2());
				}
				
				// clean up
				if (_bookmarks.isEmpty()) {
					config.removeAllValues(Pattern.compile("Color\\.ColorChooser\\.bookmarks\\.color\\..+"));
					config.removeAllValues(Pattern.compile("String\\.ColorChooser\\.bookmarks\\.name\\..+"));
				}
					
				config.sync();
				
				return null;
			}
		} );
	}

	// package

	void setSelectedColorPalette(final ColorPalette palette) {
		colorPicker.setSelectedColorPalette(palette);
	}
	
	// private classes

	private final class ColorEdit extends AbstractUndoableEdit {

		// private

		private final Color newColor;
		private final Color oldColor;

		// public

		@Override
		public void redo() {
			super.redo();
			this.setColor(newColor);
		}

		@Override
		public void undo() {
			super.undo();
			this.setColor(oldColor);
		}

		// private

		private ColorEdit(final Color oldColor, final Color newColor) {
			this.oldColor = oldColor;
			this.newColor = newColor;
		}

		private void setColor(final Color color) {
			try {
				MColorChooserDialog.this.canUpdateUndoManager = false;
				MColorChooserDialog.this.setValue(color);
			}
			finally {
				MColorChooserDialog.this.canUpdateUndoManager = true;
			}
		}

	}
	
	@Form(
		order = {
			"examples", "example1", "example2",
			"www", "colourLovers", "colorMixers", "colorSchemer", "colorSchemer2"
		}
	)
	@edu.umd.cs.findbugs.annotation.SuppressWarnings("UUF_UNUSED_FIELD")
	private static final class HTMLColorChooserForm {

		// examples
		
		@Info(type = Info.TYPE_SEPARATOR)
		private final String examples = _("Examples");

		@Info(type = Info.TYPE_HTML)
		private final String example1 = _("{0} - red color", "<code>#ff0000</code>, <code>ff0000</code>, <code>f00</code>");

		@Info(type = Info.TYPE_HTML)
		private final String example2 = _("{0} - light orange", "<code>#FFA858</code>");

		// color schemers

		@Info(type = Info.TYPE_SEPARATOR)
		private final String www = "WWW";

		@Field(label = "COLOURlovers :: Color Trends & Palettes")
		private final URI colourLovers = URI.create("http://www.colourlovers.com/");

		@Field(label = "ColorMatch Remix")
		private final URI colorMixers = URI.create("http://www.colormixers.com/");

		@Field(label = "Color Schemer Online")
		private final URI colorSchemer = URI.create("http://www.colorschemer.com/online.html");

		@Field(label = "Color Scheme Designer")
		private final URI colorSchemer2 = URI.create("http://colorschemedesigner.com/");

	}
	
	private static final class HTMLColorChooserPanel extends MColorChooserPanel {
		
		// private
		
		private ActionGroup actionGroup;
		private boolean inUpdate;
		private FormPanel<HTMLColorChooserForm> formPanel;
		private MLabel status;
		private MTextField value;
		
		// public
		
		@Override
		public void updateChooser() {
			if (!inUpdate) {
				Color color = getColorFromModel();
				
				// HACK: alpha not supported yet...
				if ((color != null) && MColor.hasAlpha(color))
					color = MColor.deriveAlpha(color, 255);
					
				String s = ColorProperty.toString(color);
				if (!Objects.equals(s, value.getText())) {
					value.setText(s);

					MLinkButton b;

					b = formPanel.getComponent("colourLovers");
					b.setURI("http://www.colourlovers.com/color/" + TK.toUpperCase(s.substring(1)));

					b = formPanel.getComponent("colorMixers");
					b.setURI("http://www.colormixers.com/?color=" + s.substring(1));
				}
			}
		}

		// protected

		@Override
		protected void buildChooser() {
			super.buildChooser();
			
			actionGroup = new ActionGroup();
			actionGroup.add("copy", new MAction(MActionInfo.COPY) {
				@Override
				public void onAction() {
					MText.copyAll(value);
				}
			} );

			actionGroup.add("paste", new MAction(MActionInfo.PASTE) {
				@Override
				public void onAction() {
					value.clear();
					value.paste();
				}
			} );

			value = new MTextField(7) {
				@Override
				protected void onChange(final DocumentEvent e) {
					HTMLColorChooserPanel.this.updateActions();
					HTMLColorChooserPanel.this.updateColor();
				}
			};
			value.setStyle("font-family: " + Font.MONOSPACED + "; font-size: larger");
			MText.setMaximumLength(value, 7);
			
			MPanel valuePanel = MPanel.createHBoxPanel();
			valuePanel.add(MLabel.createFor(value, _("Value:")));
			valuePanel.addContentGap();
			valuePanel.add(value);

			status = new MLabel();
			valuePanel.addContentGap();
			valuePanel.add(status);

			valuePanel.addContentGap();
			valuePanel.add(new MSmallButton(actionGroup.getAction("copy"), false));
			valuePanel.add(new MSmallButton(actionGroup.getAction("paste"), false));

			MPanel p = MPanel.createVBoxPanel();
			p.add(valuePanel);
			p.addContentGap();
			formPanel = new FormPanel<>(new HTMLColorChooserForm());
			p.add(formPanel);
			add(p);
		}
		
		// private

		private HTMLColorChooserPanel() {
			super("HTML");
		}
		
		private void updateActions() {
			actionGroup.setEnabled("copy", !value.isEmpty());
		}

		private void updateColor() {
			try {
				inUpdate = true;
				Color color = ColorProperty.parseColor(value.getText());
				setSelectedColor(color);
				status.setIcon(MIcon.small("ui/ok"));
				status.setToolTipText(_("OK"));
			}
			catch (ParseException exception) {
				status.setIcon(MIcon.small("ui/error"));
				status.setToolTipText(_("Error"));
			}
			finally {
				inUpdate = false;
			}
		}
		
	}

	/**
	 * Based on the idea from AB5k widget.
	 */
	private static final class PickerColorChooserPanel extends MColorChooserPanel implements MDisposable {

		// private

		private boolean errorSet;
		private Color color;
		private MLabel imageLabel;
		private MLabel valueLabel;
		private MTimer updateTimer;
		private MToggleButton enabled;
		private final Point lastMouseLocation = new Point();
		private Robot bender;

		// public

		@Override
		public void updateChooser() {
			if (color == null)
				return;

			String bg = ColorProperty.toString(color);
			String fg = ColorProperty.toString(UI.getXorColor(color));
			valueLabel.setHTML(String.format(
				"HTML: <code style=\"%s\">&nbsp;%s&nbsp;</code><br>" +
				"RGB: %d, %d, %d<br>" +
				"X: %d, Y: %d",
				"background-color: " + bg + "; color: " + fg + "; font-weight: bold",
				bg,
				color.getRed(),
				color.getGreen(),
				color.getBlue(),
				lastMouseLocation.x,
				lastMouseLocation.y
			));
		}

		// MDisposable

		/**
		 * @inheritDoc
		 * 
		 * @since 4.0
		 */
		@Override
		public void dispose() {
			bender = null;
			enabled = null;
			imageLabel = null;
			updateTimer = TK.dispose(updateTimer);
			valueLabel = null;
		}

		// protected

		@Override
		protected void buildChooser() {
			super.buildChooser();
			setLayout(new BorderLayout(MPanel.DEFAULT_CONTENT_MARGIN, MPanel.DEFAULT_CONTENT_MARGIN));

			enabled = new MToggleButton(_("Start"), MIcon.small("player/start")) {
				@Override
				protected void onClick() {
					if (!this.isSelected()) {
						this.setIcon(MIcon.small("player/start"));
						this.setText(_("Start"));

						// update selected color
						if (PickerColorChooserPanel.this.color != null)
							PickerColorChooserPanel.this.setSelectedColor(PickerColorChooserPanel.this.color);
					}
					else {
						this.setIcon(MIcon.small("player/pause"));
						this.setText(_("Press {0} to Stop", TK.toString(VK_SPACE, 0)));

						this.requestFocusInWindow();
					}
				}
			};
			add(enabled, BorderLayout.NORTH);

			imageLabel = new MLabel();
			add(imageLabel);

			valueLabel = new MLabel();
			add(valueLabel, BorderLayout.SOUTH);

			updateTimer.start();
		}

		// private

		private PickerColorChooserPanel() {
			super(_("Color Picker"));

			updateTimer = new MTimer(100) {
				@Override
				protected boolean onTimeout() {
					PickerColorChooserPanel.this.getColorFromScreen();

					return true;
				}
			};
		}

		private void createRobot() {
			if (bender != null)
				return;

			if (errorSet)
				return;

			try {
				bender = new Robot();
			}
			catch (Exception exception) {
				MLogger.exception(exception);

				enabled.setEnabled(false);
				errorSet = true;
				valueLabel.makeLargeMessage();
				valueLabel.setIconName("ui/error");
				valueLabel.setText(exception.getMessage());
			}
		}

		private void getColorFromScreen() {
			if (!enabled.isSelected())
				return;

			createRobot();

			if (bender == null)
				return;

			PointerInfo pointerInfo = MouseInfo.getPointerInfo();
			if (pointerInfo == null) { // no mouse?
				imageLabel.setIconName("ui/error");
				imageLabel.setText(_("Error"));
				
				return;
			}
			
			Point location = pointerInfo.getLocation();

			if (location.equals(lastMouseLocation))
				return;

			lastMouseLocation.setLocation(location);

			color = bender.getPixelColor(location.x, location.y);

			int size = 30;
			Dimension screenSize = UI.getScreenSize();
			int centerX = location.x - (size / 2);
			int centerY = location.y - (size / 2);
			Rectangle r = new Rectangle(centerX, centerY, size, size);

			// do not capture "offscreen" image
			if (r.x < 0)
				r.x = 0;
			else if (r.x + size > screenSize.width)
				r.x = screenSize.width - size;
			if (r.y < 0)
				r.y = 0;
			else if (r.y + size > screenSize.height)
				r.y = screenSize.height - size;

			BufferedImage screenshot = bender.createScreenCapture(r);

			int zoom = 5;
			int zoomSize = size * zoom;
			BufferedImage icon = UI.createCompatibleImage(zoomSize, zoomSize, false);
			Graphics2D g = icon.createGraphics();
			g.setColor(Color.PINK);
			g.fillRect(0, 0, zoomSize, zoomSize);

			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(
				screenshot,
				(r.x - centerX) * zoom,
				(r.y - centerY) * zoom,
				zoomSize,
				zoomSize,
				null
			);

			g.setColor(Color.RED);
			g.drawRect(zoomSize / 2, zoomSize / 2, zoom, zoom);

			g.dispose();
			imageLabel.setImage(icon);

			updateChooser();
		}

	}

}
