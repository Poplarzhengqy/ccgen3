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

package org.makagiga.commons.color;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.beans.ConstructorProperties;
import java.lang.ref.WeakReference;
import java.util.Objects;

import org.makagiga.commons.MAction;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.ValueEvent;
import org.makagiga.commons.ValueListener;
import org.makagiga.commons.annotation.InvokedFromConstructor;
import org.makagiga.commons.form.FieldEditor;
import org.makagiga.commons.painters.FlatPainter;
import org.makagiga.commons.painters.GradientPainter;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.ValueChooser;
import org.makagiga.commons.swing.event.MMenuAdapter;

/**
 * A small color chooser.
 * 
 * @since 4.0 (org.makagiga.commons.color package)
 */
@FieldEditor(type = Color.class, valueProperty = ValueChooser.VALUE_PROPERTY, labelProperty = "title")
public class MSmallColorChooser extends MPanel implements ValueChooser<Color> {

	// private

	private boolean resetActionVisible;
	private boolean useDefaultPrefSize;
	private ColorPalette colorPalette;
	private MColorButton colorButton;
	private MLabel titleLabel;
	private WeakReference<MSmallColorChooser> pairRef;
	
	// public

	/**
	 * Constructs a small color chooser.
	 * @param title the title text
	 * @param colorPalette A set of colors displayed in the panel
	 *
	 * @throws NullPointerException If {@code colorPalette} is {@code null}
	 *
	 * @since 4.0
	 */
	public MSmallColorChooser(final String title, final ColorPalette colorPalette) {
		super(UI.HORIZONTAL);
		setOpaque(false);
		this.colorPalette = Objects.requireNonNull(colorPalette);

		colorButton = new MColorButton() {
			@Override
			protected MMenu onPopupMenu() {
				return MSmallColorChooser.this.createColorMenu();
			}
		};
		if (UI.isRetro())
			colorButton.icon.setType(MColorIcon.Type.RECTANGLE_SIMPLE);
		colorButton.setPopupMenuEnabled(true);
		colorButton.addValueListener(new ValueListener<Color>() {
			@Override
			public void valueChanged(final ValueEvent<Color> e) {
				// forward event
				MSmallColorChooser.this.firePropertyChange(MSmallColorChooser.VALUE_PROPERTY, e.getOldValue(), e.getNewValue());
				TK.fireValueChanged(MSmallColorChooser.this, MSmallColorChooser.this.getValueListeners(), e.getOldValue(), e.getNewValue());
			}
		} );
		add(colorButton);

		titleLabel = new MLabel();
		titleLabel.setLabelFor(colorButton);
		titleLabel.setStyle("font-size: smaller; margin-left: 10; margin-right: 2");
		add(titleLabel);
		setTitle(title);

		setMargin(2);
		if (UI.isRetro()) {
			// set non-null painter
			setPainter(new FlatPainter(getBackground()));
		}
		else {
			GradientPainter painter = new GradientPainter();
			painter.setRounded(true);
			setPainter(painter);
		}
	}

	/**
	 * Constructs a small color chooser with default set of colors.
	 * @param title the title label
	 */
	@ConstructorProperties("title")
	public MSmallColorChooser(final String title) {
		this(title, ColorPalette.getApplicationPalette());
	}

	/**
	 * Constructs a small color chooser with default set of colors.
	 */
	public MSmallColorChooser() {
		this(null);
	}
	
	/**
	 * @since 4.2
	 */
	public MColorButton getColorButton() { return colorButton; }

	/**
	 * @since 4.0
	 */
	public Color getDefaultValue() {
		return colorButton.getDefaultValue();
	}

	/**
	 * @since 4.0
	 */
	public void setDefaultValue(final Color value) {
		colorButton.setDefaultValue(value);
	}

	@Override
	public Dimension getMaximumSize() {
		return getPreferredSize();
	}

	@Override
	public Dimension getPreferredSize() {
		Dimension thisPref = super.getPreferredSize();
		
		if (useDefaultPrefSize)
			return thisPref;
		
		// equal width for two pair components
		MSmallColorChooser pair = TK.get(pairRef);
		if (pair != null) {
			try {
				pair.useDefaultPrefSize = true;
				Dimension pairPref = pair.getPreferredSize();

				if (pairPref.width > thisPref.width)
					return new Dimension(pairPref.width, thisPref.height);
			}
			finally {
				pair.useDefaultPrefSize = false;
			}
		}
		
		return thisPref;
	}

	@Override
	public String getTitle() {
		return titleLabel.getText();
	}

	@InvokedFromConstructor
	@Override
	public void setTitle(final String value) {
		titleLabel.setText(TK.isEmpty(value) ? _("Color") : value);
	}

	/**
	 * @since 3.8.2
	 */
	public MLabel getTitleLabel() { return titleLabel; }

	/**
	 * Enables/disables this component and its subcomponents.
	 * @param value @c true - enable
	 */
	@Override
	public void setEnabled(final boolean value) {
		super.setEnabled(value);
		for (Component i : getComponents())
			i.setEnabled(value);
	}

	/**
	 * @since 4.0
	 */
	public boolean isResetActionVisible() { return resetActionVisible; }

	/**
	 * @since 4.0
	 */
	public void setResetActionVisible(final boolean value) { resetActionVisible = value; }
	
	/**
	 * @since 4.2
	 */
	public void makePair(final MSmallColorChooser another) {
		if (another == this)
			throw new IllegalArgumentException("another == this");
	
		this.pairRef = new WeakReference<>(another);
		another.pairRef = new WeakReference<>(this);
	}

	// ValueChooser

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	public void addValueListener(final ValueListener<Color> l) {
		listenerList.add(ValueListener.class, l);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	public void removeValueListener(final ValueListener<Color> l) {
		listenerList.remove(ValueListener.class, l);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	@SuppressWarnings("unchecked")
	public ValueListener<Color>[] getValueListeners() {
		return listenerList.getListeners(ValueListener.class);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	public Color getValue() {
		return colorButton.getValue();
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	public void setValue(final Color value) {
		Color oldColor = getValue();
		colorButton.setValue(value);
		if (!Objects.equals(oldColor, value))
			firePropertyChange(VALUE_PROPERTY, oldColor, value);
	}

	// protected

	/**
	 * @since 3.8.1
	 */
	protected MMenu createColorMenu() {
		ColorPaletteMenu menu = new ColorPaletteMenu(colorPalette, this, false);

		menu.add(new MAction(_("More...")) {
			@Override
			public void onAction() {
				MSmallColorChooser.this.colorButton.selectColor();
			}
		} );

		menu.addSeparator();

		for (ColorPalette i : ColorPalette.getAll()) {
			// skip large palettes
			if (i.getCount() > 150)
				continue; // for

			// already in menu
			if (i == colorPalette)
				continue; // for

			if (
				"Gold".equals(i.toString()) ||
				"Grays".equals(i.toString()) ||
				"Paintjet".equals(i.toString())
			)
				continue; // for

			menu.add(new ColorPaletteMenu(i, this, true));
		}
		
		if (resetActionVisible) {
			menu.addSeparator();
			menu.add(new MAction(MActionInfo.RESTORE_DEFAULT_VALUES) {
				@Override
				public void onAction() {
					MSmallColorChooser.this.setValueAndFireEvent(null);
				}
			} )
				.setIcon(MIcon.getNonDistractiveInstance(MActionInfo.RESTORE_DEFAULT_VALUES.getSmallIcon()));
		}
		
		final MSmallColorChooser pair = TK.get(pairRef);
		if (pair != null) {
			MMenu autoMenu = new MMenu(_("Use \"{0}\"", pair.getTitle()));

			MAction swapColorAction = new MAction(_("Swap")) {
				@Override
				public void onAction() {
					MSmallColorChooser self = MSmallColorChooser.this;
					// HACK: fix property change event order
					if (self.getY() < pair.getY()) {
						Color value = self.getValue();
						self.setValueAndFireEvent(pair.getValue());
						pair.setValueAndFireEvent(value);
					}
					else {
						Color value = pair.getValue();
						pair.setValueAndFireEvent(self.getValue());
						self.setValueAndFireEvent(value);
					}
				}
			};
			swapColorAction.setEnabled(!Objects.equals(getValue(), pair.getValue()));
			autoMenu.add(swapColorAction);

			MAction copyColorAction = new MAction(_("Copy")) {
				@Override
				public void onAction() {
					MSmallColorChooser.this.setValueAndFireEvent(pair.getValue());
				}
			};
			copyColorAction.setEnabled(!Objects.equals(getValue(), pair.getValue()));
			autoMenu.add(copyColorAction);

			autoMenu.add(new MAction(_("Contrast ({0})", _("Color"))) {
				@Override
				public void onAction() {
					MSmallColorChooser.this.setValueAndFireEvent(MColor.getContrast(pair.getValue()));
				}
			} );

			autoMenu.add(new MAction(_("Contrast ({0})", "black/white")) {
				@Override
				public void onAction() {
					MSmallColorChooser.this.setValueAndFireEvent(MColor.getContrastBW(pair.getValue()));
				}
			} );
			
			menu.add(autoMenu);
		}

		return menu;
	}

	// private

	private void setValueAndFireEvent(final Color value) {
		Color oldColor = getValue();
		setValue(value);
		TK.fireValueChanged(this, getValueListeners(), oldColor, getValue());
	}
	
	// private classes

	private static final class ColorPaletteMenu extends MMenu {

		// private

		private final ColorPalette colorPalette;
		private final WeakReference<MSmallColorChooser> chooserRef;

		// private

		private ColorPaletteMenu(final ColorPalette colorPalette, final MSmallColorChooser chooser, final boolean lazyInit) {
			super(colorPalette.toString(), colorPalette);
			setPopupMenuVerticalAlignment(CENTER);
			this.colorPalette = colorPalette;
			this.chooserRef = new WeakReference<>(chooser);
			if (lazyInit)
				addMenuListener(new ColorPaletteMenuAdapter());
			else
				init();
		}
		
		private void init() {
			MColorPicker colorPicker = new MColorPicker(colorPalette, true);
			colorPicker.addValueListener(new ValueListener<Color>() {
				@Override
				public void valueChanged(final ValueEvent<Color> e) {
					MSmallColorChooser chooser = TK.get(ColorPaletteMenu.this.chooserRef);
					chooser.setValueAndFireEvent(e.getNewValue());
					MMenu.hideCurrentPopup();
				}
			} );
			add(colorPicker);
		}

	}

	private static final class ColorPaletteMenuAdapter extends MMenuAdapter {

		// protected

		@Override
		protected void onSelect(final MMenu menu) {
			ColorPaletteMenu cpm = (ColorPaletteMenu)menu;
			if (cpm.isEmpty())
				cpm.init();
		}

		// private

		private ColorPaletteMenuAdapter() {
			super(false);
		}

	}
	
}
