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

package org.makagiga.web.browser;

import java.awt.Component;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Objects;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.Document;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.makagiga.commons.Flags;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.Net;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.html.HTMLViewFactory;
import org.makagiga.commons.html.MHTMLViewer;
import org.makagiga.commons.print.TextPrintInfo;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MScrollPane;
import org.makagiga.commons.swing.MText;

/**
 * @since 4.0
 */
public class SwingWebBrowser extends WebBrowser {

	// private

	private boolean synchronousImageLoading;
	private HTMLViewFactory.ImageDownloader imageDownloader;
	private HTMLViewFactory.ImageFactory imageFactory;
	private MHTMLViewer viewer;
	
	// public
	
	public SwingWebBrowser() {
		initImageDownloader();
		viewer = new MHTMLViewer() {
			@Override
			protected boolean onHyperlinkClick(final HyperlinkEvent e, final URI uri) {
				SwingWebBrowser browser = SwingWebBrowser.this;
				OpenLinkMode mode = browser.fireLinkEvent(uri);
				switch (mode) {
					case NOTHING:
						break;
					case INTERNAL_BROWSER:
						browser.setDocumentLocation(uri);
						break;
					case EXTERNAL_BROWSER:
						MApplication.openURI(uri, browser.viewer);
						break;
					default:
						throw new WTFError(mode);
				}

				return true;
			}
		};
		viewer.setSecureOpen(true);
		viewer.setStyle("padding: 0");

		getActionGroup().add(COPY_ACTION, MText.getAction(viewer, MText.COPY))
			.setShowTextInToolBar(true);
	}

	@Override
	public void dispose() {
		super.dispose();

		if (imageDownloader != null) {
			imageDownloader.setImageFactory(null);
			imageDownloader = null;
		}
		imageFactory = null;
		if (viewer != null) {
			MText.deinstallKit(viewer);
			viewer = TK.dispose(viewer);
		}
	}

	@Override
	public MMenu createEditMenu() {
		return MText.createMenu(viewer);
	}

	@Override
	public Component getComponent() { return viewer; }

	@Override
	public String getDocumentTitle() {
		Object title = viewer.getDocument().getProperty(Document.TitleProperty);

		return Objects.toString(title, null);
	}

	@Override
	public String getHTMLText() {
		return viewer.getText();
	}

	@Override
	public String getPlainText() {
		return MText.getPlainText(viewer);
	}

	@Override
	public Printable getPrintable(final Flags flags, final MessageFormat header, final MessageFormat footer) {
		return viewer.getPrintable(header, footer);
	}

	@Override
	@edu.umd.cs.findbugs.annotation.SuppressWarnings("URV_UNRELATED_RETURN_VALUES")
	public Object getProperty(final String name) {
		if (FONT_SIZE_PROPERTY.equals(name))
			return UI.getFont(viewer).getSize();

		if (HONOR_DISPLAY_PROPERTIES_PROPERTY.equals(name))
			return Boolean.TRUE.equals(viewer.getClientProperty(MHTMLViewer.HONOR_DISPLAY_PROPERTIES));

		if (IMAGE_FACTORY_PROPERTY.equals(name))
			return imageFactory;

		if (SYNCHRONOUS_IMAGE_LOADING_PROPERTY.equals(name))
			return synchronousImageLoading;

		return false;
	}

	@Override
	public void setProperty(final String name, final Object value) {
		switch (name) {
			case FONT_SIZE_PROPERTY:
				viewer.setStyle("font-size: " + value);
				break;
			case HONOR_DISPLAY_PROPERTIES_PROPERTY:
				viewer.putClientProperty(MHTMLViewer.HONOR_DISPLAY_PROPERTIES, (Boolean)value);
				break;
			case IMAGE_FACTORY_PROPERTY:
				imageFactory = (HTMLViewFactory.ImageFactory)value;
				break;
			case SYNCHRONOUS_IMAGE_LOADING_PROPERTY:
				synchronousImageLoading = (Boolean)value;
				break;
		}
	}

	@Override
	public boolean isPropertySupported(final String name) {
		return
			FONT_SIZE_PROPERTY.equals(name) ||
			HONOR_DISPLAY_PROPERTIES_PROPERTY.equals(name) ||
			IMAGE_FACTORY_PROPERTY.equals(name) ||
			SYNCHRONOUS_IMAGE_LOADING_PROPERTY.equals(name);
	}

	@Override
	public String getSelectedPlainText() {
		return viewer.getSelectedText();
	}

	@Override
	public void install(final WebBrowserPanel panel) {
		super.install(panel);
		panel.addCenter(viewer, MScrollPane.NO_BORDER_AUTO);
	}

	@Override
	public boolean printDocument(final Flags flags, final MessageFormat header, final MessageFormat footer) throws PrinterException {
		TextPrintInfo.PrintResult result = TextPrintInfo.printDocument(
			viewer,
			flags,
			header,
			footer,
			getDocumentTitle()
		);

		return (result == TextPrintInfo.PrintResult.COMPLETE);
	}

	@Override
	public void stop() {
		imageDownloader.cancelAll();
		initImageDownloader();
		if (viewer != null) {
			viewer.getDocument().putProperty(Document.StreamDescriptionProperty, null);
			viewer.setEditorKit(new BrowserHTMLEditorKit(this));
		}
	}

	// protected

	@Override
	protected void setContent(final Object source, final URI baseURI) throws Exception {
		if (source instanceof String) {
			String html = (String)source;
			try {
				loadingStarted(baseURI);
				viewer.setHTML(html);
			}
			finally {
				loadingFinished();
			}
		}
		else if (source instanceof URI) {
			URI uri = (URI)source;
			try {
				loadingStarted(baseURI);
				viewer.setPage(uri.toURL());
			}
			finally {
				loadingFinished();
			}
		}
		else {
			throw new IllegalArgumentException("Unsupported source: " + source);
		}
	}

	// private

	private void initImageDownloader() {
		imageDownloader = new HTMLViewFactory.ImageDownloader(Net.DOWNLOAD_USE_CACHE | Net.DOWNLOAD_NO_CACHE_UPDATE);
		imageDownloader.setImageFactory(imageFactory);
	}

	private void loadingFinished() {
		if (viewer != null)
			UI.setWaitCursor(viewer, false);
	}

	private void loadingStarted(final URI baseURI) {
		stop();

		// after stop()
		Document doc = viewer.getDocument();
		if (doc instanceof HTMLDocument) {
			HTMLDocument htmlDoc = (HTMLDocument)doc;
			try {
				URL url = (baseURI == null) ? null : baseURI.toURL();
				htmlDoc.setBase(url);
			}
			catch (MalformedURLException exception) {
				MLogger.exception(exception);
				htmlDoc.setBase(null);
			}
		}

		UI.setWaitCursor(viewer, true);
	}

	// private classes

	private static final class BrowserHTMLEditorKit extends HTMLEditorKit {

		// private

		private final WeakReference<SwingWebBrowser> browserRef;

		// public

		@Override
		public ViewFactory getViewFactory() {
			SwingWebBrowser browser = browserRef.get();

			if (browser == null)
				return super.getViewFactory();

			return new BrowserViewFactory(browser, super.getViewFactory(), browser.imageDownloader);
		}

		// private

		private BrowserHTMLEditorKit(final SwingWebBrowser browser) {
			browserRef = new WeakReference<>(browser);
		}

	}

	private static final class BrowserViewFactory extends HTMLViewFactory {

		// private

		private final WeakReference<SwingWebBrowser> browserRef;

		// public

		@Override
		public boolean getLoadsSynchronously() {
			SwingWebBrowser browser = browserRef.get();

			return (browser == null) ? super.getLoadsSynchronously() : browser.synchronousImageLoading;
		}

		// private

		private BrowserViewFactory(final SwingWebBrowser browser, final ViewFactory impl, final HTMLViewFactory.ImageDownloader id) {
			super(impl, id);
			browserRef = new WeakReference<>(browser);
		}

	}

}
