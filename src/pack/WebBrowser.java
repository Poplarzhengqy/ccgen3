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

import static org.makagiga.commons.UI._;

import java.awt.Component;
import java.awt.Window;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.Serializable;
import java.net.URI;
import java.text.MessageFormat;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Objects;
import javax.swing.event.EventListenerList;

import org.makagiga.commons.Flags;
import org.makagiga.commons.HistoryManager;
import org.makagiga.commons.MDisposable;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.Net;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.swing.ActionGroup;
import org.makagiga.commons.swing.Focusable;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MToolBar;
import org.makagiga.web.WebPermission;

/*
 * @since 4.0
 */
public abstract class WebBrowser
implements
	Focusable,
	MDisposable,
	Serializable
{

	// public

	public enum OpenLinkMode { NOTHING, INTERNAL_BROWSER, EXTERNAL_BROWSER };

	public static final String BACK_ACTION = "back";
	public static final String COPY_ACTION = "copy";
	public static final String FORWARD_ACTION = "forward";

	// propertes

	/**
	 * The current page font size in CSS units.
	 */
	public static final String FONT_SIZE_PROPERTY = "org.makagiga.web.browser.WebBrowser.FONT_SIZE_PROPERTY";

	public static final String HONOR_DISPLAY_PROPERTIES_PROPERTY = "org.makagiga.web.browser.WebBrowser.HONOR_DISPLAY_PROPERTIES_PROPERTY";
	public static final String IMAGE_FACTORY_PROPERTY = "org.makagiga.web.browser.WebBrowser.IMAGE_FACTORY_PROPERTY";
	public static final String SYNCHRONOUS_IMAGE_LOADING_PROPERTY = "org.makagiga.web.browser.WebBrowser.SYNCHRONOUS_IMAGE_LOADING_PROPERTY";

	// private

	private ActionGroup actionGroup = new ActionGroup();
	private EventListenerList eventListenerList = new EventListenerList();
	private HistoryManager<HistoryItem> historyManager = new HistoryManager<>();
	private MToolBar toolBar;
	private OpenLinkMode openLinkMode = OpenLinkMode.EXTERNAL_BROWSER;
	private URIHandler defaultURIHandler;
	private URIHandler uriHandler;
	
	// public

	public HistoryItem addHistory(final URI uri) {
		HistoryItem hi = new HistoryItem(uri);
		historyManager.add(hi);
		updateHistoryActions();

		return hi;
	}

	public void addLinkListener(final LinkListener l) {
		eventListenerList.add(LinkListener.class, l);
	}

	public void removeLinkListener(final LinkListener l) {
		eventListenerList.remove(LinkListener.class, l);
	}

	public LinkListener[] getLinkListeners() {
		return eventListenerList.getListeners(LinkListener.class);
	}
	
	public abstract MMenu createEditMenu();

	/**
	 * Returns the action group for supported tool bar and menu actions.
	 */
	public ActionGroup getActionGroup() { return actionGroup; }

	public abstract Component getComponent();

	public URI getDocumentLocation() {
		if (historyManager.isEmpty())
			return Net.ABOUT_BLANK;

		return historyManager.getCurrent().getURI();
	}

	public void setDocumentLocation(final URI uri) {
		setDocumentLocation(uri, true);
	}

	public void setDocumentLocation(final URI uri, final boolean updateHistory) {
		try {
			if (
				(uriHandler != null) &&
				uriHandler.handleURI(this, uri, updateHistory)
			)
				return;

			defaultURIHandler.handleURI(this, uri, updateHistory);
		}
		catch (Exception exception) {
			handleError(exception);
		}
	}

	public void setDocumentContent(final String html, final URI baseURI) {
		try {
			setContent(html, baseURI);
		}
		catch (Exception exception) {
			handleError(exception);
		}
	}

	/**
	 * Returns the current page title or {@code null}.
	 */
	public abstract String getDocumentTitle();

	public HistoryManager<HistoryItem> getHistoryManager() { return historyManager; }

	/**
	 * Returns the content as text/html or {@code null}.
	 */
	public abstract String getHTMLText();

	public OpenLinkMode getOpenLinkMode() { return openLinkMode; }

	public void setOpenLinkMode(final OpenLinkMode value) {
		openLinkMode = Objects.requireNonNull(value);
	}

	/**
	 * Returns the content as text/plain or {@code null}.
	 */
	public abstract String getPlainText();

	public abstract Printable getPrintable(final Flags flags, final MessageFormat header, final MessageFormat footer);

	public abstract Object getProperty(final String name);

	public abstract void setProperty(final String name, final Object value);

	public abstract boolean isPropertySupported(final String name);

	/**
	 * Returns the selected text on the current page or {@code null}.
	 */
	public abstract String getSelectedPlainText();

	public MToolBar getToolBar() { return toolBar; }

	public void install(final WebBrowserPanel panel) {
		panel.addNorth(toolBar);
	}

	public void uninstall(final WebBrowserPanel panel) {
		panel.remove(toolBar);
		//!!!remove all added components
	}

	/**
	 * Whether or not the action group returned by {@link #getActionGroup()} is visible in GUI.
	 */
	public boolean isActionGroupVisible() { return true; }

	public abstract boolean printDocument(final Flags flags, final MessageFormat header, final MessageFormat footer) throws PrinterException;

	public void setURIHandler(final URIHandler value) {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new WebPermission("WebBrowser.setURIHandler"));
	
		uriHandler = value;
	}

	public abstract void stop();

	// Focusable

	@Override
	public void focus() {
		getComponent().requestFocusInWindow();
	}

	// MDisposable

	@Override
	public void dispose() {
		actionGroup = TK.dispose(actionGroup);
		defaultURIHandler = null;
		eventListenerList = null;
		if (historyManager != null) {
			historyManager.clear();
			historyManager = null;
		}
		toolBar = null;
		uriHandler = null;
	}
	
	// protected
	
	protected WebBrowser() {
		defaultURIHandler = new URIHandler() {
			@Override
			public boolean handleURI(final WebBrowser webBrowser, final URI uri, final boolean updateHistory) throws Exception {
				if (updateHistory)
					webBrowser.addHistory(uri);
				webBrowser.setContent(uri, null);

				return true;
			}
		};

		historyManager.setLimit(100);

		actionGroup.add(BACK_ACTION, new HistoryManager.BackAction<HistoryItem>(historyManager) {
			@Override
			protected void onAction(final HistoryItem element) {
				WebBrowser.this.setDocumentLocation(element.getURI(), false);
				WebBrowser.this.updateHistoryActions();
			}
		} )
			.setShowTextInToolBar(true);

		actionGroup.add(FORWARD_ACTION, new HistoryManager.ForwardAction<HistoryItem>(historyManager) {
			@Override
			protected void onAction(final HistoryItem element) {
				WebBrowser.this.setDocumentLocation(element.getURI(), false);
				WebBrowser.this.updateHistoryActions();
			}
		} );

		toolBar = new MToolBar();
		toolBar.setTextPosition(MToolBar.TextPosition.ALONGSIDE_ICONS);
		actionGroup.updateToolBar(toolBar);
	}

	protected OpenLinkMode fireLinkEvent(final URI uri) {
		LinkEvent e = new LinkEvent(this, uri);
		OpenLinkMode result = null;
		for (LinkListener i : getLinkListeners()) {
			if (result == null) {
				result = i.linkEvent(e);

				if (result != null)
					break; // for
			}
			else {
				i.linkEvent(e);
			}
		}

		return (result == null) ? getOpenLinkMode() : result;
	}

	protected void handleError(final Throwable t) {
		MLogger.exception(t);

		// show error message
		String error = String.format(
			"<h1 style=\"margin-top: 10; margin-bottom: 5\">%s</h1>" + // title
			"<p style=\"margin: 10\"><code>%s</code></p>" + // url
			"<h2 style=\"margin-top: 10; margin-bottom: 5\">%s</h2>" + // details
			"<p style=\"margin: 10\">%s</p>", // exception
			TK.escapeXML(_("Could not load page")),
			TK.escapeXML(getDocumentLocation().toString()),
			TK.escapeXML(_("Details")),
			TK.escapeXML(t.toString())
		);
		setDocumentContent("<div style=\"margin: 10px\">" +  error + "</div>", null);
	}

	protected abstract void setContent(final Object source, final URI baseURI) throws Exception;

	// private

	private void updateHistoryActions() {
		historyManager.updateActions(
			actionGroup.getAction(BACK_ACTION),
			actionGroup.getAction(FORWARD_ACTION)
		);
	}

	private Window windowForComponent() {
		Component c = getComponent();

		return (c == null) ? null : UI.windowFor(c);
	}

	// public classes

	public static final class HistoryItem implements Serializable {

		// private

		private final URI uri;

		// public

		public HistoryItem(final URI uri) {
			this.uri = Objects.requireNonNull(uri);
		}

		@Override
		public boolean equals(final Object o) {
			if (o == this)
				return true;

			if (!(o instanceof HistoryItem))
				return false;

			HistoryItem hi = (HistoryItem)o;

			return this.uri.equals(hi.uri);
		}

		@Override
		public int hashCode() {
			return uri.hashCode();
		}

		public URI getURI() { return uri; }

	}

	public static final class LinkEvent extends EventObject {

		// private

		private final URI uri;

		// public

		public LinkEvent(final Object source, final URI uri) {
			super(source);
			this.uri = Objects.requireNonNull(uri);
		}

		public URI getURI() { return uri; }

	}

	public static interface LinkListener extends EventListener, Serializable {

		// public

		public WebBrowser.OpenLinkMode linkEvent(final LinkEvent e);

	}

	public static interface URIHandler extends Serializable {

		// public

		public boolean handleURI(final WebBrowser webBrowser, final URI uri, final boolean updateHistory) throws Exception;

	}

}
