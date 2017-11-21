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

package org.makagiga.plugins;

import static org.makagiga.commons.UI._;

import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Unpacker;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;

import org.makagiga.commons.Config;
import org.makagiga.commons.FS;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.MProperties;
import org.makagiga.commons.TK;
import org.makagiga.commons.annotation.Uninstantiable;
import org.makagiga.commons.io.MZip;
import org.makagiga.commons.swing.MFileChooser;
import org.makagiga.commons.swing.MMessage;

public final class PluginInstaller {
	
	// public

	/**
	 * @since 3.8.12
	 */
	public static final int UPDATE = 1;

	// public
	
	/**
	 * @since 2.0
	 */
	public static File getTargetFile(final File sourceFile, final String packSuffix) {
		String source = sourceFile.getPath();
		
		return new File(source.substring(0, source.length() - packSuffix.length()) + ".jar");
	}

	/**
	 * @since 3.8
	 */
	public static void install(final Window owner, final File fileToInstall) throws PluginException {
		install(owner, fileToInstall, 0);
	}
	
	/**
	 * @since 2.4
	 */
	public static void install(final Window parent, final File fileToInstall, final int flags) throws PluginException {
		MProperties p = getPluginProperties(fileToInstall);
		
		MLogger.info("plugin", "Installing \"%s\"...", fileToInstall);
		
		File targetDir = FS.makeConfigFile("plugins", FS.CREATE_DIR);
		try (MZip zip = MZip.read(fileToInstall)) {
			// unzip plugin package
			zip.unpackTo(targetDir, MZip.UNPACK_VALIDATE_ENTRY);

			if (p != null) {
				Config config = Config.getDefault();

				String id = p.getProperty("String.id");
				String type = p.getProperty("String.type");

				// auto set installed look and feel as default
				if (
					((flags & UPDATE) == 0) &&
					PluginType.LOOK_AND_FEEL.getType().equals(type)
				) {
					String lafClassName = p.getProperty("String.x.lafClassName");
					if (!TK.isEmpty(lafClassName)) {
						MLogger.info("plugin", "Setting default Look And Feel: %s", lafClassName);
						LookAndFeelPlugin.writeLAFConfig(config, lafClassName, true);
					}
				}
				
				// plugin reinstalled, do not remove on next application startup
				config.removeBoolean("Plugin.remove." + id);

				// auto enable existing plugin (if disabled)
				config.removeBoolean("Plugin.enabled." + id);

				config.sync();
			}
		}
		catch (Exception exception) {
			throw new PluginException(_("Installation failed"), exception);
		}
	}

	/**
	 * @since 2.0
	 */
	public static boolean showInstallDialog(final Window parent) {
		MFileChooser fileChooser = MFileChooser.createFileChooser(parent, _("Select a Makagiga Plugin you want to install"));
		fileChooser.setFileFilter(
			fileChooser.addFilter(_("Makagiga Plugin"), "mgplugin")
		);
		fileChooser.setApproveText(_("Install"));
		fileChooser.setConfigKey("plugin");
		if (fileChooser.openDialog()) {
			try {
				install(parent, fileChooser.getSelectedFile());

				return true;
			}
			catch (PluginException exception) {
				MMessage.error(parent, exception);
			}
		}
		
		return false;
	}

	/**
	 * Unpacks jar file packed with pack200.
	 * 
	 * @param sourceFile The source file
	 * @param targetFile The destination file
	 * @param gzipped Wheter or not the source file is gzipped
	 * 
	 * @throws IOException If error
	 * 
	 * @since 2.0
	 */
	public static void unpackJar(final File sourceFile, final File targetFile, final boolean gzipped) throws IOException {
		MLogger.info("plugin", "Unpacking \"%s\"...", sourceFile.getPath());
		InputStream input = null;
		JarOutputStream output = null;
		File tmpTargetFile = new File(targetFile.getPath() + ".tmp");
		try {
			input = new FS.BufferedFileInput(sourceFile);
			if (gzipped)
				input = new GZIPInputStream(input);
			// .tmp - do not overwrite existing .jar
			output = new JarOutputStream(new FS.BufferedFileOutput(tmpTargetFile));

			Unpacker unpacker = Pack200.newUnpacker();
			unpacker.unpack(input, output);

			// NOTE: close streams before file delete
			FS.close(input);
			FS.close(output);

			targetFile.delete(); // remove old Jar
			tmpTargetFile.renameTo(targetFile); // rename new Jar to replace old one
			
			// delete packed file after stream close
			if (!sourceFile.equals(targetFile))
				sourceFile.delete();
		}
		catch (IOException exception) {
			// clean up
			tmpTargetFile.delete();
			
			throw exception;
		}
		finally {
			FS.close(input);
			FS.close(output);
		}
	}

	/**
	 * Unpacks jar files packed with pack200.
	 * 
	 * @param dir The source directory containing packed jars
	 * 
	 * @throws IOException If error
	 * 
	 * @since 4.0
	 */
	public static void unpackJars(final File dir) throws IOException {
		String suffix = ".jar.pack.gz";
		for (File i : FS.listFiles(dir, FS.FILE_FILTER)) {
			if (i.getPath().endsWith(suffix))
				unpackJar(i, getTargetFile(i, suffix), true);
		}
	}
	
	// private
	
	@Uninstantiable
	private PluginInstaller() { }

	private static MProperties getPluginProperties(final File fileToInstall) {
		try (MZip zip = MZip.read(fileToInstall)) {
			for (ZipEntry i : zip) {
				if (!i.isDirectory() && i.getName().endsWith("}/plugin.properties")) {
					MProperties p = new MProperties();
					p.loadUTF8(zip.getInputStream());

					return p;
				}
			}
		}
		catch (Exception exception) {
			MLogger.exception(exception);
		}

		return null;
	}

}
