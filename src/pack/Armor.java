// Copyright 2007 Konrad Twardowski
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

import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.makagiga.commons.BooleanProperty;
import org.makagiga.commons.CollectionMap;
import org.makagiga.commons.FS;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.MObject;
import org.makagiga.commons.OS;
import org.makagiga.commons.TK;
import org.makagiga.commons.Tuple;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.cache.FileCache;
import org.makagiga.commons.crypto.MasterKey;
import org.makagiga.commons.io.Checksum;
import org.makagiga.commons.security.PermissionInfo;
import org.makagiga.commons.xml.SimpleXMLReader;
import org.makagiga.commons.xml.XMLBuilder;

/**
 * A security system.
 *
 * @since 3.0, 4.0 (org.makagiga.plugins package)
 */
public final class Armor extends java.security.Policy {

	// private

	private enum Access { ALLOW, DENY, DENY_ALWAYS };
	private static AccessMap savedAccess;//!!!non-static
	private final AccessMap temporaryAccess = new AccessMap(null);
	private boolean debug;
	private volatile boolean dialogVisible;
	private static boolean initialized;
	private final ClassLoader systemClassLoader;
	private final File securityXMLFile;
	private static int messageRepeats;
	private static Level lastLevel;
	private static Map<File, String> checksumCache = new ConcurrentHashMap<>();
	private static final MLogger LOG = new MLogger("armor");
	private static final Object logLock = new Object();
	private final Semaphore dialogLock = new Semaphore(1, true);//!!!reentr lock
	private final Set<String> awtPermissionAllow;
	private final Set<String> extJars;
	private final Set<String> propertyPermissionAllowRead;
	private final Set<String> runtimePermissionAllow;
	private static String baseLocation;
	private final String cacheDir;
	private static String lastMessage;
	private final String tmpDir;
	private final String throbberGifFile;
	private final String tridentPluginPropertiesFile;
	private final String vfsDataDir;
	private final String widgetsDir;
	
	// package

	final AccessControlContext acc;
	final String pluginsDir;
	final String scriptsDir;

	// public

	@Override
	public boolean implies(final java.security.ProtectionDomain domain, final java.security.Permission permission) {
		Access access = doImplies(domain, permission);
		switch (access) {
			case ALLOW:
				return true;
			case DENY:
				return askUser(domain, permission);
			case DENY_ALWAYS:
				return false;
			default:
				throw new WTFError(access);
		}
	}

	public synchronized static void init() {
		if (initialized)
			return;
		
		initialized = true;
		
		try {
			log(Level.INFO, "Initializing...");
			
			java.security.CodeSource cs = Armor.class.getProtectionDomain().getCodeSource();
			baseLocation = toAbsolutePath(cs);
			log(Level.INFO, "Base location: %s", baseLocation);

			java.security.Policy.setPolicy(new Armor());
			System.setSecurityManager(new SecurityManager());
		}
		catch (IOException | SecurityException exception) {
			exception.printStackTrace();
		}
		
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				TK.sleep(MLogger.isDeveloper() ? 10000 : 60000); // lazy start...
			
				Thread currentThread = Thread.currentThread();
				
				log(Level.INFO, "Starting %s...", currentThread.getName());
				
				ThreadMXBean mx = ManagementFactory.getThreadMXBean();
				while (true) {
					int threadCount = Thread.activeCount();
					Thread[] threads = new Thread[threadCount];
					threadCount = Thread.enumerate(threads);

					// test all threads
					for (int i = 0; i < threadCount; i++) {
						Thread thread = threads[i];
						
						// skip self
						if (thread == currentThread)
							continue; // for
						
						String threadName = thread.getName();
						
						if (
							(threadName == null) ||
							!threadName.startsWith("AWT-EventQueue-") ||
							(thread.getState() != Thread.State.BLOCKED)
						)
							continue; // for
						
						// interrupt a thread that blocks EDT (user interface)

						ThreadInfo info = mx.getThreadInfo(thread.getId());

						if (info == null)
							break; // for

						long lockOwnerId = info.getLockOwnerId();
						
						if (lockOwnerId == -1)
							break; // for
						
						// give it a chance...
						TK.sleep(5000);
						
						if (thread.getState() != Thread.State.BLOCKED)
							break; // for
						
						ThreadInfo killInfo = mx.getThreadInfo(lockOwnerId);
						for (int k = 0; k < threadCount; k++) {
							Thread kill = threads[k];
							if ((k != i) && (killInfo.getThreadId() == kill.getId())) {
								// print diagnostic info
// TODO: show GUI message
								log(Level.SEVERE, "Deadlock detected. Thread \"%s\" blocked User Interface.", kill);

								ThreadInfo[] dumpArray = mx.dumpAllThreads(true, true);
								for (ThreadInfo dump : dumpArray)
									log(Level.INFO, dump.toString());

								// It's safe to kill a "random" thread because
								// the application is already in unusable state ;)
								kill.interrupt();
							}
						}
					}

					TK.sleep(5000);
				}
			}
		}, "Deadlock Detector");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}
	
	/**
	 * @since 4.4
	 */
	public synchronized static boolean isInitialized() { return initialized; }

	// private

	private Armor() throws IOException {
		acc = AccessController.getContext();
		systemClassLoader = ClassLoader.getSystemClassLoader();
		//log(Level.INFO, "System Class Loader: " + systemClassLoader);
		debug = MLogger.isDeveloper();
	
		extJars = new HashSet<>();
		String javaExtDirs = System.getProperty("java.ext.dirs");
		for (String extDir : TK.fastSplit(javaExtDirs, File.pathSeparatorChar)) {
			log(Level.INFO, "Found extension directory: %s", extDir);

			Path extPath;
			try {
				extPath = Paths.get(extDir);
			}
			catch (InvalidPathException exception) {
				MLogger.exception(exception);
				
				continue; // for
			}

			if (Files.isDirectory(extPath)) {
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(extPath)) {
					for (Path i : ds) {
						if (!Files.isRegularFile(i))
							continue; // for

						String suffix = FS.getFileExtension(i);

						if (!"jar".equals(suffix))
							continue; // for

						String path1 = i.toFile().toString();
						log(Level.INFO, "Add extension: %s", path1);
						extJars.add(path1);

						try {
							String path2 = i.toRealPath().toFile().toString(); // resolve links
							if (!path1.equals(path2)) {
								log(Level.INFO, "Add extension (link target): %s", path2);
								extJars.add(path2);
							}
						}
						catch (IOException exception) {
							MLogger.exception(exception);
						}
					}
				}
			}
		}

		File file = FS.makeConfigFile("security.xml");
		try {
			file = file.getCanonicalFile(); // resolve links
		}
		catch (IOException exception) {
			MLogger.exception(exception);
		}
		securityXMLFile = file;
		savedAccess = new AccessMap(securityXMLFile);
		savedAccess.load();

		awtPermissionAllow = TK.newHashSet(
			"accessClipboard",//!!!
			"setWindowAlwaysOnTop",
			"showWindowWithoutWarningBanner",
			"watchMousePointer"//!!!
		);
		
		if ((OS.getJavaVersion() == 1.7f) && (OS.getJavaUpdate() < 6)) {
			log(Level.WARNING, "Unlocking Access to the Event Queue");
			awtPermissionAllow.add("accessEventQueue");
		}

		propertyPermissionAllowRead = TK.newHashSet(
			// safe
			"file.encoding",
			"file.separator",
			"java.io.tmpdir",
			"java.class.version",
			"java.library.path",//!!!?
			"java.runtime.version",
			"java.specification.name",
			"java.specification.vendor",
			"java.specification.version",
			"java.vendor",
			"java.vendor.url",
			"java.version",
			"java.vm.name",
			"java.vm.specification.name",
			"java.vm.specification.vendor",
			"java.vm.specification.version",
			"java.vm.vendor",
			"java.vm.version",
			"javawebstart.version",
			"line.separator",
			"os.arch",
			"os.name",
			"os.version",
			"path.separator",
			"user.timezone",
			// safe 2
			"com.ibm.vm.bitmode",
			"sun.arch.data.model",
			"sun.awt.disableMixing",
			"swt.library.path",
			// countdown plugin/joda-time.jar
			"org.joda.time.DateTimeZone.NameProvider",
			"org.joda.time.DateTimeZone.Provider",
			"org.joda.time.tz.CachedDateTimeZone.size",
			// pdfviewer plugin/PDFRenderer.jar
			"PDFRenderer.avoidColorConvertOp",
			"PDFRenderer.avoidExternalTtf",
			"PDFRenderer.fontSearchPath",
			// com.sun.org.apache.xerces.internal.jaxp
			"elementAttributeLimit",
			"entityExpansionLimit",
			"maxOccurLimit",
			// JAXB & Friends
			"{http://xml.apache.org/xalan}content-handler",
			"{http://xml.apache.org/xalan}entities",
			"{http://xml.apache.org/xalan}indent-amount",
			"com.sun.xml.internal.bind.v2.bytecode.ClassTailor.noOptimize",
			"com.sun.xml.internal.bind.v2.runtime.Coordinator.debugTableNPE",
			"com.sun.xml.internal.bind.v2.runtime.JAXBContextImpl.fastBoot",
			"dtm.debug",
			"encoding",
			"indent",
			"mapAnyUriToUri",
			"media-type",
			"method",
			"omit-xml-declaration",
			"standalone",
			"version",
			// Sea Glass LAF
			"JComponent.sizeVariant"
		);

		runtimePermissionAllow = TK.newHashSet(//!!!
			"accessClassInPackage.sun.beans.infos",
			// JAXB
			"accessClassInPackage.com.sun.xml.internal.bind.v2",
			"accessClassInPackage.com.sun.xml.internal.bind.v2.runtime.reflect",
			"accessClassInPackage.com.sun.xml.internal.bind.v2.runtime.unmarshaller",
			// Scripting
			"accessClassInPackage.sun.org.mozilla.javascript.internal",
			// java.util.logging.Level.getLocalizedName
			"accessClassInPackage.sun.util.logging.resources",
			// Insubstantial/Substance plugin
			"getenv.KDE_FULL_SESSION",
			"getenv.KDE_SESSION_VERSION"
		);

		cacheDir = FileCache.getInstance().getDirectory().toString();
		pluginsDir = FS.makeConfigPath("plugins");
		scriptsDir = FS.makeConfigPath("scripts");
		
		String s = System.getProperty("java.io.tmpdir");
		if (s.endsWith("/") || s.endsWith("\\"))
			tmpDir = s.substring(0, s.length() - 1); // Remove trailing separator; Windows only?
		else
			tmpDir = s;
		
		vfsDataDir = FS.makePath(FS.makeConfigPath("vfs"), "data");
		widgetsDir = FS.makeConfigPath("desktop");
		
		throbberGifFile = File.separator + "images" + File.separator + "ui" + File.separator + "throbber.gif";
		tridentPluginPropertiesFile = File.separator + "META-INF" + File.separator + "trident-plugin.properties";
	}

	private boolean askUser(final java.security.ProtectionDomain _domain, final java.security.Permission _permission) {
		java.security.CodeSource codeSource = _domain.getCodeSource();
		String location = toAbsolutePath(codeSource);

		if (location == null) {
			log(Level.WARNING, "\"null\" location for \"%s\" %s", _domain.getCodeSource(), _permission);
			
			return false;
		}

		final PermissionKey permissionKey = new PermissionKey(location, _permission);

		Access access = checkUserAccess(_domain, _permission, permissionKey);

		if (access == Access.ALLOW)
			return true;

		if (access == Access.DENY)
			return false;

		log(
			Level.INFO,
			"ASK USER:\n" +
			"\tlocation=\"%s\"\n" +
			"\tpermission=\"%s\"\n" +
			"\tdomain class loader=\"%s\"\n" +
			"\tcurrent thread=\"%s\"",
			location,
			_permission,
			_domain.getClassLoader(),
			Thread.currentThread()
		);
		MLogger.trace();//!!!
		
		final SecondaryLoop loop = AccessController.doPrivileged(new PrivilegedAction<SecondaryLoop>() {
			@Override
			public SecondaryLoop run() {
				EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
				
				return eventQueue.createSecondaryLoop();
			}
		}, acc);
		final boolean edt = SwingUtilities.isEventDispatchThread();
		
		try {
			if (!edt && dialogVisible) {
				log(Level.INFO, "Sleep Thread");
				while (dialogVisible) {
					TK.sleep(10);
				}

				// check if the access changed during wait

				TK.sleep(2000); // wait for other thread; update access info (fix !!!)
				access = checkUserAccess(_domain, _permission, permissionKey);

				if (access == Access.ALLOW)
					return true;

				if (access == Access.DENY)
					return false;
			}
		
			if (!edt) {
				dialogLock.acquireUninterruptibly();

				access = checkUserAccess(_domain, _permission, permissionKey);

				if (access == Access.ALLOW)
					return true;

				if (access == Access.DENY)
					return false;
			}

			log(Level.INFO, "Show Security Dialog");
			dialogVisible = true;

			final BooleanProperty result = new BooleanProperty();
			Runnable dialogRunnable = new Runnable() {
				@Override
				public void run() {
					try {
						/*
						UI.invokeLater(new Runnable() {
							@Override
							public void run() {
								try {
									result.set(askUserPrivileged(_domain, _permission, permissionKey));
								}
								catch (Exception exception) {
									exception.printStackTrace();
								}
								finally {
									dialogVisible = false;
									log(Level.INFO, "Loop: Exit");
									loop.exit();
								}
							}
						} );
						*/
						
						result.set(UI.invokeAndWait(new Callable<Boolean>() {
							@Override
							public Boolean call() throws Exception {
								try {
									return askUserPrivileged(_domain, _permission, permissionKey);
								}
								finally {
									dialogVisible = false;
								}
							}
						} ));
					}
					catch (Exception exception) {
						exception.printStackTrace();
						dialogVisible = false;
					}
					finally {
						log(Level.INFO, "Loop: Exit");
						loop.exit();
					}
				}
			};

			new Thread(dialogRunnable, "Security Manager Dialog").start();
			log(Level.INFO, "Loop: Enter");
			loop.enter();

			return result.get();
		}
		catch (Exception exception) {
			exception.printStackTrace();
			
			return false;
		}
		finally {
			if (!edt)
				dialogLock.release();
		}
	}
	
	private boolean askUserPrivileged(final java.security.ProtectionDomain _domain, final java.security.Permission _permission, final PermissionKey permissionKey) throws Exception {
		// NOTE: initialize variables outside the "doPrivileged" block
		final ClassLoader _domainClassLoader = _domain.getClassLoader();
		final PermissionInfo.ThreatLevel threatLevel =
			(_permission instanceof PermissionInfo)
			? PermissionInfo.class.cast(_permission).getThreatLevel()
			: PermissionInfo.ThreatLevel.UNKNOWN;
		final String permissionActions = _permission.getActions();
		final String permissionDescription =
			(_permission instanceof PermissionInfo)
			? PermissionInfo.class.cast(_permission).getPermissionDescription()
			: null;
		final String permissionName = _permission.getName();

		return AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
			@Override
			public Boolean run() throws Exception {
				if (UI.isMetal())
					UIManager.put("swing.boldMetal", false);

				SecurityDialog dialog = new SecurityDialog(
					Armor.this,
					permissionKey.getLocation(),
					_domainClassLoader,
					_permission,
					permissionName,
					permissionActions,
					permissionDescription,
					threatLevel
				);
				// HACK: set temp. new RepaintManager to fix dialog repaint
				RepaintManager oldManager = RepaintManager.currentManager(dialog);
				oldManager.paintDirtyRegions();
				RepaintManager.setCurrentManager(new RepaintManager());
				boolean allow = dialog.exec();
				RepaintManager.setCurrentManager(oldManager);

				if (dialog.doNotAskAgain.isSelected()) {
					putKey(temporaryAccess, permissionKey, allow ? Access.ALLOW : Access.DENY);
					putKey(savedAccess, permissionKey, allow ? Access.ALLOW : Access.DENY);
					savedAccess.save();
				}
				else {
					if (allow)
						putKey(temporaryAccess, permissionKey, Access.ALLOW);
				}

				return allow;
			}
		}, acc);
	}

	private Access checkUserAccess(final java.security.ProtectionDomain _domain, final java.security.Permission _permission, final PermissionKey permissionKey) {
		Access access = savedAccess.get(permissionKey);
		if (access == Access.ALLOW) {
			logALLOW(_domain, _permission, "User/Saved");
		}
		else if (access == Access.DENY) {
			logDENY(_domain, _permission, "User/Saved");
		}
		else {
			access = temporaryAccess.get(permissionKey);
			if (access == Access.ALLOW) {
				logALLOW(_domain, _permission, "User/Temporary");
			}
			else {
				access = null; // show dialog
			}
		}
		
		return access;
	}
	
	private Access doImplies(final java.security.ProtectionDomain domain, final java.security.Permission permission) {
		final String NAME = permission.getName();
		
		// disable security manager - always deny

		if (
			(permission instanceof RuntimePermission) &&
			"setSecurityManager".equals(NAME)
		) {
			return Access.DENY_ALWAYS;
		}

		// add to system classpath - always ask

		if (
			(permission instanceof PluginPermission) &&
			PluginPermission.ADD_TO_SYSTEM_CLASS_PATH.equals(permission.getActions())
		) {
			return Access.DENY; // ask user
		}

		// system classpath - all permissions
		
		final ClassLoader domainClassLoader = domain.getClassLoader();

		//!!!test web start
		if ((domainClassLoader != null) && (domainClassLoader == systemClassLoader)) {//!!!test pluginclassloader
			//return logALLOW(domain, permission, "System Class Loader");!!!log?
			return Access.ALLOW;
		}

/*
		// base package - all permissions

		if (isFileInDir(location, baseLocation)) {
			return Access.ALLOW;
		}
*/

		final PluginClassLoader pluginClassLoader =
			(domainClassLoader instanceof PluginClassLoader)
			? (PluginClassLoader)domainClassLoader
			: null;
		
		// signed and verified plugin - all permissions

		if (pluginClassLoader != null) {

			// always ask

			if (
				(permission instanceof MasterKey.Permission) ||
				(permission instanceof java.io.SerializablePermission) ||
				(permission instanceof java.net.NetPermission) ||
				(permission instanceof java.security.AllPermission) ||
				(permission instanceof java.security.SecurityPermission) ||
				(permission instanceof javax.net.ssl.SSLPermission) ||
				(permission instanceof javax.security.auth.AuthPermission) ||
				(permission instanceof javax.security.auth.kerberos.DelegationPermission)
			) {
				return Access.DENY; // ask user
			}

/* TODO: accessClassInPackage.
			if (permission instanceof RuntimePermission) {
				if (
					(NAME != null) &&
					(
						NAME.startsWith("accessClassInPackage.org.makagiga.commons.crypto") ||
						NAME.startsWith("accessClassInPackage.org.makagiga.commons.script") ||
						NAME.startsWith("accessClassInPackage.org.makagiga.commons.security") ||
						NAME.startsWith("accessClassInPackage.makagiga") ||
						NAME.startsWith("accessClassInPackage.org.simplericity")
					)
				) {
					return Access.DENY; // ask user
				}
			}
*/

			if (pluginClassLoader.isVerified()) {
				//if (debug)
				//	return logALLOW(domain, permission, "Plugin verified using Public Key");
				
				return Access.ALLOW;
			}
		}

		// java.awt.AWTPermission

		if (permission instanceof java.awt.AWTPermission) {
			if (awtPermissionAllow.contains(NAME)) {
				return logALLOW(domain, permission, "AWT White List");
			}
		}

	else

		// java.io.FilePermission

		if (permission instanceof java.io.FilePermission) {
			final String ACTIONS = permission.getActions();
			
			if (ACTIONS == null)
				return Access.DENY; // ask user
			
			switch (ACTIONS) {
			case "delete": {
				if (!TK.isEmpty(tmpDir) && isFileInDir(NAME, tmpDir)) {
					return logALLOW(domain, permission, "Temporary Directory");
				}

				if (pluginClassLoader != null) {
					if (isPluginCacheDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Plugin Cache Directory");
					}

					if (isPluginConfigDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Plugin Config Directory");
					}

					if (isWidgetConfigDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Widget Config Directory");
					}
				}
			} break;
			case "read": {
				if (!TK.isEmpty(tmpDir) && isFileInDir(NAME, tmpDir)) {
					return logALLOW(domain, permission, "Temporary Directory");
				}

				// plugin

				if (pluginClassLoader != null) {
					String pluginInstallDir = FS.makePath(pluginsDir, pluginClassLoader.getID());

					if (isFileInDir(NAME, pluginInstallDir)) {
						if (debug)
							return logALLOW(domain, permission, "Plugin Install Directory");
						
						return Access.ALLOW;
					}

					if (isPluginCacheDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Plugin Cache Directory");
					}

					if (isPluginConfigDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Plugin Config Directory");
					}
					
					if (isWidgetConfigDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Widget Config Directory");
					}

					//!!! tmp preview gen.
					if (isFileInDir(NAME, vfsDataDir)) {
						return logALLOW(domain, permission, "Preview Generator");
					}
				}

				// misc. library files

				if (NAME.endsWith(throbberGifFile)) {
					return logALLOW(domain, permission, "Throbber Image");
				}

				if (NAME.endsWith(tridentPluginPropertiesFile)) {
					return logALLOW(domain, permission, "Trident Properties");
				}
			} break;
			case "write": {
				if (!TK.isEmpty(tmpDir) && isFileInDir(NAME, tmpDir)) {
					return logALLOW(domain, permission, "Temporary Directory");
				}

				if (pluginClassLoader != null) {
					if (isPluginCacheDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Plugin Cache Directory");
					}

					if (isPluginConfigDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Plugin Config Directory");
					}
					
					if (isWidgetConfigDir(pluginClassLoader, NAME)) {
						return logALLOW(domain, permission, "Widget Config Directory");
					}
				}
			} break;
			case "read,write,delete": {
				if (!TK.isEmpty(tmpDir) && isFileInDir(NAME, tmpDir)) {
					return logALLOW(domain, permission, "Temporary Directory");
				}
			} break;
			} // switch

			// no access to the "security.xml" file for plugins
			if (
				(pluginClassLoader != null) &&
				(NAME != null) &&
				NAME.endsWith(File.separator + "security.xml")
			) {
				File canonicalFile = AccessController.doPrivileged(new PrivilegedAction<File>() {
					@Override
					public File run() {
						File file = new File(NAME);
						try {
							return file.getCanonicalFile();
						}
						catch (IOException exception) {
							exception.printStackTrace();
							
							return file;
						}
					}
				}, acc);

				if (canonicalFile.equals(securityXMLFile))
					return logDENY_ALWAYS(domain, permission, "Security File");
			}
		}

	else
	
		if (permission instanceof PluginPermission) {
			if (pluginClassLoader != null) {
				if ((NAME != null) && NAME.equals("localSettings." + pluginClassLoader.getID())) {
					return logALLOW(domain, permission, "Plugin Local Settings");
				}
			}
		}

	else

		// java.util.PropertyPermission

		if (permission instanceof java.util.PropertyPermission) {
			switch (permission.getActions()) {
				case "read":
					if (propertyPermissionAllowRead.contains(NAME)) {
						return logALLOW(domain, permission, "Property White List");
					}
					
					if ((pluginClassLoader != null) && (NAME != null)) {
						if (NAME.startsWith("h2.") && "{f94996b0-d5aa-48dd-914b-73532ddda0e5}".equals(pluginClassLoader.getID())) {
							return logALLOW(domain, permission, "Database Plugin/H2 Database");
						}
					}

					if (NAME != null) {
						if (NAME.startsWith("nativeswing.")/* && "{4cdd28f0-a52d-4b36-b818-154503b54cd2}".equals(pluginClassLoader.getID())*/) {
							return logALLOW(domain, permission, "Native Swing");
						}
						
						if (NAME.startsWith("SeaGlass.")) {
							return logALLOW(domain, permission, "Sea Glass");
						}
						
						if (NAME.startsWith("Substance.")) {
							return logALLOW(domain, permission, "Insubstantial/Substance");
						}
					}
					break;
			}
		}

	else

		// RuntimePermission

		if (permission instanceof RuntimePermission) {
			if (runtimePermissionAllow.contains(NAME)) {
				return logALLOW(domain, permission, "Runtime White List");
			}

			if (
				"modifyThreadGroup".equals(NAME) &&
				isCallFromMethod(domain, "sun.awt.image.ImageFetcher.createFetchers")//!!!
			) {
				return logALLOW(domain, permission, "Runtime White List: sun.awt.image.ImageFetcher.createFetchers");
			}
		}

		// JRE extensions - all permissions

		java.security.CodeSource codeSource = domain.getCodeSource();
		String location = toAbsolutePath(codeSource);

		if (location == null) {
			log(Level.WARNING, "\"null\" location for \"%s\" %s", domain.getCodeSource(), permission);
			
			return Access.DENY; // ask user
		}

		if (extJars.contains(location)) {
			return logALLOW(domain, permission, "Java Extension Jar");
		}

		return Access.DENY; // ask user
	}

	private String formatCodeSource(final java.security.CodeSource cs) {
		if (cs == null)
			return "<NULL CODE SOURCE>";

		URL location = cs.getLocation();

		if (location == null)
			return "<NULL CODE LOCATION>";

		return location.toString();
	}

	private boolean isCallFromMethod(final java.security.ProtectionDomain domain, final String methodName) {
		Thread current = Thread.currentThread();
		for (StackTraceElement i : current.getStackTrace()) {
			if ((i.getClassName() + "." + i.getMethodName()).equals(methodName))
				return true;
		}
		
		return false;
	}

	boolean isFileInDir(final String file, final String dir) { // package
		String f = (file + File.separator);
		if (OS.isWindows() && (f.startsWith("/") || f.startsWith("\\")))
			f = f.substring(1);
	
		return f.startsWith(dir + File.separator);
	}

	private boolean isPluginCacheDir(final PluginClassLoader pluginClassLoader, final String name) {
		String pluginCacheDir = FS.makePath(cacheDir, pluginClassLoader.getID());

		// plugin can read, write or delete from its own cache directory
		return isFileInDir(name, pluginCacheDir);
	}

	private boolean isPluginConfigDir(final PluginClassLoader pluginClassLoader, final String name) {
		String pluginConfigDir = FS.makeConfigPath(pluginClassLoader.getID());

		// plugin can read, write or delete from its own config directory
		return isFileInDir(name, pluginConfigDir);
	}

	private boolean isWidgetConfigDir(final PluginClassLoader pluginClassLoader, final String name) {
		// widget plugin can read and write to its own config directory
		String widgetConfigDir = FS.makePath(widgetsDir, pluginClassLoader.getID() + "-");

		return name.startsWith(widgetConfigDir);
	}

	@SuppressWarnings({ "PMD.SystemPrintln", "UseOfSystemOutOrSystemErr" })
	private static void log(final Level level, final String text, final Object... args) {
		String message = String.format(text, args);
		synchronized (logLock) {
			if ((level == lastLevel) && message.equals(lastMessage)) {
				messageRepeats++;
				
				return;
			}
			else {
				if (messageRepeats > 0) {
					LOG.infoFormat("Last message repeated %d time(s)", messageRepeats);
					messageRepeats = 0;
				}
				lastLevel = level;
				lastMessage = message;
			}
		}
		
		if (Level.SEVERE == level)
			LOG.errorFormat(text, args);
		else if (Level.WARNING == level)
			LOG.warningFormat(text, args);
		else
			LOG.infoFormat(text, args);
	}

	private Access logALLOW(final java.security.ProtectionDomain domain, final java.security.Permission permission, final String text) {
		log(Level.INFO, "ALLOW [%s]: %s %s", text, formatCodeSource(domain.getCodeSource()), permission);

		return Access.ALLOW;
	}

	private Access logDENY(final java.security.ProtectionDomain domain, final java.security.Permission permission, final String text) {
		log(Level.WARNING, "DENY [%s]: %s %s", text, formatCodeSource(domain.getCodeSource()), permission);

		return Access.DENY;
	}

	private Access logDENY_ALWAYS(final java.security.ProtectionDomain domain, final java.security.Permission permission, final String text) {
		log(Level.WARNING, "DENY ALWAYS [%s]: %s %s", text, formatCodeSource(domain.getCodeSource()), permission);

		return Access.DENY_ALWAYS;
	}

	private void putKey(final AccessMap map, final PermissionKey key, final Access access) {
		map.put(key, access);
		switch (key.getPermissionClassName()) {
			case "java.net.SocketPermission": {
				final String ACTIONS = key.getActions();
				final String NAME = key.getName();
				switch (ACTIONS) {
					// Coalescence simillar java.net.SocketPermission
					// into one security question to avoid SecurityDialog flood...
					case "connect,resolve": {
						PermissionKey resolveAction = key.withActions("resolve");
						map.put(resolveAction, access);
						
						int portIndex = NAME.lastIndexOf(':');//!!!ipv6
						String host;
						String port;
						if (portIndex != -1) {
							host = NAME.substring(0, portIndex);
							port = NAME.substring(portIndex + 1);
						}
						else {
							host = NAME;
							port = "";
						}
						
						if (host.isEmpty())
							return;
						
						// host w/o port
						if (!port.isEmpty()) {
							map.put(key.withName(host).withActions("resolve"), access);//!!!opt
						}
								
						//!!!opt
						
						// resolve hostname
						InetAddress address = null;
						try {
							address = InetAddress.getByName(host);
						}
						catch (Exception exception) {
							exception.printStackTrace();
						}
						if (address != null) {
							String ip = address.getHostAddress();
							
							if (!port.isEmpty())
								map.put(key.withName(ip + ":" + port), access);

							map.put(key.withName(ip).withActions("resolve"), access);
						}
					} break;
				}
			} break;
		}
	}

	private static String toAbsolutePath(final java.security.CodeSource codeSource) {
		if (codeSource == null)
			return null;

		try {
			File file = new File(codeSource.getLocation().toURI()).getAbsoluteFile();
			
			return file.getPath();
		}
		catch (URISyntaxException exception) {
			log(Level.SEVERE, "Invalid location URI: %s (%s)", codeSource.getLocation(), exception.getMessage());

			return null;
		}
	}
	
	// package
	
	static void setVerified(final File file, final String checksum) {
		log(Level.INFO, "Caching verified file: %s (SHA-1 = %s)", file, checksum);

		checksumCache.put(file, checksum);
		if (savedAccess != null) {
			try {
				savedAccess.save();
			}
			catch (IOException exception) {
				exception.printStackTrace();
			}
		}
	}
	
	static Tuple.Two<Boolean, String> verify(final File file) {
		try {
			log(Level.INFO, "Calculating SHA-1 checksum: %s", file);

			byte[] checksum = Checksum.get("SHA-1", file);
			String checksumString = Checksum.toString(checksum);
			String cachedChecksum = checksumCache.get(file);

			return Tuple.of(
				(cachedChecksum != null) && cachedChecksum.equals(checksumString),
				checksumString
			);
		}
		catch (IOException | NoSuchAlgorithmException exception) {
			exception.printStackTrace();
		
			return Tuple.of(false, null);
		}
	}

	// private

	private static final class AccessMap extends ConcurrentHashMap<PermissionKey, Access> {

		// private

		private final File configFile;
		
		// private
		
		private AccessMap(final File configFile) {
			this.configFile = configFile;
		}

		private void load() throws IOException {
			SimpleXMLReader xml = null;
			try {
				xml = new SimpleXMLReader() {
					private boolean inChecksum;
					private boolean inLocation;
					private boolean inPolicy;
					private String location;
					@Override
					protected void onEnd(final String elementName) {
						switch (elementName) {
							case "checksum":
								inChecksum = false;
								break;
							case "location":
								inLocation = false;
								location = null;
								break;
							case "policy":
								inPolicy = false;
								break;
						}
					}
					@Override
					protected void onStart(final String elementName) {
						if (!inChecksum && "checksum".equals(elementName)) {
							inChecksum = true;
						}
						else if (inPolicy && "location".equals(elementName)) {
							inLocation = true;
							location = this.getStringAttribute("value");
						}
						else if (!inPolicy && "policy".equals(elementName)) {
							inPolicy = true;
						}
						else if (inChecksum) {
							switch (elementName) {
								case "verified": {
									String locationAttr = this.getStringAttribute("location");
									String sha1 = this.getStringAttribute("sha1");

									if (TK.isEmpty(locationAttr) || TK.isEmpty(sha1)) {
										Armor.log(Level.SEVERE, "Invalid checksum entry");
								
										return;
									}

									checksumCache.put(new File(locationAttr), sha1);
								} break;
							}
						}
						else if (inLocation) {
							if (TK.isEmpty(location)) {
								Armor.log(Level.SEVERE, "Unknown location value: %s", elementName);
								
								return;
							}
						
							Access access;
							switch (elementName) {
								case "allow":
									access = Access.ALLOW;
									break;
								case "deny":
									access = Access.DENY;
									break;
								default:
									Armor.log(Level.SEVERE, "Unknown access name: %s", elementName);

									return;
							}
							
							File f = new File(this.location);
							this.location = f.isAbsolute() ? f.getPath() : FS.getPlatformConfigPath(f.getPath());
							PermissionKey key = new PermissionKey(
								this.location,
								this.getStringAttribute("permission"),
								this.getStringAttribute("name"),
								this.getStringAttribute("actions")
							);
							AccessMap.this.put(key, access);
						}
					}
				};
				xml.read(configFile);
			}
			catch (FileNotFoundException exception) {
				Armor.log(Level.INFO, "No policy properties found: %s", configFile);
			}
			finally {
				FS.close(xml);
			}
		}

		private void save() throws IOException {
			CollectionMap<String, Map.Entry<PermissionKey, Access>> compactMap = new CollectionMap<>(
				CollectionMap.MapType.HASH_MAP, CollectionMap.CollectionType.ARRAY_LIST
			);
			for (Map.Entry<PermissionKey, Access> i : entrySet()) {
				PermissionKey key = i.getKey();
				compactMap.add(key.location, i);
			}
		
			final XMLBuilder xml = new XMLBuilder();
			xml.beginTag("policy", "version", 1);
			
			xml.beginTag("checksum");
			for (Map.Entry<File, String> entry : checksumCache.entrySet()) {
				xml.singleTag(
					"verified",
					"location", entry.getKey().getPath(),
					"sha1", entry.getValue()
				);
			}
			xml.endTag("checksum");
			
			for (Map.Entry<String, Collection<Map.Entry<PermissionKey, Access>>> entry : compactMap.entrySet()) {
				String location = FS.getPortableConfigPath(entry.getKey());
				xml.beginTag("location", "value", location);
				for (Map.Entry<PermissionKey, Access> i : entry.getValue()) {
					PermissionKey key = i.getKey();
					xml.singleTag(
						(i.getValue() == Access.ALLOW) ? "allow" : "deny",
						"permission", key.permissionClassName,
						"name", key.name,
						"actions", key.actions
					);
				}
				xml.endTag("location");
			}
			xml.endTag("policy");
			xml.write(configFile);
		}

	}

	private static final class PermissionKey {

		// private

		private final int hash;
		private final String actions;
		private final String name;
		private final String location;
		private final String permissionClassName;

		// public
		
		@Override
		@SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
		public boolean equals(final Object o) {
			switch (MObject.equalsFinal(this, o)) {
				case YES: return true;
				case NO: return false;
				default:
					PermissionKey other = (PermissionKey)o;

					return
						Objects.equals(this.location, other.location) &&
						Objects.equals(this.actions, other.actions) &&
						Objects.equals(this.name, other.name) &&
						Objects.equals(this.permissionClassName, other.permissionClassName);
			}
		}

		@Override
		public int hashCode() { return hash; }
		
		public String getActions() { return actions; }
		
		public String getLocation() { return location; }
		
		public String getName() { return name; }
		
		public String getPermissionClassName() { return permissionClassName; }
		
		@Override
		public String toString() {
			return permissionClassName + ": " + actions + " -> " + name + " (" + location + ")";
		}

		public PermissionKey withActions(final String newActions) {
			return new PermissionKey(
				this.location,
				this.permissionClassName,
				this.name,
				newActions
			);
		}

		public PermissionKey withName(final String newName) {
			return new PermissionKey(
				this.location,
				this.permissionClassName,
				newName,
				this.actions
			);
		}

		// private

		private PermissionKey(final String location, final String permissionClassName, final String name, final String actions) {
			this.location = Objects.requireNonNull(location);
			this.permissionClassName = Objects.requireNonNull(permissionClassName);
			this.name = Objects.requireNonNull(name);
			this.actions = Objects.toString(actions, "");
			hash = MObject.hashCode(location, actions, name, permissionClassName);
		}

		private PermissionKey(final String location, final java.security.Permission permission) {
			this(location, permission.getClass().getName(), permission.getName(), permission.getActions());
		}

	}

}
