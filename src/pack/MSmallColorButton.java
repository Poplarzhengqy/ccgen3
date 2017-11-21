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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.Objects;

import org.makagiga.commons.ColorProperty;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.ValueListener;
import org.makagiga.commons.swing.MComponent;
import org.makagiga.commons.swing.MSmallButton;
import org.makagiga.commons.swing.ValueChooser;

/**
 * A small color button.
 *
 * @since 2.0, 4.0 (org.makagiga.commons.color package)
 */
public class MSmallColorButton extends MSmallButton implements ValueChooser<Color> {
	
	// private

	private Color color;
	
	// package
	
	MColorIcon icon;
	
	// public

	/**
	 * Constructs a small color button with black color and rectangle shape.
	 * The button size is 32x16.
	 */
	public MSmallColorButton() {
		this(Color.BLACK, new Dimension(32, 16), MColorIcon.Type.RECTANGLE);
	}

	/**
	 * Constructs a small color button.
	 * @param color A default button color
	 * @param size A button size
	 * @param type A button shape (e.g. @c Shape.RECTANGLE)
	 *
	 * @throws NullPointerException If @p color is @c null
	 */
	public MSmallColorButton(final Color color, final Dimension size, final MColorIcon.Type type) {
		icon = new MColorIcon(color, MColorIcon.COMPONENT_SIZE);
		icon.setType(type);
		setIcon(icon);
		
		setValue(color);
		setFocusPainted(false); // painted by icon
		MComponent.setFixedSize(this, size);
	}

	/**
	 * @since 2.4
	 */
	@Override
	public void addValueListener(final ValueListener<Color> l) {
		listenerList.add(ValueListener.class, l);
	}

	/**
	 * @since 2.4
	 */
	@Override
	@SuppressWarnings("unchecked")
	public ValueListener<Color>[] getValueListeners() {
		return listenerList.getListeners(ValueListener.class);
	}

	/**
	 * @since 2.4
	 */
	@Override
	public void removeValueListener(final ValueListener<Color> l) {
		listenerList.remove(ValueListener.class, l);
	}

	@Override
	public String getToolTipText(final MouseEvent e) {
		return getToolTipText(color, null);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	public Color getValue() { return color; }

	/**
	 * {@inheritDoc}
	 *
	 * @since 4.0
	 */
	@Override
	public void setValue(final Color value) {
		if (!Objects.equals(color, value)) {
			color = value;
			icon.setColor(color);
			repaint();
			setToolTipText(ColorProperty.toString(value));
		}
	}

	// protected
	
	/**
	 * @since 2.4
	 */
	protected void fireValueChanged(final Color oldColor, final Color newColor) {
		TK.fireValueChanged(this, getValueListeners(), oldColor, newColor);
	}

	// package

	static String getToolTipText(final Color color, final ColorPalette palette) {//!!!unify
		String text = MActionInfo.SET_COLOR.getDialogTitle();

		if (color == null)
			return text;

		StringBuilder code = new StringBuilder();
		String html = TK.toUpperCase(ColorProperty.toString(color));

		TK.escapeXML(code, text);
		code
			.append("<br>")
			.append("<code>")
			.append(html)
			.append("</code><br>")
			.append(String.format("RGB: %d %d %d", color.getRed(), color.getGreen(), color.getBlue()));

		String name;
		if (palette != null) {
			name = palette.getDisplayName(color);
			if ("Untitled".equals(name))
				name = ColorPalette.getNamedColors().getDisplayName(color);
		}
		else {
			name = ColorPalette.getNamedColors().getDisplayName(color);
		}

		// do not duplicate HTML color code
		if (!html.equals(name)) {
			code
				.append("<br>")
				.append("<br>");
			TK.escapeXML(code, name);
		}

		return UI.makeHTML(code.toString());
	}
	
}
