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

package org.makagiga.tabs;

import static org.makagiga.commons.UI._;

import java.awt.Component;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URI;
import java.text.MessageFormat;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;

import org.makagiga.Tabs;
import org.makagiga.commons.Config;
import org.makagiga.commons.Flags;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.TK;
import org.makagiga.commons.swing.ActionGroup;
import org.makagiga.commons.swing.Focusable;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MToolBar;
import org.makagiga.editors.Editor;
import org.makagiga.editors.EditorPrint;
import org.makagiga.editors.EditorSearch;
import org.makagiga.editors.EditorZoom;
import org.makagiga.editors.TextUtils;
import org.makagiga.plugins.PluginMenu;
import org.makagiga.web.browser.SwingWebBrowser;
import org.makagiga.web.browser.WebBrowser;
import org.makagiga.web.browser.WebBrowserPanel;

/**
 * @since 4.0
 */
public class WebBrowserTab extends Editor<WebBrowserPanel>
implements
	EditorPrint,
	EditorSearch,
	EditorZoom,
	Focusable,
	PluginMenu
{

	// private

	private PropertyChangeListener pcl;
	private static final String SHARED = "org.makagiga.tabs.WebBrowserTab.SHARED";
	
	// public

	public WebBrowserTab() {
		this(new SwingWebBrowser());
	}

	public WebBrowserTab(final WebBrowser webBrowser) {
		this(new WebBrowserPanel(webBrowser));
	}

	public WebBrowserTab(final WebBrowserPanel webBrowserPanel) {
		setMetaInfo(_("Browser"), MIcon.small("ui/internet"));
		setCore(webBrowserPanel);

		WebBrowser browser = getWebBrowser();
		browser.getToolBar().setVisible(false);
		initializeFont();
		addCenter(core);
		
		pcl = new PropertyChangeListener() {
			@Override
			public void propertyChange(final PropertyChangeEvent e) {
				if (TK.isProperty(e, WebBrowser.PROGRESS_PROPERTY, 100))
					setTabTitle(getWebBrowser().getDocumentDisplayTitle(_("Browser")));
			}
		};
		browser.addPropertyChangeListener(pcl);
	}

	public static WebBrowserTab getInstance() {
		Tabs tabs = Tabs.getInstance();
		WebBrowserTab tab = tabs.findByID(SHARED);
		if (tab == null) {
			tab = new WebBrowserTab();
			tab.getWebBrowser().addLinkListener(new WebBrowser.LinkListener() {
				@Override
				public WebBrowser.OpenLinkMode linkEvent(final WebBrowser.LinkEvent e) {
					if ("file".equals(e.getURI().getScheme()))
						return WebBrowser.OpenLinkMode.INTERNAL_BROWSER;

					return WebBrowser.OpenLinkMode.EXTERNAL_BROWSER;
				}
			} );
			tab.putClientProperty(ID_PROPERTY, SHARED);
// TODO: 2.2: add to recently closed
			tabs.addEditor(tab);
		}
		else {
			tabs.selectEditor(tab);
		}

		return tab;
	}

	@Override
	public MToolBar.TextPosition getPreferredToolBarTextPosition() { return MToolBar.TextPosition.ALONGSIDE_ICONS; }

	@Override
	public MessageFormat getPrintFooter(final boolean enabled) {
		if (enabled) {
			URI location = getWebBrowser().getDocumentLocation();

			return new MessageFormat(location.toString());
		}
		else {
			return null;
		}
	}

	@Override
	public String getPrintTitle() {
		return getWebBrowser().getDocumentDisplayTitle(null);
	}

	public WebBrowser getWebBrowser() {
		return core.getWebBrowser();
	}

	@Override
	public void onClose() {
		WebBrowser webBrowser = getWebBrowser();
		if (pcl != null) {
			webBrowser.removePropertyChangeListener(pcl);
			pcl = null;
		}
		Component component = webBrowser.getComponent();
		if (component instanceof JTextComponent)
			TextUtils.uninstallSearchHighlighter((JTextComponent)component);
		core.dispose();
	}

	@Override
	public void setPrinting(final boolean value) {
		super.setPrinting(value);
		getWebBrowser().setProperty(WebBrowser.SYNCHRONOUS_IMAGE_LOADING_PROPERTY, value);
	}

	// EditorPrint

	@Override
	public Printable getPrintable(final Flags flags, final MessageFormat header, final MessageFormat footer) throws PrinterException {
		return getWebBrowser().getPrintable(flags, header, footer);
	}

	@Override
	public PrintResult printDocument(final Flags flags, final MessageFormat header, final MessageFormat footer) throws PrinterException {
		return
			getWebBrowser().printDocument(flags, header, footer)
			? PrintResult.COMPLETE
			: PrintResult.CANCELLED;
	}

	// EditorSearch

	@Override
	public SearchResult findText(final String text, final Flags flags) {
		Component component = getWebBrowser().getComponent();

		return
			(component instanceof JTextComponent)
			? TextUtils.findText((JTextComponent)component, text, flags)
			: SearchResult.NOT_FOUND;
	}

	@Override
	public SearchResult findNextText(final String text, final Flags flags) {
		Component component = getWebBrowser().getComponent();

		return
			(component instanceof JTextComponent)
			? TextUtils.findNextText((JTextComponent)component, text, flags)
			: SearchResult.NOT_FOUND;
	}

	/**
	 * @since 4.6
	 */
	@Override
	public SearchResult findPreviousText(final String text, final Flags flags) {
		Component component = getWebBrowser().getComponent();

		return
			(component instanceof JTextComponent)
			? TextUtils.findPreviousText((JTextComponent)component, text, flags)
			: SearchResult.NOT_FOUND;
	}

	@Override
	public String getDefaultSearchText() {
		return getWebBrowser().getSelectedPlainText();
	}

	@Override
	public int getSupportedSearchOptions() {
		Component component = getWebBrowser().getComponent();

		return
			(component instanceof JTextComponent)
			? CASE_SENSITIVE | FIND_PREVIOUS
			: NO_SEARCH;
	}

	// EditorZoom

	@Override
	public JComponent getZoomComponent() { return null; }

	@Override
	public boolean isZoomEnabled(final ZoomType type) {
		WebBrowser browser = getWebBrowser();

		if (!browser.isPropertySupported(WebBrowser.FONT_SIZE_PROPERTY))
			return false;

		Component component = browser.getComponent();

		return
			(component instanceof JTextComponent)
			? TextUtils.canZoom((JTextComponent)component, type)
			: false;
	}
	
	/**
	 * @since 4.0
	 */
	@Override
	public void resetZoom() {
		doZoom(null, true);
	}

	@Override
	public void zoom(final ZoomType type) {
		doZoom(type, false);
	}

	// Focusable

	@Override
	public void focus() {
		getWebBrowser().focus();
	}

	// PluginMenu

	@Override
	public void updateMenu(final String type, final MMenu menu) {
		if (type.equals(EDIT_MENU)) {
			MMenu editMenu = getWebBrowser().createEditMenu();
			if (editMenu != null)
				menu.addItemsFromMenu(editMenu);
		}
	}

	@Override
	public void updateToolBar(final String type, final MToolBar toolBar) {
		if (type.equals(EDITOR_TOOL_BAR)) {
			WebBrowser browser = getWebBrowser();
			if (browser.isActionGroupVisible()) {
				ActionGroup actionGroup = browser.getActionGroup();
				actionGroup.updateToolBar(toolBar);
			}
		}
	}

	// protected

	protected void initializeFont() {
		WebBrowser browser = getWebBrowser();
		if (browser.isPropertySupported(WebBrowser.FONT_SIZE_PROPERTY)) {
			Config config = Config.getDefault();
			int fontSize = config.readInt(Config.getPlatformKey(getConfigKey("fontSize")), -1);
			if (fontSize != -1)
				browser.setProperty(WebBrowser.FONT_SIZE_PROPERTY, TK.limit(fontSize, TextUtils.MIN_ZOOM, TextUtils.MAX_ZOOM));
		}
	}

	// private
	
	private void doZoom(final ZoomType type, final boolean reset) {
		WebBrowser browser = getWebBrowser();
		Component component = browser.getComponent();
		if (component instanceof JTextComponent) {
			if (reset)
				TextUtils.resetZoom((JTextComponent)component, -1);
			else
				TextUtils.zoom((JTextComponent)component, type);
		}

		if (browser.isPropertySupported(WebBrowser.FONT_SIZE_PROPERTY)) {
			Object fontSize = browser.getProperty(WebBrowser.FONT_SIZE_PROPERTY);
			if (fontSize instanceof Integer) {
				Config config = Config.getDefault();
				config.write(Config.getPlatformKey(getConfigKey("fontSize")), (Integer)fontSize);
			}
		}
	}

	private String getConfigKey(final String key) {
		return getClass().getName() + "." + key;
	}

}
