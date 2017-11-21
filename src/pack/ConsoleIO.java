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

package org.makagiga.console;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.awt.Window;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.script.ScriptException;
import javax.swing.Icon;
import javax.swing.text.AttributeSet;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.makagiga.commons.Config;
import org.makagiga.commons.FS;
import org.makagiga.commons.Kiosk;
import org.makagiga.commons.MApplication;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDisposable;
import org.makagiga.commons.MIcon;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.OS;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.WTFError;
import org.makagiga.commons.io.LogFile;
import org.makagiga.commons.script.ScriptError;
import org.makagiga.commons.security.MAccessController;
import org.makagiga.commons.swing.Input;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MPasswordPanel;
import org.makagiga.commons.swing.MSplashScreen;
import org.makagiga.commons.swing.MStatusBar;
import org.makagiga.commons.swing.MainView;

/**
 * @since 4.0 (ConsoleIO name)
 */
public final class ConsoleIO extends FilterOutputStream {

	// private

	private final AccessControlContext acc;
	private final boolean applicationLog;
	private boolean inPrint;
	private static boolean wasFatal;
	private final LogFile log;
	private final MutableAttributeSet stdAttr;
	private static Pattern ansiEscapePattern = Pattern.compile("\\033\\[[0-9]+m");
	private StdOut newStdErr;
	private StdOut newStdOut;

	// package

	Console console;
	static ConsoleIO _systemIO;

	// public

	public ConsoleIO(final LogFile log) {
		super(new DevNull());
		acc = AccessController.getContext();

		stdAttr = new SimpleAttributeSet();
		StyleConstants.setForeground(stdAttr, MColor.DARK_RED);

		applicationLog = (log == null) ? false : (log == MApplication.getLogFile());
		this.log = log;
	}

	public MutableAttributeSet createColorAttr(final Color color) {
		SimpleAttributeSet attr = new SimpleAttributeSet();
		StyleConstants.setForeground(attr, color);

		return attr;
	}

	/**
	 * Returns the log file.
	 *
	 * @since 3.0
	 */
	public LogFile getLog() { return log; }

	public synchronized static ConsoleIO getSystem() {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null)
			sm.checkPermission(new RuntimePermission("setIO"));

		return _systemIO;
	}

	public synchronized static void install() {
		if (_systemIO != null)
			throw new IllegalStateException("Console IO already installed");

		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(final Thread t, final Throwable e) {
				MLogger.exception(e);
				MAccessController.invokeLater(new Runnable() {
					@Override
					public void run() {
						try {
							doHandleUncaughtException(e);
						}
						catch (ThreadDeath error) {
							throw error;
						}
						// catch all to avoid application freeze
						catch (Throwable exception) {
							MLogger.warning("core", "Exception in error handler");
							MLogger.exception(exception);
						}
					}
				}, _systemIO.acc);
			}
		} );

		_systemIO = new ConsoleIO(MApplication.getLogFile());
		_systemIO.redirectOutput();

		MApplication.addShutDownListener(new MApplication.ShutDownListener() {
			@Override
			public void shutDown(final MApplication.ShutDownEvent e) {
				if (Console.isInstance()) {
					for (ConsoleCommand i : Console.getInstance(false)) {
						if (i instanceof MDisposable)
							TK.dispose((MDisposable)i);
					}
				}

				if (_systemIO != null) {
					_systemIO.shutDown();
					_systemIO = null;
				}
			}
		} );
	}

	/**
	 * Prints a text to the {@code stderr} and to the console text area.
	 * @param text A text to print
	 *
	 * @since 3.0
	 */
	public void print(final String text) {
		print(null, text);
	}

	/**
	 * @since 3.8.5
	 */
	public synchronized void print(final AttributeSet attr, final String text) {
		if (inPrint)
			return;

		try {
			inPrint = true;

			if (newStdErr != null) 
				newStdErr.old.print(text);

			if (log != null) {
				SecurityManager sm = System.getSecurityManager();
				if (sm == null) {
					log.append(text);
				}
				else {
					AccessController.doPrivileged(new PrivilegedAction<Void>() {
						@Override
						public Void run() {
							if (applicationLog)
								log.append(text);
							else
								throw new SecurityException("Invalid log file");

							return null;
						}
					} );
				}
			}

// TODO: interpret ANSI codes instead of remove
			if (console != null)
				console.append(attr, removeANSI(text));
		}
		finally {
			inPrint = false;
		}
	}

	/**
	 * @since 3.8.5
	 */
	public MutableAttributeSet printComponent(final Component c) {
		MutableAttributeSet attr = new SimpleAttributeSet();
		StyleConstants.setComponent(attr, c);
		print(attr, " ");

		return attr;
	}

	/**
	 * @since 3.8.5
	 */
	public MutableAttributeSet printError(final String text) {
		MutableAttributeSet attr = new SimpleAttributeSet();
		StyleConstants.setBold(attr, true);
		StyleConstants.setForeground(attr, Color.RED);
		print(attr, _("Error:"));

		printLine(" " + text);

		return attr;
	}

	/**
	 * @since 3.8.5
	 */
	public MutableAttributeSet printIcon(final Icon icon) {
		MutableAttributeSet attr = new SimpleAttributeSet();
		StyleConstants.setIcon(attr, icon);
		print(attr, " ");

		return attr;
	}

	/**
	 * @since 4.2
	 */
	public MutableAttributeSet printImage(final Image image) {
		return printIcon(new MIcon(image));
	}

	/**
	 * Prints a new line of text to the {@code stderr} and to the console text area.
	 * @param text A text to print
	 *
	 * @since 3.0
	 */
	public void printLine(final String text) {
		print(text + '\n');
	}

	/**
	 * @since 3.8.5
	 */
	public void printLine(final AttributeSet attr, final String text) {
		print(attr, text + '\n');
	}

	/**
	 * Prints a new line to the {@code stderr} and to the console text area.
	 *
	 * @since 3.0
	 */
	public void printLine() {
		printLine("");
	}

	/**
	 * Prints a new line of text to the {@code stderr} and to the console text area.
	 * @param text A text to print
	 * @param args Text arguments
	 *
	 * @since 3.0
	 */
	public void printLine(final String text, final Object... args) {
		printLine(String.format(text, args));
	}

	/**
	 * @since 3.8.5
	 */
	public void printLine(final AttributeSet attr, final String text, final Object... args) {
		printLine(attr, String.format(text, args));
	}

	/**
	 * @since 4.0
	 */
	public String readLine(final ConsoleCommand command, final String initialValue, final String prompt) {
		Objects.requireNonNull(command, "command");

		return new Input.GetTextBuilder()
			.text(initialValue)
			.label(prompt)
			.title(command.getName())
			.icon("ui/console")
			.autoCompletion("console-input")
		.exec(console);
	}

	/**
	 * @since 4.0
	 */
	public char[] readPassword(final ConsoleCommand command, final String prompt) {
		Objects.requireNonNull(command, "command");

		return Input.getPassword(console.getWindowAncestor(), prompt, MPasswordPanel.ALLOW_EMPTY_PASSWORD);
	}

	/**
	 * @since 3.0
	 */
	public synchronized void redirectOutput() {
		if ((newStdErr != null) || (newStdOut != null))
			return;

		try {
			newStdErr = new StdOut(this, true, System.err);
			newStdOut = new StdOut(this, false, System.out);
			System.setErr(newStdErr);
			System.setOut(newStdOut);
		}
		catch (UnsupportedEncodingException exception) {
			throw new WTFError(exception);
		}
	}

	/**
	 * Restores {@code stderr} and {@code stdout} streams,
	 * and flushes the console log buffer to disk.
	 *
	 * @since 3.0
	 */
	public synchronized void shutDown() {
		if (newStdErr != null)
			System.setErr(newStdErr.old);

		if (newStdOut != null)
			System.setOut(newStdOut.old);

		FS.close(log);
		newStdErr = null;
		newStdOut = null;
	}

	@Override
	public void write(final byte[] b) throws IOException {
		print(stdAttr, new String(b, StandardCharsets.UTF_8));
	}

	@Override
	public void write(final byte[] b, final int off, final int len) throws IOException {
		print(stdAttr, new String(b, off, len, StandardCharsets.UTF_8));
	}

	// private

	private static void doHandleUncaughtException(final Throwable e) {
		if (!MApplication.isInitialized() && !MApplication.isShutDown() && !wasFatal) {
			wasFatal = true;

			// Force safe-mode on the next startup.
			// Enable safe-mode before "MMessage.error"
			// in case the error was caused by UI.
			Config config = Config.getDefault();
			config.write("safeMode", true);
			config.sync();

			try {
				MMessage.error(null, e, UI.makeHTML(_("A fatal error occured.<br>See <code>{0}</code> file for details.", MApplication.getLogFile().getFile())));
			}
			catch (ThreadDeath error) {
				throw error;
			}
			// catch UI exceptions
			catch (Throwable exception) {
				MLogger.warning("core", "UI error");
				MLogger.exception(exception);
			}

			Window mainWindow = MainView.getWindow();
			if (mainWindow != null)
				mainWindow.dispose();

			System.exit(1);
		}

		// script error message
		if (
			(e instanceof ScriptException) ||
			e.getClass().getName().startsWith("sun.org.mozilla.javascript.")
		) {
			ScriptError.getSharedInstance().showError(e);

			return;
		}
		else if (
			(e.getCause() instanceof PrivilegedActionException) ||
			(e.getCause() instanceof ScriptException)
		) {
			ScriptError.getSharedInstance().showError(e.getCause());

			return;
		}

		// show console with error message
		if (MLogger.isDeveloper()) {
			MSplashScreen.close();
			if (Kiosk.consoleEnabled.get())
				Console.getInstance(true);
		}

		if (e instanceof OutOfMemoryError)
			MStatusBar.error(e);
	}
	
	// package
	
	static String removeANSI(final String s) {
		if ((s == null) || OS.isWindows())
			return s;

		return ansiEscapePattern.matcher(s).replaceAll("");
	}

	// private classes

	private static final class DevNull extends OutputStream {

		// public

		@Override
		public void write(final byte[] b) { }

		@Override
		public void write(final byte[] b, final int off, final int len) { }

		@Override
		public void write(final int b) { }

	}
	
	private static final class StdOut extends PrintStream {
	
		// private
		
		private final boolean stdErr;
		private final PrintStream old;
	
		// public

		public StdOut(final OutputStream output, final boolean stdErr, final PrintStream old) throws UnsupportedEncodingException {
			super(output, false, "UTF8");
			this.stdErr = stdErr;
			this.old = old;
		}

		@Override
		public void write(final byte[] b) throws IOException {
			if (isEnabled())
				super.write(b);
			else
				old.write(b);
		}

		@Override
		public void write(final byte[] b, final int off, final int len) {
			if (isEnabled())
				super.write(b, off, len);
			else
				old.write(b, off, len);
		}

		@Override
		public void write(final int b) {
			if (isEnabled())
				super.write(b);
			else
				old.write(b);
		}
		
		// private
		
		private boolean isEnabled() {
			if (stdErr)
				return MApplication.consolePrintStdErr.booleanValue();
			
			return MApplication.consolePrintStdOut.booleanValue();
		}

	}

}
