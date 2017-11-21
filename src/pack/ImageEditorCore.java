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

package org.makagiga.editors.image;

import static org.makagiga.commons.UI._;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.event.MouseInputListener;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.UndoableEdit;
import javax.swing.undo.UndoableEditSupport;

import com.jhlabs.image.FlipFilter;

import org.pushingpixels.trident.TimelinePropertyBuilder;

import org.makagiga.MainWindow;
import org.makagiga.Tabs;
import org.makagiga.Vars;
import org.makagiga.commons.FS;
import org.makagiga.commons.Flags;
import org.makagiga.commons.ImageTransferHandler;
import org.makagiga.commons.Lockable;
import org.makagiga.commons.MArrayList;
import org.makagiga.commons.MClipboard;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDataTransfer;
import org.makagiga.commons.MGraphics2D;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.fx.MTimeline;
import org.makagiga.commons.swing.MComponent;
import org.makagiga.commons.swing.MHighlighter;
import org.makagiga.commons.swing.MMainWindow;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MScrollPane;
import org.makagiga.commons.swing.MStatusBar;
import org.makagiga.commons.swing.MSwingWorker;
import org.makagiga.commons.swing.MUndoManager;
import org.makagiga.commons.swing.MainView;
import org.makagiga.commons.swing.event.MMouseAdapter;
import org.makagiga.editors.EditorZoom;
import org.makagiga.editors.NavigationUtils;
import org.makagiga.editors.image.tools.BrushTool;
import org.makagiga.editors.image.tools.SelectionTool;
import org.makagiga.editors.image.tools.Tool;
import org.makagiga.editors.image.tools.ToolManager;
import org.makagiga.fs.MetaInfo;
import org.makagiga.plugins.Plugin;

/** An image editor core. */
public final class ImageEditorCore extends MComponent
implements
	IIOReadProgressListener,
	Lockable,
	MouseInputListener,
	MouseWheelListener
{
	
	// public
	
	// "modifyCanvas" flags
	/**
	 * @since 1.2
	 */
	public static final int INSERT = 1;
	
	/**
	 * @since 1.2
	 */
	public static final int USE_SELECTION = 1 << 1;

	// private

	private boolean newFile;
	private BufferedImage canvas;
	private final Color gridColor = MColor.SKY_BLUE.deriveAlpha(80);
	private double currentScale;
	private static double scale = 1.0d;
	private int loadProgress;
	private int modificationLevel;
	private final MArrayList<File> undoFileList = new MArrayList<>();
	private transient MSwingWorker<BufferedImage> loader;
	private MTimeline<ImageEditorCore> scaleAnimation;
	private String messageText;
	private Throwable loadError;
	private final ToolManager toolManager;
	private Undo undo;
	private UndoableEditSupport undoSupport;
	private final WeakReference<ImageEditor> editorRef;
	
	// package
	
	final Map<String, Object> metadata = new HashMap<>();

	// public

	/**
	 * Constructs an image editor core.
	 * @param editor A parent editor
	 */
	public ImageEditorCore(final ImageEditor editor) {
		currentScale = scale;
		setFocusable(true);
		editorRef = new WeakReference<>(editor);

		// init tools
		toolManager = new ToolManager();
		toolManager.setActive(toolManager.getSelectionTool());
		
		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener(this);
		
		setTransferHandler(new ImageTransferHandler(true) {
			@Override
			protected boolean onImport(final Image image) {
				ImageEditorCore.this.modifyCanvas(image);
				
				return true;
			}
		} );
	}

	public void addUndoableEditListener(final UndoableEditListener l) {
		if (undoSupport == null)
			undoSupport = new UndoableEditSupport();
		undoSupport.addUndoableEditListener(l);
	}

	/**
	 * @since 3.0
	 */
	public UndoableEditListener[] getUndoableEditListeners() {
		if (undoSupport != null)
			return undoSupport.getUndoableEditListeners();
		
		return MUndoManager.EMPTY_UNDOABLE_EDIT_LISTENER_ARRAY;
	}

	public void removeUndoableEditListener(final UndoableEditListener l) {
		if (undoSupport != null)
			undoSupport.removeUndoableEditListener(l);
	}

	public void afterModification() {
		afterModification(null);
	}

	public void afterModification(final Rectangle r) {
		if (r == null)
			repaint();
		else
			repaint(r);
		
		if (undo != null) {
			undo.redo = new Undo(canvas);
			fireUndoableEditHappened(undo);
			undo = null;
		}

		setModificationLevel(modificationLevel);
		getEditor().updateActions();
	}

	public void beforeModification() {
		undo = new Undo(canvas);
		setModificationLevel(modificationLevel + 1);
	}

	public boolean canDraw() {
		return (canvas != null) && !isLocked() && !getEditor().isLocked() && !MainWindow.isPresentationActive();
	}

	/**
	 * Clears the canvas.
	 */
	public void clear() {
		modifyCanvas(UI.createCompatibleImage(canvas.getWidth(), canvas.getHeight(), true));
	}

	public boolean copy() {
		return toolManager.getSelectionTool().copy(canvas);
	}

	public void crop() {
		if (toolManager.getSelectionTool().isSelection()) {
			modifyCanvas(getSelectedImage());
			getEditor().updateActions();
		}
	}
	
	public void flip(final int operation) {
		modifyCanvas(new FlipFilter(operation).filter(canvas, null));
	}

	/**
	 * Returns the canvas.
	 */
	public synchronized BufferedImage getCanvas() { return canvas; }
	
	public synchronized void setCanvas(final Image image) {
		if (isLocked())
			return;

		// canvas = image; // slow scrolling
		if (image != null)
			canvas = UI.toBufferedImage(image, false);
		updateScrollPane();
		getEditor().imageInfo.update(this);

		toolManager.getSelectionTool().clear();
	}
	
	public ImageEditor getEditor() {
		return editorRef.get();
	}

	public BufferedImage getSelectedImage() {
		return toolManager.getSelectionTool().getImage(canvas);
	}

	public ToolManager getToolManager() { return toolManager; }

	public void insertImage(final Image image) {
		modifyCanvas(image, INSERT | USE_SELECTION);
	}

	@Override
	public synchronized boolean isLocked() {
		ImageEditor editor = getEditor();
		
		return (editor == null) || editor.isLoading();
	}
	
	/**
	 * @inheritDoc
	 * 
	 * @since 2.0
	 */
	@Override
	public void setLocked(final boolean value) {
		// select "selection tool" in read only mode
		SelectionTool selectionTool = toolManager.getSelectionTool();
		if (value && (toolManager.getActive() != selectionTool))
			toolManager.setActive(selectionTool); // this will invoke "updateCursor"
		else
			updateCursor();
	}

	/**
	 * Loads image from @p input.
	 * @throws Exception Any error
	 *
	 * @since 1.2
	 */
	public void loadFromFile(final InputStream input, final boolean newFile) throws Exception {
		this.newFile = newFile;

		ImageEditor editor = getEditor();
		MetaInfo metaInfo = editor.getMetaInfo();
		editor.imageInfo.update(this);
		editor.updateActions();
		if (editor.isAsyncLoad() && !MainWindow.isPresentationActive()) {
			loader = new MSwingWorker<BufferedImage>(this, metaInfo.toString()) {
				@Override
				public BufferedImage doInBackground() throws Exception {
					try {
						//TEST:if (true)
							//throw new Exception("test");
							//throw new OutOfMemoryError("test");

						if (newFile)
							return null;

						BufferedImage result = null;
						
						// is image already preloaded?
						if (ImagePreloader.isMetaInfo(getEditor().getMetaInfo())) {
							BufferedImage data = TK.get(ImagePreloader.getImageSoftReference());
							if (data != null) {
								//MLogger.debug("image", "Using preloaded image");
								result = data;
							}
						}
						
						if (result == null)
							result = readImage(input);
							
						return result;
					}
					finally {
						FS.close(input);
						getEditor().setLoading(false);
					}
				}
				@Override
				protected void onError(final Throwable throwable, final boolean cancelled) {
					loadError = throwable;
					repaint();

					if (throwable instanceof OutOfMemoryError) {
						MStatusBar.error(throwable);
						ImagePreloader.stop();

						if (!MLogger.isDeveloper())
							MLogger.exception(throwable);
					}
					else {
						super.onError(throwable, cancelled);
					}
				}
				@Override
				protected void onSuccess(final BufferedImage image) {
					try {
						setNewImage(image);
						// start preloading the next image (if any)
						MetaInfo nextImage = NavigationUtils.getNextFile(getEditor().getMetaInfo());
						if (
							(nextImage != null) &&
							Vars.preloaderImage.get() &&
							!nextImage.isEncrypted() &&
							(Tabs.getInstance().findEditor(nextImage) == -1)
						) {
							ImagePreloader.start(nextImage);
						}
					}
					catch (OutOfMemoryError error) {
						MStatusBar.error(error);
						ImagePreloader.stop();
					}
				}
			};
			loader.start();
		}
		else {
			getEditor().setLoading(false); // unlock canvas
			setNewImage(newFile ? null : readImage(input));
		}
	}

	/**
	 * @since 1.2
	 */
	public void modifyCanvas(final Image image) {
		modifyCanvas(image, 0);
	}

	/**
	 * @since 1.2
	 */
	public void modifyCanvas(final Image image, final int flags) {
		try {
			Flags f = Flags.valueOf(flags);
			UI.setWaitCursor(true);
			beforeModification();
			// insert image
			if (f.isSet(INSERT)) {
				Graphics2D g = canvas.createGraphics();
				SelectionTool selectionTool = toolManager.getSelectionTool();
				if (f.isSet(USE_SELECTION) && selectionTool.isSelection()) {
					Rectangle bounds = selectionTool.getBounds();
					g.drawImage(image, bounds.x, bounds.y, null);
				}
				else {
					g.drawImage(image, 0, 0, null);
				}
				g.dispose();
			}
			// replace image
			else {
				setCanvas(image);
			}
			afterModification();
		}
		finally {
			UI.setWaitCursor(false);
		}
	}

	public boolean paste() {
		try {
			Image image = MClipboard.getImage();

			if (image == null)
				return false;

			modifyCanvas(image);
			toolManager.setActive(toolManager.getSelectionTool());

			return true;
		}
		catch (Exception exception) {
			MLogger.exception(exception);

			return false;
		}
	}

	/**
	 * Saves image.
	 * @param format A target image format (e.g. "png")
	 * @param output An output stream
	 * @throws Exception Any error
	 */
	public void saveToFileAs(final String format, final OutputStream output) throws Exception {
		saveToFileAs(format, output, false);
	}
	
	/**
	 * Saves image.
	 * @param format A target image format (e.g. "png")
	 * @param output An output stream
	 * @param sessionSave @c true if this is a session save
	 * @throws Exception Any error
	 *
	 * @since 1.2
	 */
	public void saveToFileAs(final String format, final OutputStream output, final boolean sessionSave) throws Exception {
		if (isLocked() || (canvas == null))
			return;
		
		if (sessionSave)
			modificationLevel = 0;

		newFile = false;

		BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), UI.getTypeForImageFormat(format));
		Graphics2D g = image.createGraphics();
		if (getEditor().isExportMode()) {//!!!
			// draw background
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, image.getWidth(), image.getHeight());
		}
		// draw image
		g.drawImage(canvas, 0, 0, null, null);
		g.dispose();
		ImageIO.write(image, format, output);
		
/* TODO: 2.0: write image metadata
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
		
		if (!writers.hasNext())
			throw new Exception(_("No image writer for \"{0}\" format", format));
		
		ImageWriter writer = writers.next();
		writer.setOutput(ImageIO.createImageOutputStream(output));
		IIOMetadata md;
		if (metaData != null) {
			ImageTypeSpecifier its = ImageTypeSpecifier.createFromBufferedImageType(image.getType());
			md = writer.convertImageMetadata(metaData, its, writer.getDefaultWriteParam());
		}
		else {
			md = null;
		}
		writer.write(new IIOImage(canvas, null, md));
		writer.dispose();
*/
	}
	
	public double getScale() { return scale; }
	
	public void setScale(final double value, final boolean animation) {
		if (scaleAnimation != null) {
			scaleAnimation.setEnabled(false);
			scaleAnimation.cancel();
			scaleAnimation = null;
			repaint();
		}

		scale = Math.max(value, 1.0d);
		
		if (
			!animation ||
			// disable animation for large images
			((canvas != null) && (canvas.getWidth() > 1024))
		) {
			currentScale = scale;
			repaint();
			
			return;
		}
		
		// animated zoom
		scaleAnimation = new MTimeline<>(this, 100);
		scaleAnimation.addPropertyToInterpolate(
			MTimeline.<Double>property("currentScale")
			.from(currentScale)
			.to(scale)
			.getWith(new TimelinePropertyBuilder.PropertyGetter<Double>() {
				@Override
				public Double get(final Object o, final String name) {
					return currentScale;
				}
			} )
			.setWith(new TimelinePropertyBuilder.PropertySetter<Double>() {
				@Override
				public void set(final Object o, final String name, final Double value) {
					currentScale = value;
					repaint();
				}
			} )
		);
		scaleAnimation.play();
	}

	/**
	 * Updates the mouse cursor.
	 */
	public void updateCursor() {
		if (!toolManager.getActive().isLocked() && !canDraw())
			setCursor(Cursor.DEFAULT_CURSOR);
		else
			setCursor(toolManager.getActive().getCursor());
	}
	
	// IIOReadProgressListener

	/**
	 * @since 2.0
	 */
	@Override
	public void imageComplete(final ImageReader source) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void imageProgress(final ImageReader source, final float percentageDone) {
		UI.invokeLater(new Runnable() {
			@Override
			public void run() {
				loadProgress = (int)percentageDone;
				repaint();
			}
		} );
	}

	/**
	 * @since 2.0
	 */
	@Override
	public void imageStarted(final ImageReader source, final int imageIndex) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void readAborted(final ImageReader source) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void sequenceComplete(final ImageReader source) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void sequenceStarted(final ImageReader source, final int minIndex) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void thumbnailComplete(final ImageReader source) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void thumbnailProgress(final ImageReader source, final float percentageDone) { }

	/**
	 * @since 2.0
	 */
	@Override
	public void thumbnailStarted(final ImageReader source, final int imageIndex, final int thumbnailIndex) { }

	// MouseX methods

	@Override
	public void mouseClicked(final MouseEvent e) {
		if (!MMouseAdapter.isLeft(e))
			return;
		
		// double click = toggle presentation mode
		if (
			(toolManager.getActive() == toolManager.getSelectionTool()) &&
			MMouseAdapter.isDoubleClick(e)
		) {
			e.consume();
			Plugin<?> presentation = MainWindow.getPresentationPlugin();
			if (presentation != null) {
				try {
					presentation.call("setActive", !MainWindow.isPresentationActive());
				}
				catch (Exception exception) {
					MMessage.error(UI.windowFor(this), exception);
				}
			}
			
			return;
		}

		getEditor().focus();

		toolManager.getActive().mouseClicked(e);
		e.consume();
	}

	/**
	 * Draws an object on the canvas.
	 * 
	 * @param e the mouse event
	 */
	@Override
	public void mouseDragged(final MouseEvent e) {
		if (!MMouseAdapter.isLeft(e))
			return;

		if (!toolManager.getActive().isLocked() && !canDraw())
			return;

		toolManager.getActive().mouseDragged(e);
		MScrollPane.autoScroll(this, e.getX(), e.getY());
		e.consume();
	}

	@Override
	public void mouseEntered(final MouseEvent e) {
		toolManager.getActive().mouseEntered(e);
		e.consume();
	}

	@Override
	public void mouseExited(final MouseEvent e) {
		toolManager.getActive().mouseExited(e);
		e.consume();
	}

	@Override
	public void mouseMoved(final MouseEvent e) {
		toolManager.getActive().mouseMoved(e);
		e.consume();
	}

	/**
	 * Shows information message box if editor is locked (read only).
	 * 
	 * @param e the mouse event
	 */
	@Override
	public void mousePressed(final MouseEvent e) {
		if (!toolManager.getActive().isLocked()) {
			if (MMouseAdapter.isLeft(e))
				getEditor().showLockInfo();

			if (!canDraw())
				return;
		}

		if (!MMouseAdapter.isLeft(e))
			return;

		toolManager.getActive().mousePressed(e);
		e.consume();
	}

	/**
	 * Draws an object on the canvas.
	 * 
	 * @param e the mouse event
	 */
	@Override
	public void mouseReleased(final MouseEvent e) {
		if (!MMouseAdapter.isLeft(e))
			return;

		if (!toolManager.getActive().isLocked() && !canDraw())
			return;

		toolManager.getActive().mouseReleased(e);
		e.consume();
	}

	@Override
	public void mouseWheelMoved(final MouseWheelEvent e) {
		boolean up = e.getWheelRotation() < 0;
		if (ImageEditor.mouseWheelReversedAction.isSelected())
			up = !up;
		
		// zoom in/out
		if (e.isControlDown()) {
			getEditor().zoom(up ? EditorZoom.ZoomType.IN : EditorZoom.ZoomType.OUT);
			e.consume();
			
			return;
		}

		if (e.isConsumed())
			return;
		
		switch (ImageEditor.mouseWheelFunction) {
			case NAVIGATE:
				NavigationUtils.goToFile(up);
				e.consume();
				break;
			case ZOOM:
				getEditor().zoom(up ? EditorZoom.ZoomType.IN : EditorZoom.ZoomType.OUT);
				e.consume();
				break;
			default: // SCROLL
				toolManager.getActive().mouseWheelMoved(e);
				if (!ImageEditor.autoZoom) {
					MScrollPane.mouseWheelScroll(this, e);
					e.consume();
				}
		}
	}

	// protected
	
	/**
	 * Paints grid, canvas, etc.
	 * 
	 * @param graphics the 2D graphics
	 */
	@Override
	protected void paintComponent(final Graphics graphics) {
		boolean presentation = MainWindow.isPresentationActive();
		Graphics2D g = (Graphics2D)graphics;

		// still loading...
		if ((canvas == null) || isLocked()) {
			// background
			g.setColor(Color.BLACK);
			if (presentation)
				g.fillRect(0, 0, getWidth(), getHeight());

			// status text
			Color color;
			String text;
			if (loadError != null) {
				text = _("Error: {0}", loadError);
				color = MHighlighter.ERROR_COLOR;
			}
			else {
				text = _("Loading... {0}", (loadProgress + "%"));
				color = Color.BLACK;
			}
			paintStatusText(g, text, color, false);

			return;
		}

		final int cw = canvas.getWidth();
		final int ch = canvas.getHeight();

		// auto zoom
		double useScale;
		if (ImageEditor.autoZoom || presentation) {
			useScale = UI.getAutoScale(new Dimension(cw, ch), getParent().getSize());
			currentScale = useScale;
			scale = useScale;
		}
		else {
			useScale = currentScale;
		}

		// draw background (unscaled)
		if (presentation) {
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, getWidth(), getHeight());
		}

		// zoom
		g.scale(useScale, useScale);

		// draw background (scaled)
		if (!presentation) {
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, cw, ch);
		}

		g.setRenderingHint(
			RenderingHints.KEY_RENDERING,
			(useScale < 1.0d)
				? RenderingHints.VALUE_RENDER_QUALITY
				: RenderingHints.VALUE_RENDER_DEFAULT
		);

		// draw canvas
		int dx = 0;
		int dy = 0;
		if (presentation) {
			dx = getWidth() / 2 - (int)(cw * useScale) / 2;
			dy = getHeight() / 2 - (int)(ch * useScale) / 2;
			dx /= useScale;
		}
		g.drawImage(canvas, dx, dy, null);

		if (!getEditor().isExportMode()) {
			// draw tool
			Tool tool = toolManager.getActive();
			if ((tool instanceof SelectionTool) || canDraw()) {
				if (tool.getUseBrushTool())
					BrushTool.getInstance().setup(g);
				tool.draw(g, cw, ch);
			}
			// draw h lines (grid)
			ImageEditor editor = editorRef.get();
			if (
				(editor != null) &&
				editor.getSharedActionGroup().isSelected("show-grid")
			) {
				g.setColor(gridColor);
				g.setStroke(new BasicStroke(1));
				for (int y = 0; y < ch; y++) {
					if (y % 15 == 0)
						g.drawLine(0, y, cw - 1, y);
				}
			}

			if (messageText != null)
				paintStatusText(g, messageText, MHighlighter.WARNING_COLOR, true);
		}
	}

	@Override
	protected boolean processKeyBinding(final KeyStroke ks, final KeyEvent e, final int condition, final boolean pressed) {
		if (
			pressed &&
			(condition == WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) &&
			toolManager.processKeyBinding(ks)
		) {
			return true;
		}

		return super.processKeyBinding(ks, e, condition, pressed);
	}

	// private
	
	private void fireUndoableEditHappened(final UndoableEdit edit) {
		if (undoSupport != null)
			undoSupport.postEdit(edit);
	}

	private void paintStatusText(final Graphics2D g, final String text, final Color color, final boolean center) {
		UI.setTextAntialiasing(g, null);
		g.setColor(color);
		if (center) {
			g.setFont(new Font(Font.DIALOG, Font.BOLD, UI.getDefaultFontSize() * 2));
			MGraphics2D mg = new MGraphics2D(g);
			JViewport v = MScrollPane.getViewport(this);
			Point vp = v.getViewPosition();//!!!
			mg.drawStringCentered(text, vp.x, vp.y, v.getWidth(), v.getHeight());
		}
		else {
			g.setFont(new Font(Font.DIALOG, Font.BOLD, UI.getDefaultFontSize()));
			g.drawString(text, 10, g.getFontMetrics().getAscent() + 10);
		}
	}

	private BufferedImage readImage(final InputStream input) throws IOException {
		String extension = getEditor().getMetaInfo().getFileExtension();
		Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix(extension);

		if (!readers.hasNext())
			return null;

		BufferedImage result = null;
		ImageReader reader = null;
		try (ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
			reader = readers.next();
			reader.addIIOReadProgressListener(this);
			reader.setInput(imageInput);
			result = reader.read(0, reader.getDefaultReadParam());
			MDataTransfer.readImageInfo(reader, metadata);
		}
		finally {
			if (reader != null) {
				reader.removeIIOReadProgressListener(this);
				reader.dispose();
			}
		}

		return result;
	}

	private void setModificationLevel(final int value) {
		MLogger.debug("image", "Modification level: %d", value);
		modificationLevel = value;
		getEditor().setModified(newFile || (modificationLevel != 0));
	}

	private void setNewImage(final BufferedImage image) {
		// create an empty canvas
		if (image == null) {
			setCanvas(UI.createCompatibleImage(800, 600, true));
			toolManager.setActive(BrushTool.getInstance());
		}
		// copy image to the canvas
		else {
			setCanvas(image);
			toolManager.setActive(toolManager.getSelectionTool());
		}
		getEditor().updateActions();
	}
	
	// package private

	void doUpdateDesigner(final boolean updateCursor) {
		if (toolManager.getActive() instanceof SelectionTool)
			toolManager.setActive(BrushTool.getInstance());
		getEditor().getEditorDesigner().update(this, updateCursor);
	}

	void setMessageText(final String value) {
		if (TK.isChange(value, messageText)) {
			messageText = value;
			repaint();
		}
	}

	void shutDown() {
		for (File i : undoFileList)
			i.delete();

		scaleAnimation = TK.dispose(scaleAnimation);
		loader = TK.dispose(loader);
		canvas = null;
		removeMouseListener(this);
		removeMouseMotionListener(this);
		removeMouseWheelListener(this);
		setTransferHandler(null);
	}

	void updateScrollPane() {
		if (canvas == null)
			return;

		boolean presentation = MainWindow.isPresentationActive();
		MScrollPane scroll = (MScrollPane)MScrollPane.getScrollPane(this);

		Dimension size;
		if (!presentation) {
			size = new Dimension(canvas.getWidth(), canvas.getHeight());
			size.width *= scale;
			size.height *= scale;
		}
		else {
			MMainWindow window = MainView.getWindow();
			size = new Dimension(window.getWidth(), window.getHeight());
		}
		setPreferredSize(size);
		setSize(size);

		scroll.setScrollBarVisible(!presentation);
		scroll.getViewport().setViewSize(size);
	}

	// private classes

	private final class Undo extends AbstractUndoableEdit {

		// private

		private File fileBuf;
		private Point scrollPosition;
		private Rectangle selection;
		private Undo redo;

		// public

		public Undo(final BufferedImage image) {
			ObjectOutputStream out = null;
			try {
				fileBuf = File.createTempFile("undo", null);
				fileBuf.deleteOnExit();
				ImageEditorCore.this.undoFileList.add(fileBuf);
				
				// save scroll position
				JViewport v = MScrollPane.getViewport(ImageEditorCore.this);
				scrollPosition = (v == null) ? null : v.getViewPosition();

				// save selection
				SelectionTool selectionTool = ImageEditorCore.this.getToolManager().getSelectionTool();
				selection = 
					selectionTool.isSelection()
					? new Rectangle(selectionTool.getBounds())
					: null;

				out = new ObjectOutputStream(new FS.BufferedFileOutput(fileBuf));
				out.writeObject(new MIcon(image));
			}
			catch (IOException exception) {
				MLogger.exception(exception);
			}
			finally {
				FS.close(out);
			}
		}

		@Override
		public void redo() {
			super.redo();
			if (redo != null) {
				redo.restoreImage();
				ImageEditorCore.this.setModificationLevel(modificationLevel + 1);
			}
		}

		@Override
		public void undo() {
			super.undo();
			ImageEditorCore.this.setModificationLevel(modificationLevel - 1);
			restoreImage();
		}

		// private
		
		private void restoreImage() {
			try (ObjectInputStream in = new ObjectInputStream(new FS.BufferedFileInput(fileBuf))) {
				MIcon icon = (MIcon)in.readObject();
				ImageEditorCore.this.setCanvas(icon.getImage());
				ImageEditorCore.this.afterModification();

				// restore selection
				SelectionTool selectionTool = ImageEditorCore.this.getToolManager().getSelectionTool();
				if (selection != null)
					selectionTool.setBounds(selection);

				// restore scroll position
				if (scrollPosition != null) {
					JViewport v = MScrollPane.getViewport(ImageEditorCore.this);
					if (v != null)
						v.setViewPosition(scrollPosition);
				}
			}
			catch (ClassNotFoundException | IOException exception) {
				MLogger.exception(exception);
			}
		}

	}

}
