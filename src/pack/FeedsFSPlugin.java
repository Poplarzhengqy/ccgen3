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

package org.makagiga.fs.feeds;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.net.URI;

import org.makagiga.Tabs;
import org.makagiga.Vars;
import org.makagiga.commons.Config;
import org.makagiga.commons.EnumProperty;
import org.makagiga.commons.Globals;
import org.makagiga.commons.Lockable;
import org.makagiga.commons.MAction;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.Property;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.color.MSmallColorChooser;
import org.makagiga.commons.swing.ActionGroup;
import org.makagiga.commons.swing.MCheckBox;
import org.makagiga.commons.swing.MFontButton;
import org.makagiga.commons.swing.MMenu;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MNumberSpinner;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MStatusBar;
import org.makagiga.commons.swing.MToolBar;
import org.makagiga.editors.Editor;
import org.makagiga.editors.NavigationUtils;
import org.makagiga.feeds.archive.Archive;
import org.makagiga.fs.AbstractFS;
import org.makagiga.fs.FSException;
import org.makagiga.fs.FSPlugin;
import org.makagiga.fs.MetaInfo;
import org.makagiga.plugins.PluginException;
import org.makagiga.plugins.PluginMenu;
import org.makagiga.plugins.PluginOptions;
import org.makagiga.tree.Tree;

/** The "Feeds" plugin. */
public final class FeedsFSPlugin extends FSPlugin
implements
	PluginMenu,
	PluginOptions<MPanel>
{
	
	// public
	
	public static final int DEFAULT_FETCH_INTERVAL = 60;
	public static final int MIN_FETCH_INTERVAL = 10;
	public static final int MAX_FETCH_INTERVAL = Integer.MAX_VALUE;
	
	// package
	
	final ActionGroup actions = new ActionGroup();
	static FeedsFS _fsInstance;

	// public
	
	/**
	 * Creates and returns the singleton instance of the {@code FeedsFS}.
	 *
	 * @throws FSException If initialization failed
	 * @throws IllegalStateException If already created
	 */
	@Override
	public AbstractFS create() throws Exception {
		if (_fsInstance == null)
			_fsInstance = new FeedsFS(getInfo());
		else
			throw new IllegalStateException("\"org.makagiga.fs.feeds.FeedsFS\" instance is already created");
			
		return _fsInstance;
	}
	
	@Override
	public void onDestroy() throws PluginException {
		super.onDestroy();
		FeedDownloader.abortAll();
		Archive.shutDown();
	}
	
	@Override
	public void onPostInit() throws PluginException {
		super.onPostInit();
		if (!Vars.treeReadOnly.get()) {
			Globals.addLinkAction(new MAction(_("Add Feed"), "ui/feed") {
				@Override
				public void onAction() {
					URI uri = this.getValue(Globals.LINK_URI, null);
					FeedsFSPlugin.this.addFeed(uri.toString());
				}
				@Override
				public boolean isEnabled() {
					URI uri = this.getValue(Globals.LINK_URI, null);
					String s = uri.getScheme();
					
					return "feed".equals(s) || "file".equals(s) || "http".equals(s) || "https".equals(s);
				}
			} );
		}
		
		FeedsFS fs = FeedsFS.self();
		if (fs != null) {
			Settings settings = fs.reloadSettings(this);
			fs.setUnreadColor(settings.unreadColor);
			if (settings.fetchOnStartup && !MApplication.offline.booleanValue()) {
				fs.fetchFeeds(fs.getRoot(), FeedsFS.USE_EXCLUDE_FROM_FETCH_ALL_FEEDS);
			}
			else {
				fs.fetchFeeds(fs.getRoot(), FeedsFS.FETCH_OFFLINE);
			}
		}
	}
	
	// PluginMenu
	
	/**
	 * @since 2.0
	 */
	@Override
	public void updateMenu(final String type, final MMenu menu) {
		if (FILE_MENU.equals(type)) {
			if (actions.isEmpty()) {
				actions.add("previous-new", new GoToNewAction(false));
				actions.add("next-new", new GoToNewAction(true));
				actions.addSeparator();
				actions.add("previous-unread", new GoToUnreadAction(false));
				actions.add("next-unread", new GoToUnreadAction(true));
			}
			menu.add(actions.createMenu(_("Feeds"), "ui/feed"));
		}
	}

	/**
	 * @since 2.0
	 */
	@Override
	public void updateToolBar(final String type, final MToolBar toolBar) {
		if (type.equals(TREE_TOOL_BAR)) {
			toolBar.add(FeedsFS.self().fetchAllFeedsAction);
		}
	}
	
	// PluginOptions
	
	@Override
	public MPanel createPluginConfigPanel() {
		return new FeedsConfigPanel();
	}
	
	@Override
	public void loadPluginConfig(final MPanel p) {
		if (!(p instanceof FeedsConfigPanel))
			throw new WTFError("Expected FeedsConfigPanel");
	
		FeedsConfigPanel panel = (FeedsConfigPanel)p;
		Settings settings = readGlobalSettings();
		
		// general
		panel.fetchOnStartup.setSelected(settings.fetchOnStartup);
		panel.useIntervalFetching.setSelected(settings.useIntervalFetching);
		panel.minutes.setNumber(settings.minutes);
		panel.minutes.setEnabled(settings.useIntervalFetching);
		
		// archive
		panel.archive.setDays(settings.removeArticlesAfter, settings.archivePolicy);
		
		// view
		panel.articleFont.setValue(FeedViewer.loadFont(getGlobalEntry("font")));
		panel.listFont.setValue(FeedViewer.loadFont(getGlobalEntry("listFont")));
		panel.unreadColor.setValue(settings.unreadColor);
	}
	
	@Override
	public void savePluginConfig(final MPanel p) {
		if (!(p instanceof FeedsConfigPanel))
			throw new WTFError("Expected FeedsConfigPanel");

		FeedsConfigPanel panel = (FeedsConfigPanel)p;
		Config config = Config.getDefault();
		
		// general
		config.write(getGlobalEntry("fetchOnStartup"), panel.fetchOnStartup.isSelected());
		config.write(getGlobalEntry("useIntervalFetching"), panel.useIntervalFetching.isSelected());
		config.write(getGlobalEntry("fetchInterval"), panel.minutes.getNumber());
		
		// archive
		config.write(getGlobalEntry("archivePolicy"), panel.archive.getPolicy().name());
		config.write(getGlobalEntry("removeArticlesAfter"), panel.archive.getDays());
		
		// view
		FeedViewer.saveFont(getGlobalEntry("font"), panel.articleFont.getValue());
		FeedViewer.saveFont(getGlobalEntry("listFont"), panel.listFont.getValue());
		FeedViewer.applyFont();

		FeedsFS fs = FeedsFS.self();
		fs.setUnreadColor(panel.unreadColor.getValue());
		config.write(getGlobalEntry("unread"), fs.getUnreadColor());

		fs.reloadSettings(this);

		// update colors
		new Tree.Scanner(fs) {
			@Override
			public void processItem(final MetaInfo item) {
				if (item instanceof FeedsFS.ArticleMetaInfo)
					item.refresh(false);
			}
		};
		Tree.getInstance().repaint();
		for (Editor<?> i : Tabs.getInstance()) {
			if (i instanceof FeedViewer)
				i.repaint();
		}
	}
	
	// protected
	
	Settings readGlobalSettings() { // package
		Settings settings = new Settings();
		settings.readAll(Config.getDefault(), this);
		
		return settings;
	}
	
	/**
	 * Reads the local feed settings.
	 */
	synchronized Settings readSettings(final MetaInfo feed, final boolean readGlobal) { // package
		Config config = feed.getConfig();
		Settings settings = new Settings();

		// NOTE: backward compatibility: do not use "readEnum"
		settings.archivePolicy = EnumProperty.parse(config.read("x.archivePolicy", null), Archive.Policy.USE_GLOBAL_SETTINGS);
		
		if (readGlobal && (settings.archivePolicy == Archive.Policy.USE_GLOBAL_SETTINGS)) {
			settings.readArchive(Config.getDefault(), this);
		}
		else {
			settings.removeArticlesAfter = config.readInt("x.removeArticlesAfter", Settings.DEFAULT_DAYS, Settings.MIN_DAYS, Settings.MAX_DAYS);
		}

		return settings;
	}

	// private
	
	private void addFeed(final String link) {
		FeedsFS feeds = FeedsFS.self();
		try {
			feeds.newFile(feeds.getRoot(), link);
		}
		catch (FSException exception) {
			MMessage.error(UI.windowFor(null), exception, link);
		}
	}
	
	// public classes
	
	/**
	 * @since 1.2
	 */
	public static abstract class AbstractPanel extends MPanel implements Lockable {
		
		// private
		
		private boolean locked;
		
		// public
		
		public AbstractPanel(final String header, final boolean contentMargin) {
			super(UI.VERTICAL);
			if (contentMargin)
				setContentMargin();
			if (header != null)
				addSeparator(header);
		}
		
		/**
		 * @inheritDoc
		 * 
		 * @since 2.0
		 */
		@Override
		public boolean isLocked() { return locked; }

		/**
		 * @inheritDoc
		 * 
		 * @since 2.0
		 */
		@Override
		public void setLocked(final boolean value) { locked = value; }
		
		// protected
		
		protected boolean shouldRefresh() { return false; }
		
	}

	public static final class FeedsConfigPanel extends AbstractPanel {
		
		// private
		
		private final FeedsFS.ArchiveOptions archive;
		private final MCheckBox fetchOnStartup;
		private final MCheckBox useIntervalFetching;
		private final MFontButton articleFont;
		private final MFontButton listFont;
		private final MNumberSpinner<Integer> minutes;
		private final MSmallColorChooser unreadColor;
		
		// private
		
		private FeedsConfigPanel() {
			super(null, false);

			fetchOnStartup = new MCheckBox(_("Refresh all feeds on startup"));
			add(fetchOnStartup);

			addGap();
			
			useIntervalFetching = new MCheckBox(_("Auto refresh")) {
				@Override
				protected void onClick() {
					minutes.setEnabled(useIntervalFetching.isSelected());
					if (useIntervalFetching.isSelected())
						minutes.makeDefault();
				}
			};
			add(useIntervalFetching);
			
			minutes = new MNumberSpinner<>();
			minutes.setMaximumWidth(150);
			minutes.setRange(MIN_FETCH_INTERVAL, MAX_FETCH_INTERVAL);
			minutes.setToolTipText(_("Minutes"));

			add(createPuzzlePanel(_("Every {0} minute(s)"), minutes));
			
			addContentGap();
			
			archive = new FeedsFS.ArchiveOptions(true);
			add(archive);
			
			addSeparator(_("Font"));

			listFont = new MFontButton(_("List"), null);
			add(listFont);
			
			addGap();

			articleFont = new MFontButton(_("Article"), null);
			add(articleFont);

			addSeparator(_("Color"));

			unreadColor = new MSmallColorChooser(_("Unread Article"));
			unreadColor.setDefaultValue(FeedsFS.DEFAULT_UNREAD_COLOR);
			unreadColor.setResetActionVisible(true);
			add(unreadColor);
			
			addStretch();
		}
		
	}

	// package classes
	
	static final class Settings {
		
		// package
		
		// general

		boolean fetchOnStartup;
		boolean useIntervalFetching;
		int minutes;
		
		// archive

		Archive.Policy archivePolicy;
		int removeArticlesAfter;

		// view

		Color unreadColor;

		static final int DEFAULT_DAYS = 10;
		static final int MAX_DAYS = 60;
		static final int MIN_DAYS = 2;

		// package

		void readAll(final Config config, final Config.GlobalEntry entry) {
			// general

			fetchOnStartup = config.read(entry.getGlobalEntry("fetchOnStartup"), false);
			useIntervalFetching = config.read(entry.getGlobalEntry("useIntervalFetching"), false);
			minutes = config.readInt(entry.getGlobalEntry("fetchInterval"), DEFAULT_FETCH_INTERVAL, MIN_FETCH_INTERVAL, MAX_FETCH_INTERVAL);

			// archive

			readArchive(config, entry);

			// view

			unreadColor = config.readColor(entry.getGlobalEntry("unread"), FeedsFS.DEFAULT_UNREAD_COLOR);
		}
		
		void readArchive(final Config config, final Config.GlobalEntry entry) {
			// NOTE: backward compatibility: do not use "readEnum"
			archivePolicy = EnumProperty.parse(config.read(entry.getGlobalEntry("archivePolicy"), null), Archive.Policy.REMOVE);
			removeArticlesAfter = config.readInt(entry.getGlobalEntry("removeArticlesAfter"), Settings.DEFAULT_DAYS, Settings.MIN_DAYS, Settings.MAX_DAYS);
		}
		
	}

	// private classes
	
	private static abstract class AbstractNavigationAction extends MAction {
	
		// private
	
		private static boolean needSort = true;
		private final boolean next;
		private final NavigationUtils.Filter filter;
		
		// public

		@Override
		public void onAction() {
			final Tree tree = Tree.getInstance();
			
			// ensure the model matches sort method selected by user
			if (needSort) {
				needSort = false;
				new Tree.Scanner(FeedsFS.self()) {
					@Override
					public void processItem(final MetaInfo item) { }
					@Override
					public void processParent(final MetaInfo item) {
						tree.sort(item);
					}
				};
			}

			// 1. search from current item
			MetaInfo open = findArticle(false);
			
			// 2. not found, search from the beginning
			if (open == null) {
				open = findArticle(true);
				if (open != null)
					MStatusBar.warning(_("No item found: \"{0}\"", getName()));
			}

			if (open != null)
				Tree.getInstance().open(open);
			else
				MStatusBar.info(_("No item found: \"{0}\"", getName()));
		}

		// protected
		
		protected AbstractNavigationAction(final String name, final int key, final int modifiers, final boolean next, final NavigationUtils.Filter filter) {
			super(name, next ? "ui/next" : "ui/previous", key, modifiers);
			this.next = next;
			this.filter = filter;
		}

		// private

		private FeedsFS.ArticleMetaInfo findArticle(final boolean searchFromBeginning) {
			Tabs tabs = Tabs.getInstance();
			Editor<?> tab = tabs.getSelectedTab();
			FeedViewer viewer =
				(tab instanceof FeedViewer)
				? (FeedViewer)tab // 1. selected
				: tabs.findEditor(FeedViewer.class); // 2. any

			final Property<MetaInfo> current = new Property<>();
			if (!searchFromBeginning && (viewer != null))
				current.set(viewer.getMetaInfo());
			
			final Property<FeedsFS.ArticleMetaInfo> result = new Property<>();
			new Tree.Scanner(FeedsFS.self()) {//!!!optimize
				private boolean pass;
				private FeedsFS.ArticleMetaInfo found;
				private FeedsFS.ArticleMetaInfo last;
				@Override
				public void processItem(final MetaInfo item) {
					if (!(item instanceof FeedsFS.ArticleMetaInfo))
						return;

					FeedsFS.ArticleMetaInfo article = (FeedsFS.ArticleMetaInfo)item;
					
					// next
					if (next) {
						if (!pass && current.isNull() && filter.matches(article)) {
							found = article;
							pass = true;
						}
						else if (pass && filter.matches(article)) {
							found = article;
						}
					}
// FIXME: previous item
					// previous
					else {
						if (pass && (last != null)) {
							found = last;
						}
					}
					
					if (found != null) {
						result.set(found);
						this.stop();
						
						return;
					}

					if (!pass) {
						if (current.isNull() || (current.get() == article)) {
							pass = true;
						}
					}
					
					if (!next && (article != current.get()) && filter.matches(article))
						last = article;
				}
			};

			return result.get();
		}

	}

	private static final class GoToNewAction extends AbstractNavigationAction {

		// public

		public GoToNewAction(final boolean next) {
			super(
				next
					? _("Next New ({0})", TK.toString(VK_J, 0))
					: _("Previous New ({0})", TK.toString(VK_K, 0)),
				//next ? VK_PERIOD : VK_COMMA,
				0,
				getMenuMask(),
				next,
				new NewFilter()
			);
		}

	}

	private static final class GoToUnreadAction extends AbstractNavigationAction {

		// public

		public GoToUnreadAction(final boolean next) {
			super(
				next ? _("Next Unread") : _("Previous Unread"),
				next ? VK_PERIOD : VK_COMMA,
				getMenuMask() | SHIFT_MASK,
				next,
				new UnreadFilter()
			);
		}

	}

	private static final class NewFilter extends NavigationUtils.Filter {

		// public
		
		public NewFilter() { }

		@Override
		public boolean matches(final MetaInfo metaInfo) {
			if (metaInfo instanceof FeedsFS.ArticleMetaInfo) {
				FeedsFS.ArticleMetaInfo article = (FeedsFS.ArticleMetaInfo)metaInfo;
				
				return article.isNew() || article.wasNew();
			}
			
			return false;
		}

	}

	private static final class UnreadFilter extends NavigationUtils.Filter {

		// public
		
		public UnreadFilter() { }

		@Override
		public boolean matches(final MetaInfo metaInfo) {
			if (metaInfo instanceof FeedsFS.ArticleMetaInfo) {
				FeedsFS.ArticleMetaInfo article = (FeedsFS.ArticleMetaInfo)metaInfo;
				
				return article.isUnread() || article.wasUnread();
			}

			return false;
		}

	}

}
