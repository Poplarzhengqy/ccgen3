// Copyright 2012 Konrad Twardowski
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

package org.makagiga.plugins;

import static org.makagiga.commons.UI._;

// TODO: simplify technical terminology

import java.awt.Color;
import java.io.File;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import javax.swing.BorderFactory;

import org.makagiga.commons.Args;
import org.makagiga.commons.Config;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.crypto.MasterKey;
import org.makagiga.commons.security.PermissionInfo;
import org.makagiga.commons.swing.MCheckBox;
import org.makagiga.commons.swing.MDialog;
import org.makagiga.commons.swing.MHighlighter;
import org.makagiga.commons.swing.MLabel;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MTextLabel;
import org.makagiga.commons.swing.MTimer;

final class SecurityDialog extends MDialog {

	// private

	private final MPanel detailsPanel;
	
	// package
	
	final MCheckBox doNotAskAgain;

	// protected

	@Override
	protected void onDetailsClick(final boolean visible) {
		setDetailsVisible(detailsPanel, visible);
		if (visible) {
			pack();
			detailsPanel.alignLabels();
		}
	}

	// package

	@SuppressWarnings("AssignmentToMethodParameter")
	SecurityDialog(
		final Armor armor,
		final String location,
		final ClassLoader _domainClassLoader,
		final java.security.Permission _permissionInstance,
		final String permissionName,
		final String permissionActions,
		String permissionDescription,
		PermissionInfo.ThreatLevel threatLevel
	) {
		super(
			UI.windowFor(null),
			_("Security Manager - {0}", MApplication.getFullName()),
			"ui/locked",
			STANDARD_DIALOG | NO_DEBUG | DETAILS_BUTTON | FORCE_STANDARD_BORDER
		);
		setAnimationEnabled(false);

		//Objects.requireNonNull(_domainClassLoader);
		Objects.requireNonNull(_permissionInstance);

		changeButton(getOKButton(), _("Allow"));
		changeButton(getCancelButton(), _("Deny"));

		doNotAskAgain = new MCheckBox(_("Do not ask again"));

		MPanel p = getMainPanel();
		p.setVBoxLayout();

		MLabel unsignedWarningLabel = new MLabel();
		unsignedWarningLabel.setBackground(MHighlighter.WARNING_COLOR);
		unsignedWarningLabel.setForeground(Color.BLACK);
		unsignedWarningLabel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color.BLACK),
			UI.createEmptyBorder(5)
		));
		unsignedWarningLabel.setOpaque(true);
		unsignedWarningLabel.setStyle("font-weight: bold");
		unsignedWarningLabel.setText(_("Unknown or unsigned Plugin - use at your own risk!"));
		unsignedWarningLabel.setVisible(false);
		p.add(unsignedWarningLabel);

		MIcon pluginIcon;
		String pluginDescription = null;
		String pluginName = null;
		PluginType pluginType = null;
		if (_domainClassLoader instanceof PluginClassLoader) {
			PluginClassLoader pcl = (PluginClassLoader)_domainClassLoader;
			if (!pcl.isVerified()) {
				unsignedWarningLabel.setVisible(true);
				p.addContentGap();
			}
			pluginIcon = pcl.getPluginIcon();
			pluginDescription = pcl.getPluginDescription();
			pluginName = pcl.getPluginName();
			pluginType = pcl.getPluginType();
		}
		else {
			pluginIcon = MIcon.stock("ui/question");
			if (_permissionInstance instanceof PluginPermission) {
				PluginPermission pluginPermission = (PluginPermission)_permissionInstance;
				pluginName = pluginPermission.pluginName;

				if (PluginPermission.ADD_TO_SYSTEM_CLASS_PATH.equals(permissionActions)) {
					if (pluginPermission.verified.isTrue()) {
						unsignedWarningLabel.setBackground(MHighlighter.OK_COLOR);
						unsignedWarningLabel.setText(_("Verified Plugin"));
					}
					unsignedWarningLabel.setVisible(true);
					p.addContentGap();
				}
			}
		}

		MLabel text = new MLabel();
		text.setIconTextGap(MPanel.DEFAULT_CONTENT_MARGIN);
		text.setIcon(pluginIcon);
		String textString = null;

		// script plugin, no PluginClassLoader
		if (
			(pluginName == null) &&
			!(_domainClassLoader instanceof PluginClassLoader) &&
			armor.isFileInDir(location, armor.pluginsDir) &&
			location.endsWith(File.separator + "main.js")//!!! examine root
		) {
			File scriptFile = new File(location);
			final File scriptDir = scriptFile.getParentFile();
			if (scriptDir != null) {
				PluginInfo info = AccessController.doPrivileged(new PrivilegedAction<PluginInfo>() {
					@Override
					public PluginInfo run() {
						return PluginManager.getInstance().getByID(scriptDir.getName());
					}
				}, armor.acc);
				if (info != null) {
					pluginDescription = info.shortDescription.get();
					pluginName = info.toString();
					pluginType = info.type.get();
				}
			}
		}
		// tool bar script
		else if (armor.isFileInDir(location, armor.scriptsDir)) {
			int extIndex = location.lastIndexOf('.');
			if (extIndex != -1) {
				String scriptProperties = location.substring(0, extIndex) + ".properties";
				Config scriptConfig = new Config(scriptProperties);
				pluginDescription = scriptConfig.read("description", null);
				pluginName = scriptConfig.read("name", null);
			}
			textString = _(
				"{0} ({1}) script requested access to the following element:",
				"<b>" + TK.escapeXML(Objects.toString(pluginName, _("Unknown"))) + "</b>",
				TK.escapeXML(Objects.toString(pluginDescription, _("no description")))
			);
		}

		if (TK.isEmpty(pluginName))
			pluginName = _("Unknown");
		if (TK.isEmpty(pluginDescription))
			pluginDescription = _("no description");
		if (pluginType != null)
			pluginName += " / " + pluginType;
		if (textString == null) {
			textString = _(
				"{0} ({1}) plugin<br>requested access to the following element:",
				"<b>" + TK.escapeXML(pluginName) + "</b>",
				TK.escapeXML(pluginDescription)
			);
		}
		text.setHTML(textString);

		p.add(text);

		MIcon permissionIcon = null;
		if (_permissionInstance instanceof Args.Permission) {
			permissionIcon = MIcon.stock("ui/console");
		}
		else if (_permissionInstance instanceof java.awt.AWTPermission) {
			if ("accessClipboard".equals(permissionName)) {
				permissionDescription = _("Clipboard");
				permissionIcon = MIcon.stock("ui/cut");
				threatLevel = PermissionInfo.ThreatLevel.MEDIUM;
			}
			else {
				permissionDescription = _("User Interface");
				permissionIcon = MIcon.stock("labels/emotion/happy");
				threatLevel = PermissionInfo.ThreatLevel.HIGH;
			}
		}
		else if (_permissionInstance instanceof MasterKey.Permission) {
			permissionIcon = MIcon.stock("ui/password");
		}
		else if (_permissionInstance instanceof PluginPermission) {
			if (PluginPermission.ADD_TO_SYSTEM_CLASS_PATH.equals(permissionActions))
				permissionIcon = MIcon.stock("ui/warning");
		}
		else if (_permissionInstance instanceof java.io.FilePermission) {
			permissionDescription = _("File System");
			permissionIcon = MIcon.stock("ui/folder");
			threatLevel = PermissionInfo.ThreatLevel.HIGH;
		}
		else if (
			(_permissionInstance instanceof java.lang.reflect.ReflectPermission) ||
			(_permissionInstance instanceof java.util.PropertyPermission) ||
			(_permissionInstance instanceof RuntimePermission)
		) {
			permissionDescription = _("System");
			permissionIcon = MIcon.stock("ui/console");
			threatLevel = PermissionInfo.ThreatLevel.HIGH;
		}
		else if (_permissionInstance instanceof java.net.SocketPermission) {
			permissionDescription = _("Internet/Network");
			permissionIcon = MIcon.stock("ui/internet");
			threatLevel = PermissionInfo.ThreatLevel.MEDIUM;
			//!!!MPermission.getIconName()
		}
		else {
			permissionIcon = null;
		}

		if (TK.isEmpty(permissionDescription))
			permissionDescription = TK.identifierToDisplayString(_permissionInstance.getClass().getName());

		if (threatLevel == null)
			threatLevel = PermissionInfo.ThreatLevel.UNKNOWN;

		MLabel permissionLabel = new MLabel();
		permissionLabel.setIconTextGap(MPanel.DEFAULT_CONTENT_MARGIN);
		permissionLabel.setIcon(permissionIcon);
		permissionLabel.setText(permissionDescription);
		p.addContentGap();
		p.add(permissionLabel);

		// threat level

		MLabel l = new MLabel();
		l.setIcon(MIcon.small(threatLevel.getIconName()));
		l.setText(_("Threat Level: {0}", threatLevel));
		getButtonsPanel().addContentGap();
		getButtonsPanel().add(l);

		// details

		detailsPanel = MPanel.createVBoxPanel();
		detailsPanel.setVisible(false);
		detailsPanel.addGap();
		detailsPanel.addSeparator(_("Details"));//!!!thread, stack

		String permissionNameDisplay;
		if (TK.isEmpty(permissionName)) {
			permissionNameDisplay = "?";
		}
		else {
			if (
				(_permissionInstance instanceof RuntimePermission) ||
				(_permissionInstance instanceof java.awt.AWTPermission) ||
				(_permissionInstance instanceof java.lang.reflect.ReflectPermission)
			)
				permissionNameDisplay = TK.identifierToDisplayString(permissionName) + " (" + permissionName + ")";
			else
				permissionNameDisplay = permissionName;
		}
		MTextLabel permissionNameLabel = new MTextLabel(permissionNameDisplay);
		detailsPanel.add(MPanel.createHLabelPanel(permissionNameLabel, _("Name:")));//!!!

		detailsPanel.addGap();

		MTextLabel permissionActionsLabel = new MTextLabel(Objects.toString(permissionActions, "?"));
		detailsPanel.add(MPanel.createHLabelPanel(permissionActionsLabel, _("Actions:")));

		detailsPanel.addGap();

		detailsPanel.add(MPanel.createHLabelPanel(new MTextLabel(Objects.toString(location, "?")), _("Location:")));

		detailsPanel.addGap();

		MTextLabel permissionClassLabel = new MTextLabel(_permissionInstance.getClass().getName());
		detailsPanel.add(MPanel.createHLabelPanel(permissionClassLabel, _("Type:")));

		p.add(detailsPanel);

		MPanel doNotAskAgainPanel = MPanel.createHBoxPanel();
		doNotAskAgainPanel.setContentMargin();
		doNotAskAgainPanel.addStretch();
		doNotAskAgainPanel.add(doNotAskAgain);
		p.add(doNotAskAgainPanel);

		// disable "Allow" button for 0,5 sec. to avoid accidental click
		getOKButton().setEnabled(false);
		new MTimer(500) {
			@Override
			protected boolean onTimeout() {
				SecurityDialog.this.getOKButton().setEnabled(true);

				return MTimer.STOP;
			}
		}.start();

		packFixed();

		if (threatLevel != PermissionInfo.ThreatLevel.LOW)
			getDetailsButton().doClick(0);
	}

}
