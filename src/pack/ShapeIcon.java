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

package org.makagiga.commons.icons;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;

import org.makagiga.commons.MColor;
import org.makagiga.commons.MGraphics2D;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.UI;

/**
 * @since 4.0
 */
public enum ShapeIcon implements Icon {

	// public
	
	PLUS,
	MINUS,
	UP_ARROW,
	DOWN_ARROW,
	LEFT_ARROW,
	RIGHT_ARROW;
	
	// private
	
	private final Impl small;
	
	// public
	
	/**
	 * @since 4.2
	 */
	public Icon scaleUI() {
		return new Impl(this, UI.iconSize.get());
	}
	
	/**
	 * @since 4.2
	 */
	public void set(final Action action) {
		action.putValue(Action.LARGE_ICON_KEY, scaleUI());
		action.putValue(Action.SMALL_ICON, this);
	}
	
	// Icon
	
	@Override
	public int getIconHeight() {
		return small.getIconHeight();
	}

	@Override
	public int getIconWidth() {
		return small.getIconWidth();
	}
	
	@Override
	public void paintIcon(final Component c, final Graphics graphics, int x, int y) {
		small.paintIcon(c, graphics, x, y);
	}
	
	// private
	
	private ShapeIcon() {
		small = new Impl(this, MIcon.getSmallSize());
	}
	
	// private classes
	
	private static final class Impl implements Icon {
	
		// private
		
		private final int size;
		private final ShapeIcon shape;
	
		// public
		
		public Impl(final ShapeIcon shape, final int size) {
			this.shape = shape;
			this.size = size;
		}
		
		// Icon

		@Override
		public int getIconHeight() { return size; }

		@Override
		public int getIconWidth() { return size; }
	
		@Override
		public void paintIcon(final Component c, final Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D)graphics;
			g.setColor(getIconForeground(c));
		
			int hAlign;
			int paddingX;
			int paddingY;
			if ((shape == ShapeIcon.PLUS) || (shape == ShapeIcon.MINUS)) {
				hAlign = 1;
				paddingX = 3;
				paddingY = 3;
			}
			else {
				hAlign = 0;
				paddingX = 3;
				paddingY = 5;
			}

			x += paddingX;
			y += paddingY;
			int barSize = 3;
			int w = getIconWidth() - paddingX * 2 + hAlign;
			int h = getIconHeight() - paddingY * 2 + hAlign;
			int midX = x + w / 2;
			int midY = y + h / 2;
			switch (shape) {
				case PLUS:
					g.fillRect(x, midY - 1, w, barSize);
					// use two vertical rectangles for better look with alpha colors
					int hy = (midY - 1) - y;
					g.fillRect(midX - 1, y, barSize, hy);
					g.fillRect(midX - 1, midY + barSize - 1, barSize, h - hy - barSize);
					break;
				case MINUS:
					g.fillRect(x, midY - 1, w, barSize);
					break;
				case UP_ARROW:
					drawTriangle(
						g,
						midX, y,
						x, y + h,
						x + w, y + h
					);
					break;
				case DOWN_ARROW:
					drawTriangle(
						g,
						midX, y + h,
						x, y,
						x + w, y
					);
					break;
				case LEFT_ARROW:
					x += 2;
					y -= 1;
					w -= 4;
					h += 2;
					drawTriangle(
						g,
						x, midY,
						x + w, y,
						x + w, y + h
					);
					break;
				case RIGHT_ARROW:
					x += 2;
					y -= 1;
					w -= 4;
					h += 2;
					drawTriangle(
						g,
						x + w, midY,
						x, y,
						x, y + h
					);
					break;
			}
		}

		// private

		private void drawTriangle(
			final Graphics2D graphics,
			final int x1, final int y1,
			final int x2, final int y2,
			final int x3, final int y3
		) {
			Shape shape = MGraphics2D.createTriangle(
				x1, y1,
				x2, y2,
				x3, y3
			);
			try (MGraphics2D g = MGraphics2D.copy(graphics)) {
				g.setAntialiasing(true);
				g.fill(shape);
			}
		}

		private Color getIconForeground(final Component c) {
			Color fg = MColor.GRAY;
			if (c != null) {
				// use light foreground color for themes with dark background
				fg = MColor.getContrast(UI.getBackground(c));
				
				// disabled
				if (!c.isEnabled()) {
					fg = MColor.deriveAlpha(fg, 127);
				}
				// mouse hover
				else if ((c instanceof AbstractButton) && (c.getMousePosition() != null)) {
					fg = MColor.getBrighter(fg);
				}
			}
		
			return fg;
		}

	}

}
