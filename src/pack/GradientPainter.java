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

package org.makagiga.commons.painters;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;

import org.makagiga.commons.MColor;
import org.makagiga.commons.UI;

/**
 * @since 4.0
 */
public class GradientPainter extends AbstractPainter {

	// private
	
	private boolean reversed;
	private boolean rounded;
	private final Color[] colors;
	private final float[] fractions;

	// public

	public GradientPainter() {
		this(null);
	}

	public GradientPainter(final Color primaryColor) {
		super(primaryColor, null);
		fractions = null;
		colors = null;
	}

	public GradientPainter(final float[] fractions, final Color[] colors) {
		super(null, null);
		this.fractions = fractions.clone();
		this.colors = colors.clone();
	}
	
	@Override
	public boolean isOpaque() { return !rounded; }
	
	public boolean isReversed() { return reversed; }
	
	public void setReversed(final boolean value) { reversed = value; }

	public boolean isRounded() { return rounded; }
	
	public void setRounded(final boolean value) { rounded = value; }

	@Override
	public void paint(final Component c, final Graphics2D graphics, final int x, final int y, final int width, final int height) {
		Graphics2D g = (Graphics2D)graphics.create();

		Color primary = getPrimaryColor();
		if (primary == null)
			primary = UI.getBackground(c);

		LinearGradientPaint paint;
		if (colors != null) {
			paint = new LinearGradientPaint(
				(float)x, (float)y,
				(float)x, (c.getHeight() + y) - 1,
				fractions, colors
			);
		}
		else {
			Color c1 = primary;
			float[] hsb = MColor.toHSB(c1);
			Color c2 = derive(0.05f, hsb);
			Color c3 = derive(reversed ? 0.1f : 0.07f, hsb);

			paint = new LinearGradientPaint(
				(float)x, (float)y,
				(float)x, (c.getHeight() + y) - 1,
				new float[] { 0.0f, reversed ? 0.65f : 0.8f, 1.0f },
				new Color[] { c1, c2, c3 }
				//new Color[] { Color.RED, Color.GREEN, Color.BLUE }
			);
		}
		g.setPaint(paint);
		
		if (rounded) {
			int arc = GlassPainter.getArcForHeight(height);
			UI.setAntialiasing(g, true);
			g.fillRoundRect(x, y, width, height, arc, arc);
		}
		else {
			g.fillRect(x, y, width, height);
		}
		
		if (getPaintStripes())
			paintStripes(c, g, x, y, width, height, primary);
		
		g.dispose();
	}
	
	// private

	@edu.umd.cs.findbugs.annotation.SuppressWarnings("CLI_CONSTANT_LIST_INDEX")
	private Color derive(final float b, final float[] hsb) {
		if (reversed)
			return Color.getHSBColor(hsb[0], Math.max(hsb[1] - b, 0.0f), hsb[2]);
		
		return Color.getHSBColor(hsb[0], hsb[1], Math.max(hsb[2] - b, 0.0f));
	}

}
