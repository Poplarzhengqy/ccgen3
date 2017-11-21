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

package org.makagiga.commons.about;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.makagiga.commons.FS;
import org.makagiga.commons.Gettext;
import org.makagiga.commons.Globals;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.MGraphics2D;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.MStringBuilder;
import org.makagiga.commons.OS;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.fx.Blend;
import org.makagiga.commons.fx.Reflection;
import org.makagiga.commons.html.Linkify;
import org.makagiga.commons.html.MHTMLViewer;
import org.makagiga.commons.mods.Mods;
import org.makagiga.commons.swing.MDialog;
import org.makagiga.commons.swing.MEditorPane;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MLinkButton;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MScrollPane;
import org.makagiga.commons.swing.MTab;
import org.makagiga.commons.swing.MTabbedPane;
import org.makagiga.commons.swing.MTimer;

/**
 * An <i>About</i> dialog.
 * Displays information about the application
 * (name, version, copyright, license, credits, system info, etc).
 *
 * @see org.makagiga.commons.swing.MMainWindow#about()
 *
 * @since 3.0, 4.0 (org.makagiga.commons.about package)
 */
public class MAboutDialog extends MDialog {
	
	// private

	private boolean aboutApplication;
	private Image largeLogo;
	private LinkedList<JComponent> blendList = new LinkedList<>();
	private MLabel copyright;
	private MLabel descriptionLabel;
	private MLinkButton bugs;
	private MLinkButton licenseButton;
	private MPanel credits;
	private MScrollPane creditsScrollPane;
	private MTab licenseTab;
	private MTab systemTab;
	private MTabbedPane<?> tabs;
	private MTimer blendTimer;
	private URI licenseURI;

	// public

	/**
	 * Constructs an @b About dialog with the default application information.
	 * @param owner the owner window
	 *
	 * @see org.makagiga.commons.swing.MMainWindow#about()
	 */
	public MAboutDialog(final Window owner) {
		this(
			owner,
			(MApplication.getLogo() == null) ? null : new MIcon(MApplication.getLogo()),
			MApplication.getTitle(),
			MApplication.getDescription()
		);
		setLargeLogo(MApplication.getLogo());
		aboutApplication = true;

		setCopyright(MApplication.getCopyright());
		setHomePage(MApplication.getHomePage());
		setLicense(null, null); // read from resources
		
		creditsScrollPane = new MScrollPane(MScrollPane.NO_BORDER_AUTO);
		systemTab = new MTab(_("System"));
		
		tabs.addTab(_("Credits"), creditsScrollPane);
		tabs.addTab(systemTab);
	}

	/**
	 * Constructs an @b About dialog.
	 *
	 * @param owner the owner window
	 * @param icon The application logo icon (can be @c null)
	 * @param name The application name (can be @c null)
	 * @param description the application description (can be @c null)
	 */
	public MAboutDialog(
		final Window owner,
		final Icon icon,
		final String name,
		final String description
	) {
		super(owner, name, icon, SIMPLE_DIALOG | LINK_BUTTON);
		setLargeLogo(icon);

		tabs = new MTabbedPane<>();
		tabs.addTab(initGeneralTab());
		addCenter(tabs);

		setBugs(null);
		setDescription(description);
	}
	
	/**
	 * Adds credit to the <i>Credits</i> tab.
	 *
	 * @param name A name
	 * @param description A description
	 * @param url An URL (www or email)
	 */
	public void addCredit(final String name, final String description, final String url) {
		addCredit(name, description, url, null);
	}

	/**
	 * @since 3.8.8
	 */
	public void addCredit(final String name, final String description, final String url, final License license) {
		MStringBuilder urlTitle = new MStringBuilder();
		
		// name
		if (name != null) {
			urlTitle.append(name);
			
			MLabel header = credits.addHeader(name);
			header.setForeground(Color.WHITE);
			blendList.add(header);
		}

		// description
		if (description != null) {
			if (!urlTitle.isEmpty())
				urlTitle.append(" - ");
			urlTitle.append(description);
			
			addCreditsLabel(description);
		}

		// url
		if (url != null) {
			MLinkButton urlLink = new MLinkButton();
			urlLink.setStyle("margin: 2 2 2 10");
			urlLink.setURIAndText(URI.create(url));
			if (!urlTitle.isEmpty())
				urlLink.setTitle(urlTitle.toString());
			credits.add(urlLink);
		}
		
		if (license != null) {
			MLinkButton licenseLink = new MLinkButton(license.getURI(), _("License: {0}", license));
			licenseLink.setStyle("margin: 2 2 2 10");
			credits.add(licenseLink);
		}
	}
	
	@Override
	public boolean exec() {
		tabs.addChangeListener(new ChangeListener() {
			public void stateChanged(final ChangeEvent e) {
				int selectedIndex = tabs.getSelectedIndex();
				// credits
				if (aboutApplication && (selectedIndex == 1) && (credits == null)) {
					initCreditsTab();
				}
				// system
				else if (aboutApplication && (selectedIndex == 2) && (systemTab.getComponentCount() == 0)) {
					initSystemTab();
				}
				// license
				else if (tabs.getSelectedComponent() == licenseTab) {
					initLicenseTab();
				}
			}
		} );

		setSize(UI.WindowSize.MEDIUM);

		return super.exec();
	}

	/**
	 * @since 3.8
	 */
	public MTabbedPane<?> getTabs() { return tabs; }

	/**
	 * @since 3.8
	 */
	public boolean isAboutApplication() { return aboutApplication; }
	
	public void setBugs(final String value) {
		if (TK.isEmpty(value)) {
			bugs.setVisible(false);
		}
		else {
			bugs.setURI(value);
			bugs.setVisible(true);
		}
	}
	
	public void setCopyright(final String value) {
		setMultilineText(copyright, value);
	}

	/**
	 * @since 3.8
	 */
	public void setDescription(final String value) {
		setMultilineText(descriptionLabel, value);
	}

	public void setHomePage(final String url) {
		MLinkButton homePage = getLinkButton();
		if (TK.isEmpty(url)) {
			homePage.setText(null);
			homePage.setURI((String)null);
			homePage.setToolTipText(null);
			homePage.setVisible(false);
		}
		else {
			homePage.setURIAndText(URI.create(url));
			homePage.setVisible(true);
		}
	}

	public void setLicense(final String name, final String url) {
		boolean noLicenseTab = (licenseURI == null);
		if (TK.isEmpty(name) && TK.isEmpty(url)) {
			String licenseURL = MApplication.getResourceString("Application.x.licenseURL", null);
			if (licenseURL == null) {
				licenseButton.setText(null);
				licenseButton.setURI((String)null);
				licenseButton.setVisible(false);
			}
			else {
				String licenseName = MApplication.getResourceString("Application.x.licenseName", null);
				if (licenseName == null)
					licenseName = "?";
				licenseButton.setText(_("License: {0}", licenseName));
				licenseButton.setURI(licenseURL);
				licenseButton.setVisible(noLicenseTab);
			}
		}
		else {
			licenseButton.setText(_("License: {0}", name));
			licenseButton.setURI(url);
			licenseButton.setVisible(noLicenseTab);
		}
	}

	/**
	 * @since 3.8.1
	 */
	public void setLicenseURI(final URI value) {
		licenseURI = Objects.requireNonNull(value);
		if (licenseTab == null) {
			licenseButton.setVisible(false);
			licenseTab = new MTab(_("License"));
			tabs.addTab(licenseTab);
		}
	}

	// protected

	@Override
	protected void onClose() {
		super.onClose();
		if (blendList != null) {
			blendList.clear();
			blendList = null;
		}
		blendTimer = TK.dispose(blendTimer);
		largeLogo = null;
	}
		
	// private

	private void addCreditsLabel(final String text) {
		MLabel label = new MLabel(text);
		label.setForeground(Color.WHITE);
		label.setStyle("margin: 0 0 0 10");
		blendList.add(label);
		credits.add(label);
	}
	
	private void initCreditsTab() {
		blendTimer = new MTimer(70) {
			@Override
			protected boolean onTimeout() {
				if (blendList.isEmpty())
					return false; // stop

				JComponent c = blendList.pollFirst();
				Blend.animateForeground(c, Color.WHITE, Color.BLACK);

				return true; // continue
			}
		};

		credits = MPanel.createVBoxPanel();
		credits.setContentMargin();
		credits.setBackground(Color.WHITE);
		credits.setForeground(Color.BLACK);

		String lastTranslator = Gettext.getMetaInfo("Last-Translator");
		if (!TK.isEmpty(lastTranslator)) {
			addCredit(lastTranslator, _("Translation - {0}", Locale.getDefault().getDisplayLanguage()), null);
		}

		License ccas3 = new License("Creative Common Attribution-ShareAlike 3.0 license", URI.create("http://creativecommons.org/licenses/by-sa/3.0/"));
		License lgpl21 = new License("GNU Lesser General Public License 2.1", URI.create("http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"));
		License mit = new License("MIT License", URI.create("http://en.wikipedia.org/wiki/MIT_License"));

		addCredit("Kirill Grouchnikov", "Trident - an animation library", "http://kenai.com/projects/trident/pages/Home", License.BSD);
		addCredit("The Oxygen Team", "Oxygen Icon Theme", "http://www.oxygen-icons.org", ccas3);
		addCredit("Everaldo Coelho and the KDE Team", "Crystal SVG Icon Theme", "http://www.everaldo.com");
		addCredit("SwingLabs SwingX Project Team", "Drop Shadow Border implementation", "http://swinglabs.org/", lgpl21);
		addCredit("Jeff Friesen, InformIT.com", "Icon Reader", "http://www.informit.com/articles/article.aspx?p=1186882", License.BSD);
		addCredit("Jerry Huxtable", "Image Filters", "http://www.jhlabs.com/ip/index.html", License.APACHE_V2);
		addCredit("The Apache Software Foundation", "Apache Commons Codec", "http://commons.apache.org/codec/", License.APACHE_V2);
		addCredit("Mikael Grev", "MiGBase64 - A high performance base64 codec", "http://migbase64.sourceforge.net/", License.BSD);
		addCredit("Eirik Bjorsnos", "Macify Library", "http://simplericity.com/2007/10/02/1191336060000.html", License.APACHE_V2);
		addCredit("Romain Guy", "Java/Swing Special Effects", "http://www.curious-creature.org/");
		addCredit("Santhosh Kumar", "Java/Swing Ideas", "http://www.jroller.com/page/santhosh");
		addCredit("Scott Violet", "Ocean Gradient Customizer", "http://weblogs.java.net/blog/zixle/archive/2005/09/customizing_oce_1.html");
		addCredit("Konrad Twardowski", "Makagiga Platform", "http://sourceforge.net/apps/mediawiki/makagiga/index.php?title=SDK", License.APACHE_V2);
		
		addCredit("Portland", "Xdg-utils", "http://portland.freedesktop.org/", mit);
		addCredit("SourceForge.net", "Project Hosting", "http://sourceforge.net");
		addCredit("Yellowicon Studio", "\"Emotion\" Icon Set", "http://www.yellowicon.com");

		Mods.exec(this, Globals.MOD_ABOUT_INIT_CREDITS);
		
		// add Duke

		Image dukeImage = MIcon.getImage("duke.jpg");
		Reflection r = new Reflection();
		r.setBlur(true);
		MLabel duke = new MLabel(r.createImage(dukeImage));
		duke.setStyle("margin: " + (credits.getContentMargin() * 2));
		duke.setToolTipText("Duke");
		credits.add(duke);
		
		creditsScrollPane.setScrollSpeed(20);
		creditsScrollPane.setViewportView(credits);

		blendTimer.start();
	}
	
	private MTab initGeneralTab() {
		bugs = new MLinkButton();
		bugs.setText(_("Report Bug..."));

		copyright = new MLabel();

		licenseButton = new MLinkButton();
		descriptionLabel = new MLabel();

		MTab tab = new MTab(_("General")) {
			@Override
			protected void paintComponent(final Graphics graphics) {
				super.paintComponent(graphics);
				Image i = MAboutDialog.this.largeLogo;
				if (i != null) {
					try (MGraphics2D g = MGraphics2D.copy(graphics)) {
						g.setAlpha(0.05f);
						g.drawImage(
							i,
							this.getWidth() - i.getWidth(null) - MTab.DEFAULT_CONTENT_MARGIN * 2,
							this.getHeight() - i.getHeight(null) - MTab.DEFAULT_CONTENT_MARGIN * 2
						);
					}
				}
			}
		};
		tab.setMargin(tab.getContentMargin() * 2);
		tab.setGroupLayout(false)
			.beginRows()
				.addComponent(descriptionLabel)
				.addGap(tab.getContentMargin() * 2)
				.addComponent(copyright)
				.addGap()
				.addComponent(licenseButton)
				.addGap()
				.addStretch()
				.addComponent(bugs)
			.end();

		return tab;
	}

	private void initLicenseTab() {
		if (licenseTab.getComponentCount() == 0) {
			File file = new File(licenseURI);
			try (FS.TextReader reader = FS.getUTF8Reader(file)) {
				long len = file.length();
				StringBuilder licenseText = new StringBuilder((int)len);
				FS.readLines(reader, licenseText);
				if (len > 15000) {
					MEditorPane viewer = MEditorPane.newPlainText(licenseText.toString(), false);
					licenseTab.addCenter(viewer, MScrollPane.NO_BORDER_AUTO);
				}
				else {
					try {
						UI.setWaitCursor(this, true);
						
						licenseText = TK.escapeXML(null, licenseText);
					
						MHTMLViewer viewer = new MHTMLViewer();
						licenseTab.addCenter(viewer, MScrollPane.NO_BORDER_AUTO);
					
						Linkify linkify = Linkify.getInstance();
						viewer.setHTML("<pre>" + linkify.applyToHTML(licenseText) + "</pre>");
					}
					finally {
						UI.setWaitCursor(this, false);
					}
				}
				licenseTab.validate();
			}
			catch (IOException exception) {
				MMessage.error(this, exception);
			}
		}
	}
	
	private void initSystemTab() {
		String summaryText;
		try {
			UI.setWaitCursor(this, true);
			summaryText = OS.getSummary(true);
		}
		finally {
			UI.setWaitCursor(this, false);
		}
		MEditorPane summary = MEditorPane.newPlainText(summaryText, false);
		systemTab.addCenter(summary, MScrollPane.NO_BORDER_AUTO);
	}
	
	private void setLargeLogo(final Object logo) {
		Image i;
		if (logo instanceof Icon)
			i = UI.toBufferedImage((Icon)logo);
		else if (logo instanceof Image)
			i = (Image)logo;
		else
			return;
	
		largeLogo = UI.scaleImage(i, 200, 200, UI.Quality.HIGH);
	}

	private static void setMultilineText(final MLabel l, final String value) {
		if (TK.isEmpty(value)) {
			l.setText(null);
			l.setVisible(false);
		}
		else {
			l.setMultilineText(value);
			l.setVisible(true);
		}
	}

}
