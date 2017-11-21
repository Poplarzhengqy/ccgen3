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

package org.makagiga.commons;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileLock;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.EventListener;
import java.util.EventObject;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.EventListenerList;

import org.makagiga.commons.about.MAboutDialog;
import org.makagiga.commons.annotation.ConfigEntry;
import org.makagiga.commons.cache.FileCache;
import org.makagiga.commons.io.LogFile;
import org.makagiga.commons.proxy.ProxyManager;
import org.makagiga.commons.sb.DefaultPageChecker;
import org.makagiga.commons.sb.PageChecker;
import org.makagiga.commons.sb.SecureOpen;
import org.makagiga.commons.security.MAccessController;
import org.makagiga.commons.security.MAuthenticator;
import org.makagiga.commons.security.MPermission;
import org.makagiga.commons.swing.MComponent;
import org.makagiga.commons.swing.MMainWindow;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MNotification;
import org.makagiga.commons.swing.MSplashScreen;
import org.makagiga.commons.swing.MStatusBar;
import org.makagiga.commons.swing.MTip;
import org.makagiga.commons.swing.MainView;

/**
 * This class <i>imitates</i> the Swing Application Framework API (JSR-296)
 * https://appframework.dev.java.net
 *
 * @mg.note Use {@link #quit()} to shutdown the application.
 *
 * @see #getQuitAction()
 *
 * @since 2.0
 */
public abstract class MApplication {
	
	// public

	/**
	 * @since 3.8.8
	 */
	public enum Init {
		AUTHENTICATOR, PROXY, TIPS, USER_AGENT,

		/**
		 * Automatically invoke {@link #initLookAndFeel()}.
		 *
		 * @since 4.0
		 */
		LOOK_AND_FEEL
	};
	
	// confirmations

	/**
	 * @since 4.4
	 */
	@ConfigEntry("Application.offline")
	public static final BooleanProperty offline = new BooleanProperty(false, BooleanProperty.SECURE_WRITE);

	/**
	 * Show exit confirmation dialog.
	 * 
	 * @since 3.0
	 */
	@ConfigEntry("Confirm.exit")
	public static final BooleanProperty confirmExit = new BooleanProperty();
	
	// console

	/**
	 * The Console font size (8..32).
	 *
	 * @since 3.0
	 */
	@ConfigEntry("Console.fontSize")
	public static final IntegerProperty consoleFontSize = new IntegerProperty(12);

	/**
	 * Whether or not Console should be opened in tab instead of window.
	 *
	 * @since 3.0
	 */
	@ConfigEntry("Console.openInTab")
	public static final BooleanProperty consoleOpenInTab = new BooleanProperty(true);

	/**
	 * @since 4.0
	 */
	@ConfigEntry("Console.printStdErr")
	public static final BooleanProperty consolePrintStdErr = new BooleanProperty(true, BooleanProperty.SECURE_WRITE);

	/**
	 * @since 4.0
	 */
	@ConfigEntry("Console.printStdOut")
	public static final BooleanProperty consolePrintStdOut = new BooleanProperty(true, BooleanProperty.SECURE_WRITE);

	/**
	 * @since 4.4
	 */
	@ConfigEntry("Calendar.firstDayOfWeek")
	public static final EnumProperty<MCalendar.FirstDayOfWeek> firstDayOfWeek = new EnumProperty<>(MCalendar.FirstDayOfWeek.DEFAULT, EnumProperty.NON_NULL);

	/**
	 * The Console prompt text.
	 *
	 * @since 3.0
	 */
	@ConfigEntry("Console.prompt")
	public static final StringProperty consolePrompt = new StringProperty(">:");

	// private
	
	private final AccessControlContext acc;
	private volatile static boolean callOnShutDown = true;
	private static boolean firstRun;
	private static boolean forceRTL;
	private static boolean initialized;
	private static boolean isShutDown;
	private static boolean safeMode;
	private static Class<? extends MApplication> resourcesClass;
	private static Color _lightBrandColor;
	private static EventListenerList listeners = new EventListenerList();
	private static File lockFile;
	private static FileLock fileLock;
	private static Icon icon;
	private static Icon smallIcon;
	private static Image logo;
	private static LogFile _logFile;
	private static MAction quitAction;
	private static MApplication _instance;
	private static ResourceBundle resources;
	private static Set<Init> initSet;
	private static String _copyright;
	private static String _description;
	private static String _fileVersion;
	private static String _fullName;
	private static String _fullVersion;
	private static String _homePage;
	private static String _internalName;
	private static VersionProperty _internalVersion;
	
	// public
	
	/**
	 * @since 3.0
	 */
	public static void addConfigEntries(final Class<?> clazz) {
		checkPermission("addConfigEntries");
		Config.registerDefaultClass(clazz);
	}
	
	/**
	 * @since 3.0
	 */
	public synchronized static void addShutDownListener(final ShutDownListener l) {
		listeners.add(ShutDownListener.class, l);
	}

	/**
	 * @since 3.0
	 */
	public synchronized static void removeShutDownListener(final ShutDownListener l) {
		listeners.remove(ShutDownListener.class, l);
	}
	
	/**
	 * @since 3.0
	 */
	public static boolean canLaunchAnotherInstance() {
		if (!isLocked())
			return true;
		
		try {
			return UI.invokeAndWait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					UIManager.put("swing.boldMetal", false);

					return new MMessage.Builder()
						.icon(getIcon())
						.ok(MActionInfo.CONTINUE)
						.cancel(MActionInfo.QUIT)
						.html(_("It seems that {0} is already running", getTitle()))
					.exec();
				}
			} );
		}
		catch (Exception exception) {
			MLogger.exception(exception);
			
			return true;
		}
	}

	/**
	 * @since 3.0
	 */
	public static boolean checkOneInstance() {
		if (!isLocked())
			return true;
		
		try {
			return UI.invokeAndWait(new Callable<Boolean>() {
				@Override
				public Boolean call() throws Exception {
					UIManager.put("swing.boldMetal", false);

					MMessage.info(
						null,
						UI.makeHTML(_("It seems that {0} is already running", getTitle()))
					);
					
					return false;
				}
			} );
		}
		catch (Exception exception) {
			MLogger.exception(exception);
			
			return true;
		}
	}

	public static String getBugs() {
		return getResourceString("Application.x.bugs", null);
	}
	
	public static String getBuildInfo() {
		return getResourceString("Application.x.buildInfo", null);
	}
	
	/**
	 * Returns the copyright info (example: "(C) 2005 Foo").
	 */
	public static String getCopyright() {
		return (_copyright == null) ? getResourceString("Application.vendor", null) : _copyright;
	}
	
	/**
	 * Returns a short application description, or @c null.
	 */
	public static String getDescription() {
		return (_description == null) ? getResourceString("Application.description", null) : _description;
	}

	/**
	 * Sets short application description to @p value.
	 *
	 * @since 2.4
	 */
	public static void setDescription(final String value) {
		checkPermission("setDescription");
		synchronized (MApplication.class) {
			_description = value;
		}
	}
	
	/**
	 * @since 2.4
	 */
	public static String getFileVersion() {
		return (_fileVersion == null) ? getResourceString("Application.x.fileVersion", null) : _fileVersion;
	}

	/**
	 * @since 2.4
	 */
	public static void setFileVersion(final String value) {
		checkPermission("setFileVersion");
		synchronized (MApplication.class) {
			_fileVersion = value;
		}
	}

	/**
	 * @since 4.0
	 */
	public static boolean getForceRTL() { return forceRTL; }

	/**
	 * Returns the full name (example: "Makagiga").
	 */
	public static String getFullName() {
		return (_fullName == null) ? getResourceString("Application.name", getInternalName()) : _fullName;
	}

	/**
	 * Returns the full version (example: "1.9.10 Beta").
	 */
	public static String getFullVersion() {
		return (_fullVersion == null) ? getResourceString("Application.version", null) : _fullVersion;
	}
	
	/**
	 * Returns the home page URL
	 * (example: "http://www.example.com/").
	 */
	public static String getHomePage() {
		return (_homePage == null) ? getResourceString("Application.homepage", null) : _homePage;
	}

	/**
	 * @since 3.8.8
	 */
	public synchronized static Color getLightBrandColor() {
		if (_lightBrandColor == null) {
			String s = getResourceString("Application.x.lightBrandColor", null);
			if (TK.isEmpty(s)) {
				_lightBrandColor = MColor.SKY_BLUE;
			}
			else {
				try {
					_lightBrandColor = ColorProperty.parseColor(s);
				}
				catch (ParseException exception) {
					_lightBrandColor = MColor.SKY_BLUE;
					MLogger.exception(exception);
				}
			}
		}
		
		return _lightBrandColor;
	}
	
	/**
	 * Returns the lock file or {@code null} if the {@link #isLocked()} method was not called.
	 *
	 * @since 4.0
	 */
	public synchronized static File getLockFile() { return lockFile; }

	/**
	 * Returns the default log file ("console.log").
	 *
	 * @since 3.2
	 */
	public static LogFile getLogFile() {
		checkPermission("getLogFile");
		synchronized (MApplication.class) {
			if (_logFile == null)
				_logFile = new LogFile(FS.makeConfigFile("console.log"));

			return _logFile;
		}
	}

	/**
	 * Returns the application icon, or @c null.
	 */
	public synchronized static Icon getIcon() { return icon; }
	
	/**
	 * Sets the application icon to @p value (can be @c null).
	 */
	public static void setIcon(final Icon value) {
		checkPermission("setIcon");
		synchronized (MApplication.class) {
			icon = value;
		}
	}

	/**
	 * @since 4.2
	 */
	public synchronized static Icon getSmallIcon() {
		if (smallIcon != null)
			return smallIcon;

		Icon icon = getIcon();
		if (icon instanceof MIcon)
			icon = MIcon.class.cast(icon).scaleSmall();
		
		return icon;
	}
	
	/**
	 * @since 4.2
	 */
	public static void setSmallIcon(final Icon value) {
		checkPermission("setIcon");
		synchronized (MApplication.class) {
			smallIcon = value;
		}
	}

	/**
	 * Returns the internal name (example: "makagiga").
	 * Internal name is used as a part of the user configuration directory path.
	 * This name should be file system safe (e.g. no space, local characters or "/").
	 * 
	 * @throws IllegalStateException If "internal name" is not set
	 */
	public static String getInternalName() {
		if (TK.isEmpty(_internalName))
			throw new IllegalStateException("\"internal name\" is not set");

		return _internalName;
	}

	public synchronized static VersionProperty getInternalVersion() {
		if (_internalVersion.equalsValue(0)) {
			String value = getResourceString("Application.x.internalVersion", null);
			if (value != null) {
				// New VersionProperty format
				if (value.contains(".")) {
					try {
						_internalVersion.parse(value);
					}
					catch (ParseException exception) {
						MLogger.exception(exception);
					}
				}
				// Old hex format
				else {
					_internalVersion.set(Integer.parseInt(value, 16));
				}
			}
		}
		
		return _internalVersion;
	}

	/**
	 * Returns the logo image, or @c null.
	 */
	public synchronized static Image getLogo() { return logo; }
	
	/**
	 * Sets the logo image to @p value (can be @c null).
	 */
	public static void setLogo(final Image value) {
		checkPermission("setLogo");
		synchronized (MApplication.class) {
			logo = value;
		}
	}

	/**
	 * Returns the <i>Quit</i> action, which exits the application.
	 */
	public synchronized static MAction getQuitAction() {
		if (quitAction == null)
			quitAction = new QuitAction();

		return quitAction;
	}
	
	/**
	 * @since 4.0
	 */
	public synchronized static String getResourcesPath(final boolean resourceBundle) {
		StringBuilder s = new StringBuilder();
		if (resourceBundle) {
			s.append(resourcesClass.getPackage().getName());
			s.append('.');
		}
		else {
			s.append('/');
			s.append(resourcesClass.getPackage().getName().replace('.', '/'));
			s.append('/');
		}
		s.append("resources");

		return s.toString();
	}
	
	/**
	 * @since 3.0
	 */
	public synchronized static String getResourceString(final String key, final String defaultValue) {
		if (resources == null) {
			MLogger.warning("core", "No Application resource for \"%s\" key", key);
			
			return defaultValue;
		}
		
		try {
			return resources.getString(key);
		}
		catch (MissingResourceException exception) {
			if (MLogger.isDeveloper())
				MLogger.warning("core", "Missing Application resource for \"%s\" key", key);
			
			return defaultValue;
		}
	}

	/**
	 * @since 3.8
	 */
	public static String getRestartMessage() {
		return _("OK. Restart application to apply changes.");
	}

	/**
	 * Returns the full name + full version
	 * (example: "Makagiga 1.9.10 Beta").
	 */
	public synchronized static String getTitle() {
		String fullVersion = getFullVersion();

		if (fullVersion == null)
			return getFullName();
		
		return (getFullName() + " " + fullVersion);
	}
	
	/**
	 * @since 3.0
	 */
	public static boolean isFirstRun() { return firstRun; }

	public synchronized static boolean isInitialized() { return initialized; }
	
	/**
	 * Returns @c true if application is locked.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * if (MApplication.isLocked()) {
	 *   System.out.println("Only one instance is allowed.");
	 *   System.exit(0);
	 * }
	 * System.out.println("Loading...");
	 * </pre>
	 *
	 * @throws IllegalStateException If "internal name" is not set
	 */
	public synchronized static boolean isLocked() {
		lockFile = FS.makeConfigFile(getInternalName() + ".lock");
		try {
			fileLock = new FileOutputStream(lockFile).getChannel().tryLock();
		}
		catch (IOException exception) { // quiet
			return false;
		}
		// do not close stream or file will be unlocked

		return (fileLock == null);
	}

	public synchronized static boolean isSafeMode() { return safeMode; }
	
	/**
	 * @since 2.2
	 */
	public synchronized static boolean isShutDown() { return MApplication.isShutDown; }

	public static void launch(final Class<? extends MApplication> clazz) {
		checkPermission("launch");

		try {
			clazz.newInstance();
		}
		catch (Exception exception) {
			MLogger.exception(exception);
		}
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final String uri) {
		return openURI(uri, (PageChecker)null);
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final String uri, final PageChecker pageChecker) {
		return openURI(Net.fixURI(uri), pageChecker);
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final URI uri) {
		return openURI(uri, (PageChecker)null);
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final URI uri, final PageChecker pageChecker) {
		if (pageChecker != null)
			return secureOpen(uri, pageChecker);
		
		if (Kiosk.linkAllowOpen.get()) {
			showOpenInfo(uri);

			return OS.open(uri);
		}
		
		return false;
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final URI uri, final SecureOpen so) {
		return openURI(uri, so.isSecureOpen() ? DefaultPageChecker.get() : null);
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final URL url, final PageChecker pageChecker) {
		try {
			return openURI(url.toURI(), pageChecker);
		}
		catch (URISyntaxException exception) {
			MMessage.error(null, exception);

			return false;
		}
	}

	/**
	 * @since 4.0
	 */
	public static boolean openURI(final String uriTemplate, final String... args) {
		if (TK.isEmpty(args))
			return openURI(uriTemplate);
		
		Object[] escapedArgs = new Object[args.length];
		for (int i = 0; i < escapedArgs.length; i++)
			escapedArgs[i] = (args[i] == null) ? "" : TK.escapeURL(args[i]);

		URI escapedURI = Net.fixURI(MessageFormat.format(uriTemplate, escapedArgs));

		return openURI(escapedURI);
	}

	/**
	 * Quits the application, if {@code onQuit()} return {@code true}.
	 *
	 * @mg.warning
	 * This method invokes {@link java.lang.System#exit} which shuts down the entire JVM.
	 *
	 * @see #onQuit()
	 */
	public static void quit() {
		checkPermission("quit");
		
		if (!Kiosk.actionQuit.get()) {
			MLogger.info("core", "\"Quit\" action is disabled");
			
			return;
		}
		
		if (_instance != null) {
			if (_instance.onQuit())
				_instance.shutDown();
		}
		else {
			System.exit(0);
		}
	}

	// protected

	protected MApplication() {
		acc = AccessController.getContext();
		_instance = this;

		MLogger.info("core", getTitle());

		if ((initSet != null) && initSet.contains(Init.TIPS))
			MTip.load(); // in background

		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				// Do not load Metal classes: UIManager.put("swing.boldMetal", false);

				if (Args.check())
					return;
				
				ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
				toolTipManager.setDismissDelay(8000); // default is 4000
				
				if ((initSet != null) && initSet.contains(Init.LOOK_AND_FEEL))
					initLookAndFeel();

				startup();
				
				initialized = true;
				MSplashScreen.close();

				// setup common main window components
				MMainWindow mainWindow = MainView.getWindow();
				if (mainWindow != null) {
					if (MLogger.isDeveloper()) {
						JMenuBar menuBar = mainWindow.getJMenuBar();
						if (
							(menuBar != null) &&
							!Boolean.TRUE.equals(menuBar.getClientProperty("org.makagiga.commons.MLogger.noMemoryButton"))
						) {
							int addIndex = -1;
							int count = menuBar.getComponentCount();
							for (int i = 0; i < count; i++) {
								if (menuBar.getComponent(i) instanceof Box.Filler) {
									addIndex = i;

									break; // for
								}
							}
							menuBar.add(MLogger.createMemoryButton(), addIndex);
						}
					}
				}
				
				Config config = Config.getDefault();
				boolean needSyncConfig = false;
				
				// disable forced safe-session
				
				if (isSafeMode()) {
					config.write("safeMode", false);
					needSyncConfig = true;
				}

				if (!OS.isSupported() && config.read("showOSWarning." + OS.getName(), true)) {
					config.write("showOSWarning." + OS.getName(), false);
					needSyncConfig = true;
					MNotification.showInfo(_("This operating system ({0}) is not fully supported", OS.getName()));
				}
				
				if (needSyncConfig)
					config.sync();

				installShutDownHooks();
			}
		} );
	}
	
	/**
	 * @since 3.0
	 */
	protected boolean confirmQuit() {
		if (!confirmExit.get()/* && (MNotification.getMessageCount() == 0)*/)
			return true;

		Component oldGlassPane = null;
		JComponent grayBackground = null;
		MMainWindow mw = MainView.getWindow();
		
		if (mw != null) {
			if (mw.isLocked())
				return true;
		
			oldGlassPane = mw.getGlassPane();
			MMainWindow.clearGlassPane(mw);
			mw.restore();
			
			grayBackground = new MComponent() {
				@Override
				protected void paintComponent(final Graphics g) {
					g.setColor(UI.TRANSPARENT_BLACK);
					MGraphics2D.fillRect(g, this);
				}
			};
			oldGlassPane.setVisible(false);
			mw.setGlassPane(grayBackground);
			grayBackground.setVisible(true);
		}

		boolean result = MMessage.simpleConfirm(mw, MActionInfo.QUIT);

		if (mw != null) {
			grayBackground.setVisible(false);
			mw.setGlassPane(oldGlassPane);
		}
		
		return result;
	}

	protected static void init(final String[] args, final String internalName) {
		init(
			args,
			internalName,
			internalName, // full name
			0, // internal version
			null, // full version
			null, // copyright
			null // home page
		);
	}

	/**
	 * @since 3.0
	 */
	protected static void init(final String[] args, final String internalName, final Class<? extends MApplication> resourcesClass) {
		synchronized (MApplication.class) {
			MApplication.resourcesClass = resourcesClass;
		}
		init(
			args,
			internalName,
			null, // no full name - read from resources
			0, // internal version
			null, // full version
			null, // copyright
			null // home page
		);
	}

	/**
	 * @throws IllegalArgumentException If @p internalName is "categories" or "internetsearch" (to avoid file name collision)
	 *
	 * @since 2.4
	 */
	protected static void init(
		final String[] args,
		final String internalName,
		final String fullName,
		final int internalVersion,
		final String fullVersion,
		final String copyright,
		final String homePage
	) {
		// HACK: http://makagiga.blogspot.com/2010/08/openjdk-slooow-user-interface.html
		// NOTE: Set this property before Toolkit initialization (see below).
		if (OS.isLinux() && OS.isOpenJDK())
			System.setProperty("sun.java2d.pmoffscreen", "false");

		// HACK: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5076635
		// The default value from XSettings is too small (200).
		if (OS.isLinux()) {
			Toolkit t = Toolkit.getDefaultToolkit();
			Object i = t.getDesktopProperty("awt.multiClickInterval");
			if ((i instanceof Integer) && Integer.class.cast(i).intValue() < 400) {
				try {
					Field f = t.getClass().getDeclaredField("awt_multiclick_time");
					f.setAccessible(true);
					f.set(null, 500);
				}
				catch (Exception exception) {
					MLogger.exception(exception);
				}
			}
		}

		synchronized (FS.class) {
			FS.restricted = false;
		}
		_copyright = copyright;
		_fullName = fullName;
		_fullVersion = fullVersion;
		_homePage = homePage;

		if (
			"categories".equals(internalName) ||
			"internetsearch".equals(internalName)
		)
			throw new IllegalArgumentException("Reserved internal name: " + internalName);

		_internalName = internalName;
		_internalVersion = new VersionProperty(internalVersion);
		
		// init resources - before Args.init
		synchronized (MApplication.class) {
			if ((resourcesClass != null) && (resources == null)) {
				String resourcePath = getResourcesPath(true) + "." + resourcesClass.getSimpleName();
				try {
					resources = ResourceBundle.getBundle(resourcePath);
					//MLogger.info("core", "Using \"%s\" Application resources", resourcePath);

					// init icon/logo
					String logoName = getResourceString("Application.x.logo", null);
					if ((logoName != null) && !OS.isHeadless()) {
						setIcon(MIcon.stock(logoName));
						setLogo(MIcon.getImage(logoName));
					}
				}
				catch (MissingResourceException exception) {
					MLogger.exception(exception);
					MLogger.warning("core", "Missing Application resources: %s", resourcePath);
				}
			}
		}

		Args.init(args);
		
		forceRTL = Args.isSet("rtl");
		
		// init and load config
		Config.setDefaultComment("Generated by " + getFullName() + ", do not modify!");
		Config config = Config.getDefault();

		// init safe mode
		safeMode = Args.isSet("safe-mode");
		if (!isSafeMode() && !MLogger.isDeveloper())
			safeMode = config.read("safeMode", false);

		// check first run
		int run = config.readInt("run", 1, 1);
		firstRun = (run == 1);
		if (firstRun) {
			//MLogger.info("core", "First run. Setting default values...");
			config.write("run", ++run);
			UI.buttonIcons.set(!OS.isMac() && !OS.isWindows());
			config.sync();
		}
	}

	/**
	 * Sets the preferred look and feel.
	 * 
	 * @see UI#getPreferredLookAndFeelClassName()
	 * 
	 * @since 2.2
	 */
	protected static void initLookAndFeel() {
		try {
			String laf = UI.getPreferredLookAndFeelClassName();
			UI.setLookAndFeel(laf);
		}
		catch (Exception exception) {
			MLogger.exception(exception);
		}
	}

	/**
	 * @since 4.0
	 */
	protected static void initPlatform(final Init... options) {
		initSet = TK.newEnumSet(options);

		if (initSet.contains(Init.USER_AGENT)) {
			String value = "Mozilla/5.0 (compatible; " + getFullName() + "/" + getInternalVersion() + ")";
			System.setProperty("http.agent", value);
		}

		if (initSet.contains(Init.AUTHENTICATOR))
			MAuthenticator.init();

		if (initSet.contains(Init.PROXY))
			ProxyManager.init();
	}

	/**
	 * Invoked when the user quits the application.
	 * 
	 * @return @c true - quit application, @c false - cancel quit
	 */
	protected boolean onQuit() {
		MLogger.info("core", "Quit...");
		
		return confirmQuit();
	}

	/**
	 * Use the @ref Args class to check command line arguments.
	 */
	protected abstract void startup();
	
	// private
	
	private static void checkPermission(final String name) {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new MApplication.Permission(name));
	}
	
	private void doShutDownAndCleanUp() {
		MLogger.info("core", "Shut down...");
		
		Config config = Config.getDefault();
		MMainWindow mainWindow = MainView.getWindow();
		if (mainWindow != null)
			mainWindow.writeConfig(config, null);

		ShutDownListener[] sdl = listeners.getListeners(ShutDownListener.class);
		if (sdl.length > 0) {
			ShutDownEvent e = new ShutDownEvent(this);
			for (ShutDownListener i : sdl)
				i.shutDown(e);
		}
		
		config.sync();
		
		// clean up
		FileCache.getInstance().close();
		FS.close(_logFile);
	}
	
	private void installShutDownHooks() {
		if (!initialized)
			return;
		
		// session shut down handler
		if (OS.isWindows()) {
			// HACK: JVM raises SIGTERM signal on WM_QUERYENDSESSION event
			// see: sun/windows/awt_Toolkit.cpp
			try {
				InvocationHandler signalHandler = new InvocationHandler() {
					@Override
					public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
						if ("handle".equals(method.getName())) {
							//MLogger.debug("core", "method=\"%s\", args=\"%s\"", method, TK.toString(args, " "));
							if (callOnShutDown)
								shutDown();
						}

						return null;
					}
				};
				
				Class<?> signalClass = Class.forName("sun.misc.Signal");
				Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
				Object signalObject = signalClass.getConstructor(String.class).newInstance("TERM");

				Method handleMethod = signalClass.getMethod("handle", signalClass, signalHandlerClass);
				handleMethod.invoke(
					null,
					signalObject,
					Proxy.newProxyInstance(
						signalHandlerClass.getClassLoader(),
						new Class<?>[] { signalHandlerClass },
						signalHandler
					)
				);
/*
				sun.misc.Signal.handle(new sun.misc.Signal("TERM"), new sun.misc.SignalHandler() {
					public void handle(final sun.misc.Signal sig) {
						if (callOnShutDown)
							shutDown();
					}
				} );
*/
 
			}
			catch (ThreadDeath error) {
				throw error;
			}
			catch (Throwable error) { // catch all
				MLogger.exception(error);
			}
		}
		else {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					if (callOnShutDown)
						shutDown();
				}
			} );
		}
	}

	private static boolean secureOpen(final URI uri, final PageChecker pageChecker) {
		if (!Kiosk.linkAllowOpen.get())
			return false;
		
		PageChecker.PageStatus pageStatus = pageChecker.getPageStatus(uri);
		if (
			(pageStatus == PageChecker.PageStatus.OK) ||
			pageChecker.showWarning(null, uri, pageStatus)
		) {
			showOpenInfo(uri);

			return OS.open(uri);
		}
		
		return false;
	}

	private static void showOpenInfo(final URI uri) {
		if (MainView.getStatusBar() != null)
			MStatusBar.info(_("Opening \"{0}\"...", uri));
	}

	@edu.umd.cs.findbugs.annotation.SuppressWarnings("DM_EXIT")
	private synchronized void shutDown() {
		callOnShutDown = false;
		try {
			MAccessController.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					doShutDownAndCleanUp();
				}
			}, acc);
		}
		catch (Exception exception) {
			MLogger.exception(exception);
		}
		isShutDown = true;

		AccessController.doPrivileged(new PrivilegedAction<Void>() {
			@Override
			public Void run() {
				System.exit(0);
				
				return null;
			}
		} );
	}

	// public classes

	public static class AboutAction extends MAction {

		// public

		public AboutAction() {
			super(MApplication.getTitle());
			setHTMLHelp(_("Displays the information about this application."));
			setSmallIcon(MApplication.getSmallIcon());
		}

		@Override
		public void onAction() {
			MMainWindow mainWindow = MainView.getWindow();
			if (mainWindow == null) {
				MAboutDialog about = new MAboutDialog(null);
				about.exec();
			}
			else {
				mainWindow.about();
			}
		}
		
	}

	public static class FullScreenAction extends MAction {
		
		// public
		
		public FullScreenAction() {
			super(_("Full Screen"), "ui/fullscreen", VK_F11);
			setAuthorizationProperty(Kiosk.actionFullScreen);
		}
		
		@Override
		public void onAction() {
			MMainWindow mainWindow = MainView.getWindow();
			if (mainWindow != null)
				mainWindow.toggleFullScreen();
		}
		
	}
	
	/**
	 * @since 3.0
	 */
	public static final class Permission extends MPermission {
		
		// private
		
		private Permission(final String name) {
			super(name, ThreatLevel.HIGH, "Application");
		}
		
	}

	public static class PrintAction extends MAction {
		
		// public
		
		public PrintAction() {
			super(MActionInfo.PRINT);
			setAuthorizationProperty(Kiosk.actionPrint);
		}
		
	}

	/**
	 * Since 3.8.9
	 */
	public static class SettingsAction extends MAction {

		// public

		public SettingsAction() {
			super(MActionInfo.SETTINGS);
			setAuthorizationProperty(Kiosk.actionSettings);
		}

	}

	/**
	 * @since 3.0
	 */
	public static final class ShutDownEvent extends EventObject {
		
		// public
		
		public ShutDownEvent(final Object source) {
			super(source);
		}
		
	}

	/**
	 * Invoked when the program exits normally,
	 * or when the @b JVM is terminated (e.g. system shut down).
	 * 
	 * @since 3.0
	 */
	public static interface ShutDownListener extends EventListener {
		
		// public
		
		public void shutDown(final ShutDownEvent e);
		
	}

	/**
	 * @since 4.4
	 */
	public static class ToggleOfflineAction extends MAction {
		
		// public

		public ToggleOfflineAction() {
			super(_("Work Offline"));
			setAuthorizationProperty(Kiosk.actionSettings);
			setHTMLHelp(_("Disable Internet connections in this application."));
			setSelected(MApplication.offline.get());
		}

		@Override
		public void onAction() {
			MApplication.offline.set(isSelected());
		}

	}

	/**
	 * @since 2.4
	 */
	public static class ToggleStatusBarAction extends MDataAction.Weak<MStatusBar> {
		
		// public

		public ToggleStatusBarAction(final MStatusBar statusBar) {
			super(statusBar, _("Status Bar"));
			
			setSelected(!statusBar.isClosed());

			// update action on status bar property change
			statusBar.addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(final PropertyChangeEvent e) {
					if (e.getPropertyName().equals(MStatusBar.CLOSED_PROPERTY))
						setSelected(!(Boolean)e.getNewValue());
				}
			} );
		}

		@Override
		public Component getHighlightedComponent() { return getStatusBar(); }

		public MStatusBar getStatusBar() {
			return get();
		}

		@Override
		public void onAction() {
			MStatusBar sb = getStatusBar();
			if (sb != null)
				sb.setClosed(!isSelected());
		}

	}

	/**
	 * @since 2.4
	 */
	public static class ZoomInAction extends MAction {
		
		// public

		public ZoomInAction() {
			super(_("Zoom In"), "ui/zoomin", VK_EQUALS, getMenuMask());
			setAuthorizationProperty(Kiosk.actionZoom);
		}
		
	}

	/**
	 * @since 2.4
	 */
	public static class ZoomOutAction extends MAction {
		
		// public

		public ZoomOutAction() {
			super(_("Zoom Out"), "ui/zoomout", VK_MINUS, getMenuMask());
			setAuthorizationProperty(Kiosk.actionZoom);
		}
		
	}

	/**
	 * @since 4.0
	 */
	public static class ZoomResetAction extends MAction {
		
		// public

		public ZoomResetAction() {
			super(_("Reset Zoom"), VK_0, getMenuMask());
			setAuthorizationProperty(Kiosk.actionZoom);
		}
		
	}

	// private classes

	private static final class QuitAction extends MAction {

		// public

		public QuitAction() {
			super(MActionInfo.QUIT);
			setAuthorizationProperty(Kiosk.actionQuit);
		}

		@Override
		public void onAction() {
			MApplication.quit();
		}

	}

}
