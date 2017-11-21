// Copyright 2007 Konrad Twardowski
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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.util.Objects;
import javax.swing.Icon;

import org.makagiga.commons.MColor;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;

/**
 * @since 2.0, 4.0 (org.makagiga.commons.color package)
 */
public class MColorIcon
implements
	Icon,
	Serializable
{

	/**
	 * An icon type/shape.
	 */
	public enum Type {
		/**
		 * Oval/circle shape.
		 */
		OVAL,

		/**
		 * Rectangle shape.
		 */
		RECTANGLE,

		/**
		 * @since 3.8.3
		 */
		RECTANGLE_SIMPLE
	};
	
	public static final int COMPONENT_SIZE = -1;
	public static final int UI_SIZE = -2;

	// private
	
	private Color background;
	private Color color;
	private Icon icon;
	private int height;
	private int padding;
	private int width;
	private Type type;
	
	// public
	
	public MColorIcon() {
		this(Color.BLACK, MIcon.getDefaultSize());
	}

	public MColorIcon(final Color color) {
		this(color, UI_SIZE, UI_SIZE, Type.RECTANGLE);
	}

	/**
	 * @since 3.8
	 */
	public MColorIcon(final Color color, final Icon icon) {
		this.color = color;
		this.icon = icon;
		this.type = Type.RECTANGLE;
	}

	public MColorIcon(final Color color, final int size) {
		this(color, size, size, Type.RECTANGLE);
	}

	/**
	 * @since 4.0
	 */
	public MColorIcon(final Color color, final int size, final Icon icon) {
		this.color = color;
		this.width = size;
		this.height = size;
		this.icon = icon;
		this.type = Type.RECTANGLE;
	}

	/**
	 * @throws NullPointerException If @p size is @c null
	 */
	public MColorIcon(final Color color, final Dimension size) {
		this(color, size.width, size.height, Type.RECTANGLE);
	}

	/**
	 * @throws NullPointerException If @p type is @c null
	 */
	public MColorIcon(final Color color, final int width, final int height, final Type type) {
		this.color = color;
		this.width = width;
		this.height = height;
		this.type = Objects.requireNonNull(type);
	}
	
	/**
	 * @mg.default {@code null} (no background color)
	 *
	 * @since 4.2
	 */
	public Color getBackground() { return background; }
	
	/**
	 * @since 4.2
	 */
	public void setBackground(final Color value) { background = value; }
	
	public Color getColor() { return color; }
	
	public void setColor(final Color value) { color = value; }

	/**
	 * @since 3.8
	 */
	public Icon getIcon() { return icon; }

	/**
	 * @since 3.8
	 */
	public void setIcon(final Icon value) { icon = value; }

	/**
	 * @since 3.8
	 */
	public int getPadding() { return padding; }

	/**
	 * @since 3.8
	 */
	public void setPadding(final int value) { padding = value; }

	public Type getType() { return type; }

	/**
	 * @throws NullPointerException If @p value is @c null
	 */
	public void setType(final Type value) {
		type = Objects.requireNonNull(value);
	}
	
	// Icon
	
	@Override
	public int getIconHeight() {
		if (icon != null)
			return makePadding(icon.getIconHeight());

		switch (height) {
			case COMPONENT_SIZE:
			case UI_SIZE:
				return makePadding(MIcon.getSmallSize());
			default:
				return makePadding(height);
		}
	}

	@Override
	public int getIconWidth() {
		if (icon != null)
			return makePadding(icon.getIconWidth());

		switch (width) {
			case COMPONENT_SIZE:
			case UI_SIZE:
				return makePadding(MIcon.getSmallSize());
			default:
				return makePadding(width);
		}
	}
	
	@Override
	public void paintIcon(final Component c, final Graphics graphics, int x, int y) {
		Graphics2D g = (Graphics2D)graphics;

		int w;
		int h;
		if (c == null) {
			w = getIconWidth();
			h = getIconHeight();
		}
		else {
			if (width == COMPONENT_SIZE) {
				x = 0;
				w = c.getWidth();
			}
			else {
				w = getIconWidth();
			}
			if (height == COMPONENT_SIZE) {
				y = 0;
				h = c.getHeight();
			}
			else {
				h = getIconHeight();
			}
		}

		paintBackground(c, g, x, y, w, h);

		if (icon != null)
			icon.paintIcon(c, g, x + padding, y + padding);
	}
	
	/**
	 * @since 4.2
	 */
	public static MColorIcon smallRectangle(final Color color) {
		int size = MIcon.getSmallSize();

		return new MColorIcon(color, size, size, Type.RECTANGLE);
	}

	// protected

	/**
	 * @since 3.8
	 */
	protected void paintBackground(final Component c, final Graphics2D g, final int x, final int y, final int w, final int h) {
		if (background != null) {
			g.setColor(background);
			g.fillRect(x, y, w, h);
		}
	
		boolean enabled;
		boolean focused;
		if (c == null) {
			enabled = true;
			focused = false;
		}
		else {
			enabled = c.isEnabled();
			focused = c.isFocusOwner();
		}

		Color baseColor =
			(color == null)
			?
				(c == null) ? Color.BLACK : c.getBackground()
			:
				color;

		if (baseColor == null)
			baseColor = Color.BLACK;
		Color shapeColor;
		if (enabled) {
			shapeColor = baseColor;
		}
		else {
			shapeColor = baseColor.darker();
		}

		Shape shape = null;
		switch (type) {
			case OVAL:
				shape = new Ellipse2D.Float(x + 2f, y + 2f, (float)(w - 5), (float)(h - 5));
				break;
			case RECTANGLE:
				shape = new Rectangle2D.Float(x + 2f, y + 2f, (float)(w - 5), (float)(h - 5));
				break;
			case RECTANGLE_SIMPLE:
				shape = new Rectangle2D.Float(x, y, w, h);
				break;
			default:
				throw new WTFError(type);
		}
		Object oldAA = UI.setAntialiasing(g, true);
		g.setColor(shapeColor);
		g.fill(shape);
		
		// draw border
		
		boolean mouseOver = enabled && (c != null) && (c.getMousePosition() != null);
		
		if ((type != Type.OVAL) && !UI.isRetro()) {
			Color borderColor = null;
			if (mouseOver)
				borderColor = MColor.getBrighter(shapeColor);
			if (borderColor == null)
				borderColor = shapeColor.darker();
			g.setColor(borderColor);
			g.draw(shape);
		}

		if (type == Type.OVAL) {
			Composite oldComposite = g.getComposite();
			Shape oldClip = g.getClip();
			Paint oldPaint = g.getPaint();

			LinearGradientPaint paint = new LinearGradientPaint(
				2f, 2f,
				(float)(w - 3), (float)(h - 3),
				new float[] { 0.0f, (mouseOver ? 0.9f : 0.6f) },
				new Color[] { Color.WHITE, UI.INVISIBLE }
			);
			g.setClip(shape);
			g.setComposite(AlphaComposite.SrcOver.derive(0.6f));
			g.setPaint(paint);
			g.fill(shape);

			g.setClip(oldClip);
			g.setComposite(oldComposite);
			g.setPaint(oldPaint);
		}

		if (focused) {
			float fp;
			switch (type) {
				case OVAL:
					fp = 2f;
					shape = new Ellipse2D.Float(x + 2f + fp, y + 2f + fp, w - 5 - (fp * 2), h - 5 - (fp * 2));
					break;
				case RECTANGLE:
					fp = 2f;
					shape = new Rectangle2D.Float(x + 2f + fp, y + 2f + fp, w - 5 - (fp * 2), h - 5 - (fp * 2));
					break;
				case RECTANGLE_SIMPLE:
					shape = new Rectangle2D.Float(x + 2f, y + 2f, w - 4, h - 4);
					break;
				default:
					throw new WTFError(type);
			}
			g.setColor(MColor.getContrastBW(shapeColor));
			g.draw(shape);
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
	}

	// private

	private int makePadding(final int i) {
		if (padding == 0)
			return i;

		return i + (padding * 2);
	}

}
