// Copyright 2010 Konrad Twardowski
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

package org.makagiga.web.wiki;

import static org.makagiga.commons.UI._;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.makagiga.commons.FS;
import org.makagiga.commons.Globals;
import org.makagiga.commons.Item;
import org.makagiga.commons.MArrayList;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.Net;
import org.makagiga.commons.TK;
import org.makagiga.commons.Tuple;
import org.makagiga.commons.UI;
import org.makagiga.commons.html.HTMLAutodiscovery;
import org.makagiga.commons.html.HTMLBuilder;
import org.makagiga.commons.mods.Mods;
import org.makagiga.commons.swing.MComboBox;
import org.makagiga.commons.swing.MDialog;
import org.makagiga.commons.swing.MLinkButton;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MSwingWorker;
import org.makagiga.commons.swing.MTimer;
import org.makagiga.commons.swing.MToolBar;
import org.makagiga.web.browser.SwingWebBrowser;
import org.makagiga.web.browser.WebBrowser;
import org.makagiga.web.browser.WebBrowserPanel;

/**
 * @since 3.8.8
 */
public class WikiPanel extends WebBrowserPanel {

	// private

	private Language selectLanguage;
	private MComboBox<Language> languageComboBox;
	private MLinkButton addressButton;
	private static final MLogger log = MLogger.get("wiki");
	private transient MSwingWorker<Tuple.Two<String, MArrayList<Language>>> downloader;
	private URI pageURI;
	private static URICache _cache;
	private WebBrowser.LinkListener linkListener;
	
	// public
	
	public WikiPanel() {
		super(new SwingWebBrowser());
		WebBrowser browser = getWebBrowser();

		languageComboBox = new MComboBox<Language>() {
			@Override
			public Dimension getMaximumSize() {
				return super.getPreferredSize();
			}
			@Override
			protected void onSelect() {
				Language l = this.getSelectedItem();
				if (l != WikiPanel.this.selectLanguage) {
					URI uri = l.toURI();
					WikiPanel.this.setPageURI(uri, true);
				}
			}
		};
		languageComboBox.setRenderer(Item.createListCellRenderer());
		languageComboBox.setToolTipText(_("Select Language"));
		languageComboBox.setVisible(false);
		
		selectLanguage = new Language(null, "(" + _("Select Language") + ")");

		updateComponents();

		addressButton = new MLinkButton();

		MToolBar toolBar = browser.getToolBar();
		addToolBarComponents(toolBar);

		linkListener = new WebBrowser.LinkListener() {
			@Override
			public WebBrowser.OpenLinkMode linkEvent(final WebBrowser.LinkEvent e) {
				URI uri = e.getURI();
				String host = uri.getHost();
				String path = uri.getPath();

				//!!!scroll to ref
				
				String ref = uri.getFragment();
				
				if (ref != null) {
					WikiPanel.this.getWebBrowser().addHistory(uri);

					return WebBrowser.OpenLinkMode.NOTHING;
				}

				URI currentPage = WikiPanel.this.pageURI;
				if (
					(path != null) && path.startsWith("/wiki/") &&
					(host != null) && host.equals(currentPage.getHost())
				) {
					String s = currentPage.toString();
					s = s.substring(0, s.indexOf("/wiki/")) + path;

					WikiPanel.this.loadPage(URI.create(s), true);

					return WebBrowser.OpenLinkMode.NOTHING;
				}

				return WebBrowser.OpenLinkMode.EXTERNAL_BROWSER;
			}
		};
		browser.addLinkListener(linkListener);
		browser.setURIHandler(new WebBrowser.URIHandler() {
			@Override
			public boolean handleURI(final WebBrowser webBrowser, final URI uri, final boolean updateHistory) throws Exception {
				loadPage(uri, updateHistory);

				return true;
			}
		} );
	}

	public WikiPanel(final URI pageURI) {
		this();
		setPage(pageURI);
	}
	
	/**
	 * @since 4.4
	 */
	public void addToolBarComponents(final MToolBar toolBar) {
		toolBar.addGap();
		toolBar.add(languageComboBox);
		toolBar.addStretch();
		toolBar.addGap();
		toolBar.add(addressButton);
		toolBar.addGap();
	}
	
	/**
	 * @since 4.4
	 */
	public MLinkButton getAddressButton() { return addressButton; }
	
	/**
	 * @since 4.4
	 */
	public MComboBox<Language> getLanguageComboBox() { return languageComboBox; }

	/**
	 * @since 4.0
	 */
	public URI getPage() {
		return getWebBrowser().getDocumentLocation();
	}
	
	/**
	 * @since 4.0
	 */
	public void setPage(final URI uri) {
		setPageURI(uri, true);
	}

	public static void openURI(final Window owner, final String uri, final String title) {
		URI pageURI = URI.create(uri);
		
		if (Boolean.TRUE.equals(Mods.exec(owner, Globals.MOD_WIKI_SHOW_PANEL, pageURI)))
			return;

		WikiPanel wikiPanel = new WikiPanel();
		try {
			wikiPanel.setPage(pageURI);
			Dialog dialog = new Dialog(owner, title, wikiPanel);
			dialog.exec();
		}
		finally {
			wikiPanel.dispose();
		}
	}

	// MDisposable

	@Override
	public void dispose() {
		if (linkListener != null) {
			getWebBrowser().removeLinkListener(linkListener);
			linkListener = null;
		}

		super.dispose();
		
		languageComboBox = null;
		addressButton = null;
		downloader = TK.dispose(downloader);
		pageURI = null;
	}

	// private

	private Tuple.Two<String, MArrayList<Language>> downloadPage() throws IOException {
		URL url = pageURI.toURL();
		Net.DownloadInfo downloadInfo = new Net.DownloadInfo(url, ".html", Net.DOWNLOAD_USE_CACHE | Net.SETUP_COMPRESS);
		downloadInfo.startDownload();
		
		String code = FS.read(downloadInfo.getFile(), "UTF8");

		MArrayList<Language> languageList = new MArrayList<>();
		try {
			String langSection = TK.substring(code, "<!-- LANGUAGES -->", "<!-- /LANGUAGES -->", false);
			for (String i : TK.fastSplit(langSection, '<')) {
				if (i.startsWith("a href")) {
					String href = HTMLAutodiscovery.getAttribute(i, "href");
					
					if (TK.isEmpty(href))
						throw new ParseException("No language href found", 0);
					
					int nameIndex = i.lastIndexOf('>');
					String name;
					if (nameIndex != -1) {
						name = i.substring(nameIndex + 1);
					}
					else {
						name = href;
					}

					if (TK.isEmpty(name))
						throw new ParseException("No language name found", 0);
					
					languageList.add(new Language(href, name));
				}
			}

			languageList.sort(new Comparator<Language>() {
				@Override
				public int compare(final Language l1, final Language l2) {
					int i = TK.compareFirst(
						l1.getText().equalsIgnoreCase("English"),
						l2.getText().equalsIgnoreCase("English")
					);

					if (i != 0)
						return i;

					return l1.getText().compareToIgnoreCase(l2.getText());
				}
			} );
		}
		catch (ParseException exception) {
			MLogger.developerException(exception);
		}

		String titleCode;
		try {
			titleCode = TK.substring(code, "<title>", "</title>", true);
		}
		catch (ParseException exception) {
			MLogger.developerException(exception);
			titleCode = "";
		}

		try {
			code = TK.substring(code, "<div id=\"bodyContent\">", "<!-- /bodyContent -->", false);
		}
		catch (ParseException exception) {
			log.warningFormat("Unknown body content: %s", exception);
		}

		// remove "Edit" links
		code = code.replaceAll(
			Pattern.quote("<span class=\"editsection\">[<a href=") +
			".+" +
			Pattern.quote("</a>]</span>"),
			""
		);

		// remove CDATA which is rendered incorrectly
		StringBuilder buf = new StringBuilder(code);
		TK.fastReplace(buf, "//<![CDATA[", "");
		TK.fastReplace(buf, "//]]>", "");

		// multiple class names are not supported
		TK.fastReplace(buf, " class=\"external text\" ", " class=\"external\" ");

		// use larger font
		TK.fastReplace(buf, "font-size: x-small;", "font-size:normal;");//not safe !!!
		TK.fastReplace(buf, "font-size: 85%;", "font-size: normal;");
		TK.fastReplace(buf, "font-size: 90%;", "font-size: normal;");
		TK.fastReplace(buf, "font-size:x-small;", "font-size:normal;");
		TK.fastReplace(buf, "font-size:85%;", "font-size:normal;");
		TK.fastReplace(buf, "font-size:90%;", "font-size:normal;");
		TK.fastReplace(buf, "font-size:0.9em;", "font-size:normal;");

		buf.insert(0, "<html><head>" + titleCode + "</head><body>");
		buf.append("</body></html>");
		
		return Tuple.of(buf.toString(), languageList);
	}

	private synchronized static URICache getCache() {
		if (_cache == null)
			_cache = new URICache();

		return _cache;
	}

	private void loadPage(final URI uri, final boolean updateHistory) {
		CacheItem cacheItem = getCache().get(uri);

		try {
			if ((cacheItem != null) && (cacheItem.parsedHTML != null)) {
				cacheItem.time = System.currentTimeMillis();
				pageURI = uri;
				setParsedHTML(cacheItem.parsedHTML, uri, updateHistory, cacheItem.languageList);
				updateComponents();
			}
			else {
				setPageURI(uri, updateHistory);
			}
		}
		catch (Exception exception) {
			MMessage.error(getWindowAncestor(), exception);
		}
	}

	private void setHTML(final String html, final URI uri, final boolean updateHistory, final MArrayList<Language> languageList) {
		try {
			WikiPanel.getCache().put(uri, new CacheItem(html, languageList));
			setParsedHTML(html, uri, updateHistory, languageList);
		}
		catch (Exception exception) {
			MMessage.error(WikiPanel.this.getWindowAncestor(), exception);
		}
	}

	private void setPageURI(final URI uri, final boolean updateHistory) {
		pageURI = Objects.requireNonNull(uri);

		log.debugFormat("Set page URI: %s (update history = %s)", uri, updateHistory);

		if (updateHistory)
			getWebBrowser().addHistory(pageURI);

		addressButton.setURI(uri);
		addressButton.setText(uri.getHost());

		//!!!viewer.uninstallCache();

		if (downloader != null)
			downloader.abort();

		downloader = new MSwingWorker<Tuple.Two<String, MArrayList<Language>>>(this, "Wiki", MSwingWorker.Option.WAIT_CURSOR) {
			@Override
			protected Tuple.Two<String, MArrayList<Language>> doInBackground() throws Exception {
				return WikiPanel.this.downloadPage();
			}
			@Override
			protected void onSuccess(final Tuple.Two<String, MArrayList<Language>> result) {
				String html = result.get1();
				MArrayList<Language> languageList = result.get2();
				WikiPanel.this.setHTML(html, WikiPanel.this.pageURI, true, languageList);
				WikiPanel.this.updateComponents();
			}
		};
		downloader.start();
	}

	private void setParsedHTML(final String parsedHTML, final URI uri, final boolean updateHistory, final MArrayList<Language> languageList) throws Exception {
		//!!!getViewer().installCache(Net.DOWNLOAD_USE_CACHE | Net.DOWNLOAD_NO_CACHE_UPDATE);

		languageComboBox.setEventsEnabled(false);
		languageComboBox.removeAllItems();
		languageComboBox.addItem(selectLanguage);
		languageComboBox.addAllItems(languageList);
		languageComboBox.setEventsEnabled(true);
		languageComboBox.setVisible(!languageComboBox.isEmpty());

		if (updateHistory)
			getWebBrowser().addHistory(uri);

		HTMLBuilder html = new HTMLBuilder();
		html.beginStyle();
			html.beginRule("body");
				html.addAttr("margin", "0px");
				html.addAttr("padding", "5px");
			html.endRule();
			html.beginRule("a.external");
				html.addAttr("color", MColor.DARK_GREEN);
			html.endRule();
			html.beginRule("a.new");
				html.addAttr("color", MColor.RED);
			html.endRule();
			html.beginRule("code");
				html.addAttr("font-size", "larger");
			html.endRule();
			html.beginRule("small");
				html.addAttr("font-size", "normal");
			html.endRule();
		html.endStyle();

		html.append(parsedHTML);

		getWebBrowser().setDocumentContent(html.toString(), uri);
	}

	private void updateComponents() {//!!!disable/enable during load
	}
	
	// public classes
	
	/**
	 * @since 4.0
	 */
	public static class Dialog extends MDialog {
	
		// private
		
		private PropertyChangeListener pcl;
		private WeakReference<WikiPanel> panelRef;
	
		// public
		
		public Dialog(final Window owner, final String title, final WikiPanel panel) {
			super(owner, title, SIMPLE_DIALOG | FORCE_STANDARD_BORDER);
			panelRef = new WeakReference<>(panel);
			
			setToolBar(panel.getWebBrowser().getToolBar());
			MPanel p = getMainPanel();
			p.setLayout(new BorderLayout());
			p.setMargin(0, 0, WikiPanel.DEFAULT_CONTENT_MARGIN, 0);
			addCenter(panel);
			setSize(UI.WindowSize.MEDIUM);

			pcl = new PropertyChangeListener() {
				@Override
				public void propertyChange(final PropertyChangeEvent e) {
					if (TK.isProperty(e, WebBrowser.PROGRESS_PROPERTY, 100))
						setTitle(getWikiPanel().getWebBrowser().getDocumentDisplayTitle(null));
				}
			};
			panel.getWebBrowser().addPropertyChangeListener(pcl);
		}
		
		@Override
		public boolean exec() {
			return exec(getWikiPanel());
		}
		
		public WikiPanel getWikiPanel() {
			return TK.get(panelRef);
		}
		
		// protected
		
		@Override
		protected void onClose() {
			super.onClose();
			
			WikiPanel panel = getWikiPanel();
			if (panel != null) {
				panel.getWebBrowser().removePropertyChangeListener(pcl);
				pcl = null;
			}
		}
	
	}

	/**
	 * @since 4.4
	 */
	public static final class Language extends Item<String> {
	
		// public
		
		public Language(final String value, final String text) {
			super(value, text);
		}
		
		public URI toURI() {
			String href = getValue();
			if (href.startsWith("//"))
				href = "http:" + href;
			
			return URI.create(href);
		}
	
	}

	// private classes

	private static final class CacheItem {

		// private

		private long time;
		private final MArrayList<Language> languageList = new MArrayList<>();
		private final String parsedHTML;

		// private

		private CacheItem(final String parsedHTML, final MArrayList<Language> languageList) {
			time = System.currentTimeMillis();
			this.parsedHTML = parsedHTML;
			this.languageList.addAll(languageList);
		}

	}
	
	private static final class URICache extends HashMap<URI, CacheItem> {

		// private

		private final long maxTime;

		// private

		private URICache() {
			maxTime = TimeUnit.MINUTES.toMillis(5);

			new MTimer(TimeUnit.MINUTES, 5) {
				@Override
				protected boolean onTimeout() {
					if (URICache.this.isEmpty()) {
						//MLogger.debug("wiki", "Cache is empty");

						return true;
					}

					//MLogger.debug("wiki", "Checking %d cached item(s)", URICache.this.size());

					Iterator<Map.Entry<URI, CacheItem>> it = URICache.this.entrySet().iterator();
					long now = System.currentTimeMillis();
					while (it.hasNext()) {
						Map.Entry<URI, CacheItem> entry = it.next();
						if ((now - entry.getValue().time) >= URICache.this.maxTime) {
							//MLogger.debug("wiki", "Removing expired entry: " + entry.getKey());
							it.remove();
						}
					}

					return true;
				}
			}.start();
		}

	}

}
