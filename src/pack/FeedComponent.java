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

package org.makagiga.feeds;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.beans.ConstructorProperties;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import org.makagiga.commons.MColor;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.Net;
import org.makagiga.commons.TK;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.annotation.InvokedFromConstructor;
import org.makagiga.commons.html.HTMLBuilder;
import org.makagiga.commons.html.MHTMLViewer;
import org.makagiga.commons.mv.MRenderer;
import org.makagiga.commons.swing.MComboBox;
import org.makagiga.commons.swing.MLinkButton;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MScrollPane;
import org.makagiga.commons.swing.MTaskPanel;
import org.makagiga.feeds.archive.Archive;
import org.makagiga.feeds.archive.ArchiveItem;

public class FeedComponent extends MTaskPanel<Object> {
	
	// public
	
	public enum Type { COMBO_BOX, COMBO_BOX_AND_VIEWER, HTML_LIST };
	
	// private

	private boolean fullTextVisible;
	private ChannelInfo channelInfo;
	private int archiveFlags;
	private int articleLimit;
	private int newItems;
	private MComboBox<ArchiveItem> comboBox;
	private MHTMLViewer viewer;
	private MLinkButton linkButton;
	private MPanel viewerPanel;
	private String styleSheet;
	private Type type;
	
	// public
	
	public FeedComponent() {
		this(Type.HTML_LIST);
	}

	@ConstructorProperties("type")
	public FeedComponent(final Type type) {
		viewerPanel = MPanel.createBorderPanel(5);
		setMainComponent(viewerPanel);
		setType(type);
	}

	public void cancel() {
		synchronized (this) {
			channelInfo = null;
		}
		cancel(true);
	}
	
	/**
	 * @since 2.0
	 */
	public synchronized void download(final String url) {
		cancel();
		
		setComponentsEnabled(false, null);
		
		channelInfo = new ChannelInfo(new Feed(), url);

		start();
	}
	
	public int getArchiveFlags() { return archiveFlags; }
	
	public void setArchiveFlags(final int value) { archiveFlags = value; }

	/**
	 * @since 3.4
	 */
	public int getArticleLimit() { return articleLimit; }

	/**
	 * @since 3.4
	 */
	public void setArticleLimit(final int value) { articleLimit = value; }

	/**
	 * @since 2.0
	 */
	public ChannelInfo getChannelInfo() { return channelInfo; }

	public MComboBox<ArchiveItem> getComboBox() { return comboBox; }
	
	/**
	 * @since 2.0
	 */
	public MLinkButton getLinkButton() { return linkButton; }
	
	public int getNewItemCount() { return newItems; }
	
	/**
	 * Returns a style sheet (CSS) for @ref Type.HTML_LIST.
	 * Returns @c null if user style sheet is not set.
	 * 
	 * @since 2.4
	 */
	public String getStyleSheet() { return styleSheet; }

	/**
	 * Sets the style sheet (CSS).
	 * This works only with @ref Type.HTML_LIST.
	 * 
	 * @since 2.4
	 */
	public void setStyleSheet(final String value) { styleSheet = value; }
	
	/**
	 * @since 1.2
	 */
	@Override
	public String getTitle() {
		if (
			(channelInfo != null) &&
			(channelInfo.channel != null) &&
			channelInfo.channel.isTitlePresent()
		)
			return channelInfo.channel.getTitle();
		
		return _("Feed");
	}
	
	/**
	 * @since 2.0
	 */
	public Type getType() { return type; }

	/**
	 * @since 2.0
	 */
	@InvokedFromConstructor
	public void setType(final Type value) {
		if (Objects.equals(type, value))
			return;
		
		type = value;
		
		viewerPanel.removeAll();
		
		if ((type == Type.COMBO_BOX) || (type == Type.COMBO_BOX_AND_VIEWER)) {
			comboBox = new MComboBox<ArchiveItem>() {
				@Override
				protected void onSelect() {
					ArchiveItem item = getSelectedItem();
					
					if (viewer != null) {
						if (item == null)
							viewer.clear();
						else
							FeedComponent.this.setHTML(item.getText());
					}
					
					if ((item != null) && item.isLinkPresent()) {
						try {
							linkButton.setURIAndText(URI.create(item.getLink()));
							//linkButton.setText(_("Complete Story"));
							linkButton.setVisible(true);
						}
						catch (IllegalArgumentException exception) {
							// malformed URI
							linkButton.setVisible(false);
						}
					}
					else {
						linkButton.setVisible(false);
					}
				}
			};
			comboBox.setRenderer(new MRenderer<ArchiveItem>() {
				@Override
				protected void onRender(final ArchiveItem item) {
					// limit combo box width
					this.setText(TK.rightSqueeze(item.toString(), 50));
				}
			} );
			viewerPanel.addNorth(MPanel.createHLabelPanel(comboBox, _("Articles:")));
			
			linkButton = new MLinkButton();
			linkButton.setSecureOpen(true);
			viewerPanel.addSouth(linkButton);
		}

		if ((type == Type.COMBO_BOX_AND_VIEWER) || (type == Type.HTML_LIST)) {
			viewer = new MHTMLViewer();
			viewer.setSecureOpen(true);
			viewerPanel.addCenter(viewer, MScrollPane.NO_BORDER_AUTO);
		}
		
		if (type == Type.HTML_LIST) {
			viewer.setStyle("background-color: white; color: black; margin: 0; padding: 0");
		}

		// update components
		if ((channelInfo != null) && (channelInfo.channel != null) && (channelInfo.archiveItems != null))
			showItems(channelInfo.channel, channelInfo.archiveItems);
	}
	
	/**
	 * @since 2.0
	 */
	public String getURL() {
		return (channelInfo == null) ? null : channelInfo.url;
	}

	/**
	 * @since 2.0
	 */
	public void setURL(final String value) {
		if (!Objects.equals(getURL(), value))
			download(value);
	}
	
	/**
	 * @since 2.4
	 */
	public MHTMLViewer getViewer() { return viewer; }
	
	/**
	 * @since 2.4
	 */
	public boolean isFullTextVisible() { return fullTextVisible; }

	/**
	 * @since 2.4
	 */
	public void setFullTextVisible(final boolean value) { fullTextVisible = value; }

	// protected

	@Override
	protected Object doInBackground() throws Exception {
		channelInfo.channel = channelInfo.feed.download(channelInfo.url);

		return null;
	}

	@Override
	protected void done() {
		if (isCancelled()) {
			setComponentsEnabled(true, null);
			if (channelInfo != null)
				channelInfo.feed.cancelDownload();
		}
		else {
			String errorMessage = null;
			try {
				get();

				Archive archive = Archive.getInstance();
				newItems = 0;

				Feed feed = channelInfo.feed;
				URL source = feed.getSource();
				channelInfo.archiveItems = archive.merge(source.toString(), channelInfo.channel, archiveFlags);
				if (channelInfo.archiveItems != null) {
					for (ArchiveItem i : channelInfo.archiveItems) {
						if (i.isNew())
							newItems++;
					}
					showItems(channelInfo.channel, channelInfo.archiveItems);
				}
			}
			catch (Exception exception) {
				Throwable cause = exception.getCause();
				if (cause instanceof FeedListException) {
					String address = FeedUtils.getAddressFromList(FeedListException.class.cast(cause).getFeed());
					if (address != null) {
						download(address);
					}
					else {
						errorMessage = _("Error");
					}
				}
				else {
					MLogger.developerException(exception);
					errorMessage = TK.buildErrorMessage((cause == null) ? exception : cause);
				}
			}
			finally {
				setComponentsEnabled(true, errorMessage);
			}
		}
	}
	
	/**
	 * @since 3.0
	 */
	protected void setComponentsEnabled(final boolean enabled, final String errorMessage) {
		if (comboBox != null)
			comboBox.setEnabled(enabled);
		if (linkButton != null)
			linkButton.setEnabled(enabled);
		if (viewer != null)
			viewer.setEnabled(enabled);
		if (enabled && (errorMessage != null))
			setErrorMessage(errorMessage);
	}
	
	protected void showItems(final AbstractChannel<?> channel, final List<ArchiveItem> items) {
		switch (type) {
			case COMBO_BOX:
			case COMBO_BOX_AND_VIEWER:
				showItemsInComboBox(channel, items);
				break;
			case HTML_LIST:
				showItemsInHTMLList(channel, items);
				break;
			default:
				throw new WTFError(type);
		}
	}
	
	protected void showItemsInComboBox(final AbstractChannel<?> channel, final List<ArchiveItem> items) {
		if (comboBox == null)
			return;

		comboBox.setEventsEnabled(false);
		try {
			comboBox.removeAllItems();
		}
		finally {
			comboBox.setEventsEnabled(true);
		}
		if (!items.isEmpty()) {
			if (articleLimit < 1) // no limit
				comboBox.addAllItems(items);
			else
				comboBox.addAllItems(items.subList(0, Math.min(items.size(), articleLimit)));
		}
	}

	protected void showItemsInHTMLList(final AbstractChannel<?> channel, final List<ArchiveItem> items) {
		if (viewer == null)
			return;
		
		Color articleBackground1 = MColor.deriveColor(Color.WHITE, 0.98f);
		Color articleBackground2 = MColor.deriveColor(Color.WHITE, 0.92f);
		
		HTMLBuilder html = new HTMLBuilder();
		html.beginHTML();
		html.beginStyle();
			html.beginRule("a");
				html.addAttr("text-decoration", "none");
			html.endRule();
			html.beginRule("td.article1");
				html.addAttr("background-color", articleBackground1);
			html.endRule();
			html.beginRule("td.article2");
				html.addAttr("background-color", articleBackground2);
			html.endRule();
			
			// add user style sheet
			if (styleSheet != null)
				html.appendLine(styleSheet);
		html.endStyle();
		html.beginDoc();
		html.beginTag("table", "cellpadding", 5, "cellspacing", 0, "width", "100%");
		boolean odd = false;
		List<ArchiveItem> list = items;
		if (!items.isEmpty() && (articleLimit > 0)) {
			list = items.subList(0, Math.min(items.size(), articleLimit));
		}
		for (ArchiveItem i : list) {
			odd = !odd;
			String style = i.isNew() ? "font-weight: bold" : "";
			String tdClass = odd ? "article1" : "article2";
			String title = TK.centerSqueeze(i.toString(), 128);
			// title and link
			html.beginTag("tr");
			html.doubleTag(
				"td", HTMLBuilder.createLink(i.getLink(), title),
				"class", tdClass,
				"style", style
			);
			html.endTag("tr");
			
			// full text
			if (fullTextVisible) {
				html.beginTag("tr");
				html.doubleTag(
					"td", i.getText(),
					"class", tdClass
				);
				html.endTag("tr");
			}
		}
		html.endTag("table");
		html.endDoc();
		setHTML(html.toString());
	}

	// private

	private void setHTML(final String html) {
		viewer.installCache(Net.DOWNLOAD_USE_CACHE | Net.DOWNLOAD_NO_CACHE_UPDATE);
		viewer.getImageDownloader().setImageFactory(new FeedUtils.BlockImageFactory());
		viewer.setHTML(html);
	}

	// public classes
	
	/**
	 * @since 2.0
	 */
	public static final class ChannelInfo {
		
		// private

		private AbstractChannel<?> channel;
		private final Feed feed;
		private List<ArchiveItem> archiveItems;
		private final String url;
		
		// public
		
		public AbstractChannel<?> getChannel() { return channel; }
		
		public Feed getFeed() { return feed; }
		
		public String getURL() { return url; }
		
		// private
		
		private ChannelInfo(final Feed feed, final String url) {
			this.feed = feed;
			this.url = url;
		}
		
	}

}
