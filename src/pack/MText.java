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

package org.makagiga.commons.swing;

import static java.awt.event.KeyEvent.*;

import static org.makagiga.commons.UI._;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.im.InputContext;
import java.awt.im.spi.InputMethodDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.EditorKit;
import javax.swing.text.Element;
import javax.swing.text.Utilities;
import javax.swing.text.JTextComponent;

import org.makagiga.commons.FS;
import org.makagiga.commons.Kiosk;
import org.makagiga.commons.MAction;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MArrayList;
import org.makagiga.commons.MClipboard;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDataAction;
import org.makagiga.commons.MDataTransfer;
import org.makagiga.commons.MDate;
import org.makagiga.commons.MLogger;
import org.makagiga.commons.OS;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.annotation.Uninstantiable;
import org.makagiga.commons.autocompletion.AutoCompletion;
import org.makagiga.commons.fx.Blend;
import org.makagiga.commons.swing.event.MDocumentAdapter;
import org.makagiga.commons.swing.event.MMouseAdapter;

/**
 * A text component extension.
 * 
 * @since 4.0 (org.makagiga.commons.swing package)
 */
public final class MText {

	// public

	static final int MARGIN = 2; // package

	/**
	 * Cut action.
	 */
	public static final String CUT = "org.makagiga.commons.swing.MText.cut";

	/**
	 * Copy action.
	 */
	public static final String COPY = "org.makagiga.commons.swing.MText.copy";

	/**
	 * Paste action.
	 */
	public static final String PASTE = "org.makagiga.commons.swing.MText.paste";

	/**
	 * Paste HTML action.
	 *
	 * @since 3.8.11
	 */
	public static final String PASTE_HTML = "org.makagiga.commons.swing.MText.pasteHTML";

	/**
	 * Clear action.
	 */
	public static final String CLEAR = "org.makagiga.commons.swing.MText.clear";

	/**
	 * Delete action.
	 */
	public static final String DELETE = "org.makagiga.commons.swing.MText.delete";

	/**
	 * Select all action.
	 */
	public static final String SELECT_ALL = "org.makagiga.commons.swing.MText.selectAll";

	/**
	 * Spelling action.
	 *
	 * @since 2.4
	 */
	public static final String SPELLING = "org.makagiga.commons.swing.MText.spelling";
	
	/**
	 * @since 2.0
	 */
	public static final String NO_GLOBAL_MENU = "org.makagiga.commons.swing.MText.noGlobalMenu";

	// private

	private static ClassLoader spellCheckerClassLoader;
	private static Handler handler;
	private static List<GlobalMenu> globalMenus = new MArrayList<>();
	private static SoftReference<SpellChecker> spellCheckerSoftCache;
	private static String spellCheckerClassName;
	private static final String AUTO_COMPLETION = "org.makagiga.commons.swing.MText.autoCompletion";
	private static final String AUTO_HIDE_TIMER = "org.makagiga.commons.swing.MText.autoHideTimer";
	private static final String MODIFIABLE = "org.makagiga.commons.swing.MText.modifiable";
	private static final String UNDO_MANAGER = "org.makagiga.commons.swing.MText.undoManager";
	private static final String USER_MENU = "org.makagiga.commons.swing.MText.userMenu";

	// public

	public static void addGlobalMenu(final GlobalMenu value) {
		globalMenus.add(value);
	}

	/**
	 * @since 3.8.4
	 */
	public static void removeGlobalMenu(final GlobalMenu value) {
		globalMenus.remove(value);
	}

	/**
	 * Installs common actions, enables drag and drop, and sets margin.
	 * @param textComponent A non-null text component
	 */
	public static void commonSetup(final JTextComponent textComponent) {
		if (!(textComponent instanceof JPasswordField))
			textComponent.setDragEnabled(Kiosk.actionDragDrop.get());
		setupMargin(textComponent);
		install(textComponent);
	}
	
	public static void copyAll(final JTextComponent textComponent) {
		if (textComponent == null)
			return;
		
		selectAll(textComponent);
		textComponent.copy();
	}

	/**
	 * Creates a new instance of the default spell checker.
	 * 
	 * @return @c null if the default spell checker is not available
	 * 
	 * @throws Exception If error
	 * 
	 * @since 2.0
	 */
	public synchronized static SpellChecker createDefaultSpellChecker() throws Exception {
		return TK.newInstance(spellCheckerClassName, spellCheckerClassLoader);
	}

	/**
	 * Creates and returns menu for text component (cut, copy, paste, etc).
	 * @param textComponent A non-null text component
	 */
	public static MMenu createMenu(final JTextComponent textComponent) {
		updateActions(textComponent);

		MMenu menu = new MMenu();

		if (!(textComponent instanceof JPasswordField)) {
			boolean multiline = isMultiline(textComponent);

			if (textComponent.isEditable()) {
				MUndoManager undoManager = getUndoManager(textComponent);
				if (undoManager != null) {
					undoManager.updateMenu(menu);
					menu.addSeparator();
				}
				menu.add(getAction(textComponent, CUT));
				menu.add(getAction(textComponent, COPY));
				menu.add(getAction(textComponent, PASTE));
				if (multiline)
					menu.add(getAction(textComponent, PASTE_HTML));
				menu.add(getAction(textComponent, DELETE));
				if (OS.isKDE())
					menu.add(getAction(textComponent, CLEAR));
				menu.addSeparator();
			}
			else {
				menu.add(getAction(textComponent, COPY));
			}
			menu.add(getAction(textComponent, SELECT_ALL));

			if (textComponent.isEditable()) {
				// spelling
				if (getDefaultSpellChecker() != null)
					menu.add(getAction(textComponent, SPELLING));

				MMenu moreMenu = new MMenu(_("More"));
				moreMenu.setPopupMenuVerticalAlignment(MMenu.CENTER);

				moreMenu.addTitle(_("Insert Symbol"));
				moreMenu.add(new InsertSymbolAction(textComponent, '\u20ac', "Euro Sign"));
				moreMenu.add(new InsertSymbolAction(textComponent, '\u00a9', "Copyright Sign"));
				moreMenu.add(new InsertSymbolAction(textComponent, '\u00ae', "Registered Sign"));
				moreMenu.add(new InsertSymbolAction(textComponent, '\u2122', "Trade Mark Sign"));

				if (multiline) {
					// CREDITS:
					// http://www.howtogeek.com/howto/28947/use-a-unicode-text-trick-to-make-lists-with-checkboxes/
					// http://grzglo.jogger.pl/2010/10/05/lista-zadan-ze-znakami-specjalnymi-w-notatniku/
					moreMenu.add(new InsertSymbolAction(textComponent, '\u2610', "Ballot box"));
					moreMenu.add(new InsertSymbolAction(textComponent, '\u2611', "Ballot box with check"));
					moreMenu.add(new InsertSymbolAction(textComponent, '\u2612', "Ballot box with X"));

					moreMenu.addTitle(_("Insert Date/Time"));
					moreMenu.add(new InsertDateAction(textComponent, MDate.MEDIUM, MDate.SHORT));
					moreMenu.add(new InsertDateAction(textComponent, MDate.FULL, -1));
					moreMenu.add(new InsertDateAction(textComponent, "EEE MMM d yyyy"));
					moreMenu.add(new InsertDateAction(textComponent, "EEE MMM d HH:mm yyyy"));
					moreMenu.add(new InsertDateAction(textComponent, MDate.MEDIUM, -1));
					moreMenu.add(new InsertDateAction(textComponent, -1, MDate.SHORT));
					moreMenu.add(new InsertDateAction(textComponent, MDate.DEFAULT_DATE_TIME_FORMAT));
				}

				moreMenu.addSeparator();

				// input method menu
				moreMenu.add(new InputMethodAction(textComponent));
				// auto completion history
				if (getAutoCompletion(textComponent) != null)
					moreMenu.add(new AutoCompletion.HistoryAction(textComponent));
				menu.add(moreMenu);
			}
		}

		// add global menus
		if (
			Kiosk.textExtraMenu.get() &&
			!globalMenus.isEmpty() &&
			!UI.getClientProperty(textComponent, NO_GLOBAL_MENU, false)
		) {
			if (!menu.isEmpty())
				menu.addSeparator();
			for (GlobalMenu i : globalMenus)
				i.onGlobalMenu(textComponent, menu);
		}

		// add user menu
		UserMenu<JTextComponent> userMenu = UI.getClientProperty(textComponent, USER_MENU, null);
		if (userMenu != null)
			userMenu.onUserMenu(textComponent, menu);

		return menu;
	}

	/**
	 * @since 3.4
	 */
	public static void deinstallKit(final JEditorPane editorPane) {
		EditorKit kit = editorPane.getEditorKit();
		if (kit != null)
			kit.deinstall(editorPane);
	}

	/**
	 * @since 3.8.2
	 */
	public static boolean disableCaretUpdate(final JTextComponent textComponent) {
		Caret c = textComponent.getCaret();
		if (c instanceof DefaultCaret) {
			DefaultCaret.class.cast(c).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
			
			return true;
		}

		return false;
	}

	/**
	 * Returns a text component action.
	 * @param textComponent A non-null text component
	 * @param name An action name
	 *
	 * @since 2.0
	 */
	public static MAction getAction(final JTextComponent textComponent, final String name) {
		return UI.getClientProperty(textComponent, name, null);
	}
	
	public static AutoCompletion getAutoCompletion(final JTextComponent textComponent) {
		if (textComponent == null)
			return null;
		
		if (textComponent instanceof JPasswordField)
			return null;

		return UI.getClientProperty(textComponent, AUTO_COMPLETION, null);
	}
	
	/**
	 * @since 4.0
	 */
	public static String getAutoCompletionID(final JTextComponent textComponent) {
		AutoCompletion ac = getAutoCompletion(textComponent);
		
		return (ac == null) ? null : ac.getID();
	}
	
	/**
	 * @since 2.0
	 */
	public synchronized static String getDefaultSpellChecker() { return spellCheckerClassName; }

	/**
	 * @since 4.0
	 */
	public synchronized static void setDefaultSpellChecker(final String className, final ClassLoader loader) {
		MLogger.debug("core", "Setting default spell checker to \"%s\"...", className);
		spellCheckerClassName = className;
		spellCheckerClassLoader = loader;
	}

	/**
	 * @since 2.0
	 */
	public static Action getEnterPressAction(final JTextField textField) {
		Object result = textField.getInputMap(JTextField.WHEN_FOCUSED)
			.get(KeyStroke.getKeyStroke(VK_ENTER, 0));
		
		return (result instanceof Action) ? (Action)result : null;
	}

	/**
	 * @since 1.2
	 */
	public static void setEnterPressAction(final JTextField textField, final Action action) {
		if (action == null) {
			textField.getInputMap(JTextField.WHEN_FOCUSED)
				.remove(KeyStroke.getKeyStroke(VK_ENTER, 0));
		}
		else {
			if (MAction.getValue(action, MAction.NAME, null) == null)
				action.putValue(Action.NAME, textField.getClass().getName() + "-enterPressAction");
			MAction.connect(textField, JTextField.WHEN_FOCUSED, VK_ENTER, action);
		}
	}

	public static MMenu getMenu(final JTextComponent textComponent, final String name) {
		return UI.getClientProperty(textComponent, name, null);
	}

	/**
	 * @since 4.4
	 */
	public static int getModelPosition(final JTextComponent textComponent) {
		JViewport textViewport = MScrollPane.getViewport(textComponent);

		if (textViewport != null)
			return textComponent.viewToModel(textViewport.getViewPosition());

		return -1;
	}

	/**
	 * @since 4.4
	 */
	public static void setModelPosition(final JTextComponent textComponent, final int modelPosition) throws BadLocationException {
		Rectangle r = textComponent.modelToView(modelPosition);
		if (r != null) {
			r.height = textComponent.getVisibleRect().height;
			textComponent.scrollRectToVisible(r);
		}
	}

	public static String getPlainText(final JTextComponent textComponent) {
		try {
			Document doc = textComponent.getDocument();

			return doc.getText(0, doc.getLength());
		}
		catch (Exception exception) {
			MLogger.exception(exception);

			return null;
		}
	}

	/**
	 * @since 4.0
	 */
	public static String getText(final Document doc, final Element element) {
		if ((doc == null) || (element == null))
			return null;

		try {
			return doc.getText(element.getStartOffset(), element.getEndOffset() - element.getStartOffset());
		}
		catch (BadLocationException exception) {
			return null;
		}
	}

	/**
	 * @since 2.0
	 */
	public static MUndoManager getUndoManager(final JTextComponent textComponent) {
		return UI.getClientProperty(textComponent, UNDO_MANAGER, null);
	}

	/**
	 * @since 3.8.2
	 */
	public static boolean goTo(final JTextComponent textComponent, final int line, final int column) {
		textComponent.requestFocusInWindow();

		if ((line == -1) && (column == -1))
			return false;

		String text = textComponent.getText();

		int currentLine = 1;
		int oldLine = -1;
		for (int i = 0; i < text.length(); i++) {
			if (i == textComponent.getCaretPosition()) {
				oldLine = currentLine;

				break; // for
			}
			if (text.charAt(i) == '\n')
				currentLine++;
		}

		currentLine = 1;
		for (int i = 0; i < text.length(); i++) {
			if (currentLine == line) {
				if (column == -1) {
					// do not change column if same line
					if ((oldLine == -1) || (line != oldLine))
						textComponent.setCaretPosition(i);
				}
				else {
					textComponent.setCaretPosition(i + column - 1);
				}

				return true;
			}
			if (text.charAt(i) == '\n')
				currentLine++;
		}

		return false;
	}

	public static boolean hasSelection(final JTextComponent textComponent) {
		int i = textComponent.getCaretPosition();

		return
			(textComponent.getSelectionEnd() != i) ||
			(textComponent.getSelectionStart() != i);
	}

	/**
	 * Inserts @p string at the current caret position.
	 *
	 * @return @c false If insert failed
	 *
	 * @throws NullPointerException If @p textComponent is @c null
	 *
	 * @since 3.4
	 */
	public static boolean insertString(final JTextComponent textComponent, final String string) {
		return insertString(textComponent, string, textComponent.getCaretPosition());
	}

	/**
	 * Inserts @p string at the @p position.
	 *
	 * @return @c false If insert failed
	 *
	 * @throws NullPointerException If @p textComponent is @c null
	 *
	 * @since 3.4
	 */
	public static boolean insertString(final JTextComponent textComponent, final String string, final int position) {
		try {
			textComponent.getDocument().insertString(position, string, null);

			return true;
		}
		catch (BadLocationException exception) {
			MLogger.exception(exception);

			return false;
		}
	}

	/**
	 * Installs actions and undo manager.
	 * @param textComponent A non-null text component
	 */
	public static void install(final JTextComponent textComponent) {
		if (handler == null)
			handler = new Handler();

		if (isMultiline(textComponent))
			textComponent.putClientProperty("caretWidth", 2);

		if (UI.isQuaqua()) {
			textComponent.putClientProperty("Quaqua.TextComponent.autoSelect", false);
			textComponent.putClientProperty("Quaqua.TextComponent.showPopup", false);
		}
		textComponent.addCaretListener(handler);
		textComponent.addKeyListener(handler);
		textComponent.addMouseListener(handler);
		textComponent.addMouseMotionListener(handler);

		if (textComponent instanceof JPasswordField)
			return; // install Handler only

		installUndoManager(textComponent);

		// cut
		MAction cutAction = new StaticAction(textComponent, MActionInfo.CUT) {
			@Override
			public void onAction() {
				getTextComponent().cut();
			}
		};
		cutAction.setHTMLHelp(_("Cuts the selected text to the clipboard."));

		// copy
		MAction copyAction = new StaticAction(textComponent, MActionInfo.COPY) {
			@Override
			public void onAction() {
				getTextComponent().copy();
			}
		};
		copyAction.setHTMLHelp(_("Copies the selected text to the clipboard."));

		// paste
		String pasteHelp = _("Pastes from the clipboard.");
		MAction pasteAction = new StaticAction(textComponent, MActionInfo.PASTE) {
			@Override
			public void onAction() {
				getTextComponent().paste();
			}
		};
		pasteAction.setHTMLHelp(pasteHelp);

		// paste HTML
		MAction pasteHTMLAction = new StaticAction(textComponent, _("Paste as HTML")) {
			@Override
			public void onAction() {
				MText.pasteHTML(this.getTextComponent());
			}
		};
		pasteHTMLAction.setHTMLHelp(pasteHelp);

		// clear
		MAction clearAction = new StaticAction(textComponent, MActionInfo.CLEAR_RIGHT) {
			@Override
			public void onAction() {
				getTextComponent().setText(null); // clear
			}
		};
		clearAction.setHTMLHelp(_("Clears all text."));

		// delete
		MAction deleteAction = new StaticAction(textComponent, MActionInfo.DELETE.noKeyStroke()) {
			@Override
			public void onAction() {
				getTextComponent().replaceSelection(null); // delete selected text
			}
		};
		deleteAction.setHTMLHelp(_("Deletes the selected text."));

		// select all
		MAction selectAllAction = new StaticAction(textComponent, MActionInfo.SELECT_ALL) {
			@Override
			public void onAction() {
				selectAll(getTextComponent());
			}
		};
		selectAllAction.setHTMLHelp(_("Selects all text."));
		
		// spell checker
		MAction spellingAction = new StaticAction(textComponent, _("Spelling")) {
			@Override
			public void onAction() {
				spelling(getTextComponent());
			}
		};
		spellingAction.setIconName("ui/checkspelling");

		// bind keyboard shortcuts
		if (textComponent instanceof JTextField)
			clearAction.connect(textComponent, JComponent.WHEN_FOCUSED, VK_U, MAction.getMenuMask());
		cutAction.connect(textComponent, JComponent.WHEN_FOCUSED, VK_DELETE, SHIFT_MASK);
		copyAction.connect(textComponent, JComponent.WHEN_FOCUSED, VK_INSERT, MAction.getMenuMask());
		pasteAction.connect(textComponent, JComponent.WHEN_FOCUSED, VK_INSERT, SHIFT_MASK);

		textComponent.putClientProperty(CUT, cutAction);
		textComponent.putClientProperty(COPY, copyAction);
		textComponent.putClientProperty(PASTE, pasteAction);
		textComponent.putClientProperty(PASTE_HTML, pasteHTMLAction);
		textComponent.putClientProperty(CLEAR, clearAction);
		textComponent.putClientProperty(DELETE, deleteAction);
		textComponent.putClientProperty(SELECT_ALL, selectAllAction);
		textComponent.putClientProperty(SPELLING, spellingAction);
		
		if (textComponent instanceof Modifiable)
			installModifiable(textComponent, (Modifiable)textComponent);
	}

	/**
	 * @throws IllegalArgumentException If @p id is invalid
	 */
	public static void installAutoCompletion(final JTextComponent textComponent, final String id) {
		if (textComponent == null)
			return;
		
		if (textComponent instanceof JPasswordField)
			throw new IllegalArgumentException("Cannot set text auto completion for password field");
		
		textComponent.putClientProperty(AUTO_COMPLETION, new AutoCompletion(textComponent, id));
	}

	/**
	 * Returns @c true for @c JEditorPane or @c JTextArea @p textComponent.
	 *
	 * @since 3.0
	 */
	public static boolean isMultiline(final JTextComponent textComponent) {
		return (textComponent instanceof JEditorPane) || (textComponent instanceof JTextArea);
	}

	/**
	 * @since 2.0
	 */
	public static void load(final JTextComponent textComponent, final File file) throws IOException {
		try (FS.BufferedFileInput input = new FS.BufferedFileInput(file)) {
			load(textComponent, input);
		}
	}

	/**
	 * @since 4.0
	 */
	public static void load(final JTextComponent textComponent, final Reader reader) throws IOException {
		try {
			uninstallUndoManager(textComponent);
			textComponent.read(reader, null);
		}
		finally {
			finishLoad(textComponent);
		}
	}

	/**
	 * @mg.warning
	 * The {@code input} stream will be automatically closed after load.
	 *
	 * @since 2.0
	 */
	public static void load(final JTextComponent textComponent, final InputStream input) throws IOException {
		try (Reader reader = FS.getUTF8Reader(input)) {
			uninstallUndoManager(textComponent);
			textComponent.read(reader, null);
		}
		finally {
			finishLoad(textComponent);
		}
	}

	/**
	 * @since 2.0
	 */
	public static void loadHTML(final JEditorPane textComponent, final InputStream input) throws IOException {
		try {
			uninstallUndoManager(textComponent);
			textComponent.read(input, textComponent.getDocument());
		}
		finally {
			finishLoad(textComponent);
		}
	}

	public static void makeDefault(final JTextComponent textComponent) {
		if (textComponent == null)
			return;
		
		selectAll(textComponent);
		
		if (!UI.animations.get())
			return;
		
		if (UI.isGTK() || UI.isRetro())
			return; // unsupported
			
		if (textComponent instanceof JTextField)
			Blend.animateBackground(textComponent, UI.getSelectionBackground(), null);
	}

	/**
	 * @since 2.0
	 */
	public static void save(final JTextComponent textComponent, final File file) throws IOException {
		try (FS.BufferedFileOutput output = new FS.BufferedFileOutput(file)) {
			save(textComponent, output);
		}
	}

	/**
	 * @mg.warning
	 * The {@code output} stream will be automatically closed after save.
	 *
	 * @since 2.0
	 */
	public static void save(final JTextComponent textComponent, final OutputStream output) throws IOException {
		try (FS.TextWriter writer = FS.getUTF8Writer(output)) {
			if (textComponent.getDocument().getLength() == 0) // make sure the file is not empty
				writer.println();
			else
				textComponent.write(writer);
		}
	}

	@SuppressWarnings("deprecation")
	public static void saveAutoCompletion(final JTextComponent textComponent) {
		if (textComponent == null)
			return;
		
		if (textComponent instanceof JPasswordField)
			return;
		
		AutoCompletion autoCompletion = getAutoCompletion(textComponent);
		if (autoCompletion != null) {
			String text = textComponent.getText();
			if (!TK.isEmpty(text)) {
				autoCompletion.addItem(text);
				autoCompletion.save();
			}
		}
	}

	public static void selectAll(final JTextComponent textComponent) {
		textComponent.requestFocusInWindow();
		textComponent.selectAll();
	}
	
	/**
	 * Limits the maximum number of characters that can be typed by user.
	 * 
	 * @mg.note This will replace existing document filter.
	 * 
	 * @param textComponent the text component where to apply the limit
	 * @param maximumLength the maximum text length; -1 = no limit
	 * 
	 * @throws IllegalArgumentException If @p maximumLength is less than -1
	 * @throws NullPointerException If @p textComponent is @c null
	 * 
	 * @since 3.0
	 */
	public static void setMaximumLength(final JTextComponent textComponent, final int maximumLength) {
		Document d = textComponent.getDocument();
		if (d instanceof AbstractDocument) {
			AbstractDocument ad = (AbstractDocument)d;
			if (maximumLength == -1) {
				if (ad.getDocumentFilter() instanceof MaximumLengthDocumentFilter)
					ad.setDocumentFilter(null);
			}
			else {
				ad.setDocumentFilter(new MaximumLengthDocumentFilter(maximumLength));
			}
		}
	}
	
	/**
	 * @since 4.0
	 */
	public static void setStandardColors(final JTextComponent textComponent) {
		textComponent.setBackground(MColor.WHITE);
		textComponent.setForeground(MColor.BLACK);
		textComponent.setCaretColor(MColor.BLACK);
		textComponent.setSelectionColor(MColor.getBrighter(MColor.SKY_BLUE));
		textComponent.setSelectedTextColor(MColor.BLACK);
	}

	public static void setText(final JTextComponent textComponent, final String value) {
		AutoCompletion autoCompletion = getAutoCompletion(textComponent);
		if (autoCompletion == null) {
			textComponent.setText(value);
		}
		else {
			boolean enabled = autoCompletion.enabled.get();
			try {
				autoCompletion.enabled.no();
				textComponent.setText(value);
			}
			finally {
				autoCompletion.enabled.set(enabled);
			}
		}
	}

	/**
	 * Sets margin size of @p textComponent to @ref MARGIN.
	 *
	 * @since 1.2
	 */
	public static void setupMargin(final JTextComponent textComponent) {
		if (!UI.isA03() && !UI.isQuaqua() && !UI.isSubstance() && !UI.isSynth() && !UI.isWindows())
			textComponent.setMargin(UI.createInsets(MARGIN));
	}
	
	/**
	 * @since 4.4
	 */
	public static Font setupMonospacedFont(final JTextComponent textComponent) {
		Font oldFont = UI.getFont(textComponent);
		Font newFont = UI.createMonospacedFont(oldFont.getStyle(), oldFont.getSize() + 1);
		textComponent.setFont(newFont);
		
		return newFont;
	}

	public static void setUserMenu(final JTextComponent textComponent, final UserMenu<?> value) {
		textComponent.putClientProperty(USER_MENU, value);
	}
	
	/**
	 * @since 2.4
	 */
	public static void spelling(final JTextComponent textComponent) {
		SpellChecker sc = null;
		try {
			synchronized (MText.class) {
				if (spellCheckerSoftCache != null)
					sc = spellCheckerSoftCache.get();
				if (sc == null) {
					sc = createDefaultSpellChecker();
					spellCheckerSoftCache = new SoftReference<>(sc);
				}
			}
		}
		catch (ThreadDeath error) {
			throw error;
		}
		catch (Throwable throwable) {
			MLogger.exception(throwable);
			MStatusBar.error(_("Could not initialize Spell Checker plugin"));
			
			return;
		}

		try {
			sc.setProperty(SpellChecker.INTERACTIVE_PROPERTY, true);
			sc.setProperty(SpellChecker.LOCALE_PROPERTY, textComponent.getLocale());
			sc.check(textComponent);
		}
		catch (ThreadDeath error) {
			throw error;
		}
		catch (Throwable throwable) {
			MMessage.error(null, throwable);
		}
	}

	/**
	 * @since 3.4
	 */
	public static void uninstallUndoManager(final JTextComponent textComponent) {
		MUndoManager undoManager = getUndoManager(textComponent);
		if (undoManager != null) {
			undoManager.getUndoAction().disconnect(textComponent, JComponent.WHEN_FOCUSED);
			undoManager.getRedoAction().disconnect(textComponent, JComponent.WHEN_FOCUSED);
			textComponent.getDocument().removeUndoableEditListener(undoManager);
			undoManager.setOwner(null);
		}
	}

	/**
	 * @since 4.4
	 */
	public static void unselectAll(final JTextComponent textComponent) {
		textComponent.setSelectionStart(0);
		textComponent.setSelectionEnd(0);
	}

	public static void updateActions(final JTextComponent textComponent) {
		if (textComponent instanceof JPasswordField)
			return;

		boolean isEditable = textComponent.isEditable();
		boolean isEmpty = (textComponent.getDocument().getLength() == 0);
		
		MUndoManager undoManager = getUndoManager(textComponent);
		if (undoManager != null)
			undoManager.updateUndoRedoActions(isEditable);
		
		getAction(textComponent, CLEAR).setEnabled(isEditable && !isEmpty);
		getAction(textComponent, SELECT_ALL).setEnabled(!isEmpty);
		getAction(textComponent, SPELLING).setEnabled(isEditable && !isEmpty);
		updateSelectionActions(textComponent);
		getAction(textComponent, PASTE).setEnabled(isEditable);
		getAction(textComponent, PASTE_HTML).setEnabled(isEditable);
	}
	
	/**
	 * @since 2.0
	 */
	public static void updateToolBar(final JTextComponent textComponent, final MToolBar toolBar) {
		MUndoManager undoManager = getUndoManager(textComponent);
		if (undoManager != null) {
			undoManager.updateToolBar(toolBar);
			toolBar.addSeparator();
		}
		toolBar.add(getAction(textComponent, CUT));
		toolBar.add(getAction(textComponent, COPY), MToolBar.SHOW_TEXT);
		toolBar.add(getAction(textComponent, PASTE), MToolBar.SHOW_TEXT);
		
		if (getDefaultSpellChecker() != null) {
			toolBar.addSeparator();
			toolBar.add(getAction(textComponent, SPELLING));
		}
	}

	// private
	
	@Uninstantiable
	private MText() { }

	private static void finishLoad(final JTextComponent textComponent) {
		// reinstall after read
		if (textComponent instanceof Modifiable)
			installModifiable(textComponent, (Modifiable)textComponent);
		installUndoManager(textComponent);

		// mark as unmodified
		if (textComponent instanceof Modifiable)
			Modifiable.class.cast(textComponent).setModified(false, null);
	}

	private static void installModifiable(final JTextComponent textComponent, final Modifiable modifiable) {
		DocumentListener documentListener = new StaticModifiableDocumentHandler(modifiable);
		textComponent.getDocument().addDocumentListener(documentListener);
		if (textComponent != modifiable)
			textComponent.putClientProperty(MODIFIABLE, modifiable);
	}

	/**
	 * Installs undo manager.
	 * @param textComponent A non-null text component
	 */
	private static void installUndoManager(final JTextComponent textComponent) {
		if (textComponent instanceof JPasswordField)
			return;
	
		// uninstall previous undo manager
		uninstallUndoManager(textComponent);
		
		MUndoManager undoManager = new MUndoManager(textComponent) {
			@Override
			public void updateUserActions() {
				JComponent c = this.getOwner();
				if (c instanceof JTextComponent)
					MText.updateActions((JTextComponent)c);
			}
		};
		textComponent.getDocument().addUndoableEditListener(undoManager);
		textComponent.putClientProperty(UNDO_MANAGER, undoManager);
		undoManager.getUndoAction().connect(textComponent, JComponent.WHEN_FOCUSED);
		undoManager.getRedoAction().connect(textComponent, JComponent.WHEN_FOCUSED);
	}

	private static void pasteHTML(final JTextComponent textComponent) {
		Clipboard clipboard = MClipboard.getDefault();
		DataFlavor[] flavors = MDataTransfer.getAvailableDataFlavors(clipboard);
		DataFlavor htmlFlavor = MDataTransfer.findBestHTMLFlavor(flavors, false);
		if (htmlFlavor != null) {
			try {
				String html = MDataTransfer.getHTML(clipboard, htmlFlavor);
				MText.insertString(textComponent, html);
			}
			catch (IOException | UnsupportedFlavorException exception) {
				MLogger.exception(exception);
				textComponent.paste(); // paste plain text if no HTML
			}
		}
		else {
			textComponent.paste(); // paste plain text if no HTML
		}
	}

	private static void setCursorVisible(final JTextComponent textComponent, final boolean visible) {
		if ((!textComponent.isEditable() || !textComponent.isEnabled()) && !visible)
			return;

		MTimer autoHideTimer = UI.getClientProperty(textComponent, AUTO_HIDE_TIMER, null);
		if (visible) {
			if (autoHideTimer != null) {
				autoHideTimer.stop();
				textComponent.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
			}
		}
		else {
			Point p = textComponent.getMousePosition();
			
			if (p == null)
				return;
			
			int modelPos = textComponent.viewToModel(p);
			
			if (modelPos == -1)
				return;
			
			int currentPos = textComponent.getCaretPosition();
			try {
				int rowStart = Utilities.getRowStart(textComponent, currentPos);
				
				if (rowStart == -1)
					return;
				
				int rowEnd = Utilities.getRowEnd(textComponent, currentPos);

				if (rowEnd == -1)
					return;
			
				// hide mouse cursor only if it hovers the current row
				if ((modelPos < rowStart) || (modelPos > rowEnd))
					return;
			}
			catch (BadLocationException exception) {
				MLogger.exception(exception);
			}
		
			if (autoHideTimer == null) {
				autoHideTimer = new AutoHideTimer(textComponent);
				textComponent.putClientProperty(AUTO_HIDE_TIMER, autoHideTimer);
			}
			autoHideTimer.restart();
		}
	}

	private static void updateSelectionActions(final JTextComponent textComponent) {
		if (textComponent instanceof JPasswordField)
			return;

		boolean isEditable = textComponent.isEditable();
		boolean isSelection = hasSelection(textComponent);
		getAction(textComponent, CUT).setEnabled(isEditable && isSelection);
		getAction(textComponent, COPY).setEnabled(isSelection);
		getAction(textComponent, DELETE).setEnabled(isEditable && isSelection);
	}
	
	// public classes
	
	/**
	 * A set of common text component extensions.
	 */
	public static interface CommonExtensions {
		
		// public
		
		/**
		 * Sets text to @c null.
		 */
		public void clear();
		
		/**
		 * Returns @c true if no text.
		 */
		public boolean isEmpty();
		
	}

	public static interface GlobalMenu {

		// public

		public void onGlobalMenu(final JTextComponent textComponent, final MMenu menu);

	}

	/**
	 * @since 4.2
	 */
	public static class InsertAction extends MDataAction.Weak<JTextComponent> {
	
		// private
		
		private final String stringToInsert;

		// public

		public InsertAction(final JTextComponent textComponent) {
			this(textComponent, null, null);
		}

		public InsertAction(final JTextComponent textComponent, final String name) {
			this(textComponent, name, null);
		}

		public InsertAction(final JTextComponent textComponent, final String name, final String stringToInsert) {
			super(textComponent, Objects.toString(name, stringToInsert));
			this.stringToInsert = stringToInsert;
		}
		
		public String getString() { return stringToInsert; }
		
		@Override
		public void onAction() {
			String s = getString();
			if (s != null)
				insertText(s);
		}
		
		// protected

		protected void insertText(final String text) {
			JTextComponent tf = get();
			if (tf != null) {
				MText.insertString(tf, text);
			}
			else {
				showErrorMessage();
			}
		}

	}

	/**
	 * @since 2.0
	 */
	public interface Modifiable {

		// public

		/**
		 * Returns @c true if text component was modified.
		 */
		public boolean isModified();

		/**
		 * Sets the <i>modified</i> state of this text component.
		 *
		 * @param value @c true if text component was modified
		 * @param event An event that caused the modification (may be @c null)
		 */
		public void setModified(final boolean value, final Object event);

	}
	
	/**
	 * An interface for spell checking (based on the Sonnet API).
	 * 
	 * @since 2.0, 4.0 (abstract class instead of interface)
	 */
	public static abstract class SpellChecker {
		
		// public

		/**
		 * @since 2.4
		 */
		public static final String INTERACTIVE_PROPERTY = "interactive";

		/**
		 * @since 2.4
		 */
		public static final String LOCALE_PROPERTY = "locale";
		
		// public
		
		public abstract void addToPersonal(final String word);
		
		public abstract void addToSession(final String word);
		
		public abstract void check(final JTextComponent textComponent);
		
		public abstract Object getProperty(final String name);
		
		public abstract void setProperty(final String name, final Object value);
		
		public abstract void install(final JTextComponent textComponent);
		
		public abstract void uninstall(final JTextComponent textComponent);
		
		public abstract boolean isCorrect(final String word);
		
		public abstract List<String> suggest(final String word);
		
		public abstract void storeReplacement(final String bad, final String good);
		
	}

	/**
	 * A set of common text component extensions.
	 */
	public static interface TextFieldExtensions extends CommonExtensions {
		
		// public

		/**
		 * Requests focus in window, selects all text,
		 * and displays the visual feedback.
		 */
		public void makeDefault();
		
		// text completion
		
		/**
		 * Returns the auto completion ID associated with this text field.
		 *
		 * @mg.warning
		 * Since version 4.0 this method returns {@code String} instead of {@code org.makagiga.commons.autocompletion.AutoCompletion}.
		 *
		 * @see MText#getAutoCompletion(javax.swing.text.JTextComponent)
		 * @see MText#getAutoCompletionID(javax.swing.text.JTextComponent)
		 */
		public String getAutoCompletion();

		/**
		 * Saves the auto completion items to the file.
		 */
		public void saveAutoCompletion();

		/**
		 * Installs auto completion object in this text field.
		 * @param id An auto completion ID (e.g. "foldername")
		 * @throws IllegalArgumentException If @p id is invalid
		 */
		public void setAutoCompletion(final String id);
	
		/**
		 * @since 2.0
		 */
		public String getText();
		
		/**
		 * @since 2.0
		 */
		public void setText(final String value);

	}

	public static interface UserMenu<T extends JTextComponent> {

		// public

		public void onUserMenu(final T textComponent, final MMenu menu);

	}
	
	// private classes

	private static final class AutoHideTimer extends MTimer {

		// private

		private final WeakReference<JTextComponent> textComponentRef;

		// protected

		@Override
		protected boolean onTimeout() {
			JTextComponent tf = textComponentRef.get();
			if (tf != null)
				tf.setCursor(UI.getInvisibleCursor());

			return false;
		}

		// private

		private AutoHideTimer(final JTextComponent textComponent) {
			super(1000);
			textComponentRef = new WeakReference<>(textComponent);
		}

	}

	private static final class Handler extends MMouseAdapter
	implements
		CaretListener,
		KeyListener
	{
		
		// public

		@Override
		public void mouseEntered(final MouseEvent e) {
			JTextComponent textComponent = (JTextComponent)e.getSource();
			setCursorVisible(textComponent, true);
		}

		@Override
		public void mouseExited(final MouseEvent e) {
			JTextComponent textComponent = (JTextComponent)e.getSource();
			setCursorVisible(textComponent, true);
		}

		@Override
		public void mouseMoved(final MouseEvent e) {
			JTextComponent textComponent = (JTextComponent)e.getSource();
			setCursorVisible(textComponent, true);
		}

		@Override
		public void mousePressed(final MouseEvent e) {
			super.mousePressed(e);
			JTextComponent textComponent = (JTextComponent)e.getSource();
			setCursorVisible(textComponent, true);
		}

		@Override
		public void popupTrigger(final MouseEvent e) {
			if (UI.mouseGestures.get() && e.isConsumed())
				return;

			JTextComponent textComponent = (JTextComponent)e.getSource();
			MMenu menu = createMenu(textComponent);
			if (!menu.isEmpty())
				menu.showPopup(e);
		}

		// CaretListener

		@Override
		public void caretUpdate(final CaretEvent e) {
			JTextComponent textComponent = (JTextComponent)e.getSource();
			updateSelectionActions(textComponent);
		}

		// KeyListener

		@Override
		public void keyPressed(final KeyEvent e) {
			JTextComponent textComponent = (JTextComponent)e.getSource();
			if (UI.isPopupTrigger(e)) {
				MMenu menu = createMenu(textComponent);
				if (!menu.isEmpty())
					menu.showPopup(textComponent);
			}
			else {
				// HACK: scroll non-editable text component
				if ((e.getModifiers() == 0) && !textComponent.isEditable() && MText.isMultiline(textComponent)) {
					boolean fired = false;
					switch (e.getKeyCode()) {
						case VK_LEFT:
							fired = fire(textComponent, "unitScrollLeft");
							break;
						case VK_RIGHT:
							fired = fire(textComponent, "unitScrollRight");
							break;
						case VK_UP:
							fired = fire(textComponent, "unitScrollUp");
							break;
						case VK_DOWN:
							fired = fire(textComponent, "unitScrollDown");
							break;
						case VK_PAGE_UP:
							fired = fire(textComponent, "scrollUp");
							break;
						case VK_PAGE_DOWN:
							fired = fire(textComponent, "scrollDown");
							break;
					}
					if (fired)
						e.consume();
				}
			}
		}

		@Override
		public void keyReleased(final KeyEvent e) { }

		@Override
		public void keyTyped(final KeyEvent e) {
			JTextComponent textComponent = (JTextComponent)e.getSource();
			setCursorVisible(textComponent, false);
		}
		
		// private
		
		private boolean fire(final JTextComponent c, final String action) {
			JScrollPane sp = MScrollPane.getScrollPane(c);
			if (sp != null) {
				MAction.fire(action, sp);
				
				return true;
			}
			
			return false;
		}

	}
	
	private static final class InputMethodAction extends StaticAction {

		// public
		
		@Override
		public void onAction() {
			JTextComponent tf = getTextComponent();
			
			if (tf == null)
				return;
			
			InputContext context = tf.getInputContext();
			if (context != null) {
				try {
					int numIM = 0;
					for (InputMethodDescriptor i : ServiceLoader.loadInstalled(InputMethodDescriptor.class))
						numIM++;
					if (numIM == 0) {
						MStatusBar.info(_("No input method installed"));

						return;
					}

					try {
						// HACK: sun.awt.im.InputMethodManager
						Class<?> inputMethodManagerClass = Class.forName("sun.awt.im.InputMethodManager");

						Method getInstanceMethod = inputMethodManagerClass.getMethod("getInstance");
						Object inputMethodManagerObject = getInstanceMethod.invoke(null);

						Method notifyChangeRequestByHotKeyMethod = inputMethodManagerClass.getMethod("notifyChangeRequestByHotKey", Component.class);
						notifyChangeRequestByHotKeyMethod.invoke(inputMethodManagerObject, tf);
					}
					catch (ThreadDeath error) {
						throw error;
					}
					catch (Throwable throwable) { // catch all errors and exceptions
						MMessage.error(null, throwable);
					}
				}
				catch (Exception exception) {
					MMessage.error(null, exception);
				}
			}
		}
		
		// private
		
		private InputMethodAction(final JTextComponent textComponent) {
			super(textComponent, _("Select Input Method..."));
		}
	
	}

	private static final class InsertDateAction extends InsertAction {
		
		// private

		private int dateStyle;
		private int timeStyle;
		private String format;
		private static final String HELP = _("Insert Date/Time");
		
		// public
		
		public InsertDateAction(final JTextComponent textComponent, final int dateStyle, final int timeStyle) {
			super(textComponent);
			this.dateStyle = dateStyle;
			this.timeStyle = timeStyle;
			setHTMLHelp(HELP);
			setName(format());
		}

		public InsertDateAction(final JTextComponent textComponent, final String format) {
			super(textComponent);
			this.format = format;
			setHTMLHelp(HELP);
			setName(format());
		}
		
		@Override
		public String getString() {
			return format();
		}
		
		// private
		
		private String format() {
			MDate now = MDate.now();
			
			if (format != null)
				return now.format(format);

			// date and time
			if ((dateStyle != -1) && (timeStyle != -1))
				return now.formatDateTime(dateStyle, timeStyle);

			// date only
			if (dateStyle != -1)
				return MDate.formatDate(now, dateStyle);

			// time only
			return MDate.formatTime(now, timeStyle);
		}
		
	}

	private static final class InsertSymbolAction extends InsertAction {

		// private

		private InsertSymbolAction(final JTextComponent textComponent, final char symbol, final String toolTipText) {
			super(textComponent, null, Character.toString(symbol));
			setHTMLHelp(_("Insert: {0}", toolTipText));
		}

	}

	/**
	 * CREDITS: http://www.javaswing.net/max-length-on-jtextfield-using-documentfilter-a-step-by-step-tutorial.html
	 */
	private static final class MaximumLengthDocumentFilter extends DocumentFilter {
		
		// private
		
		private final int maximumLength;
		
		// public

		@Override
		public void insertString(final FilterBypass fb, final int offset, final String string, final AttributeSet attr) throws BadLocationException {
			if (fb.getDocument().getLength() + string.length() <= maximumLength)
				super.insertString(fb, offset, string, attr);
			else
				TK.beep();
		}

		@Override
		public void replace(final FilterBypass fb, final int offset, final int length, final String text, final AttributeSet attrs) throws BadLocationException {
			if (fb.getDocument().getLength() + ((text == null) ? 0 : text.length()) - length <= maximumLength)
				super.replace(fb, offset, length, text, attrs);
			else
				TK.beep();
		}
		
		// private
		
		private MaximumLengthDocumentFilter(final int maximumLength) {
			if (maximumLength < 0)
				throw new IllegalArgumentException();
			
			this.maximumLength = maximumLength;
		}
		
	}

	private static abstract class StaticAction extends MDataAction.Weak<JTextComponent> {

		// protected

		protected StaticAction(final JTextComponent textComponent, final MActionInfo info) {
			super(textComponent, info);
		}

		protected StaticAction(final JTextComponent textComponent, final String name) {
			super(textComponent, name);
		}

		protected JTextComponent getTextComponent() {
			return get();
		}

	}

	private static final class StaticModifiableDocumentHandler extends MDocumentAdapter<JTextComponent> {

		// private

		private final WeakReference<Modifiable> modifiableRef;

		// protected

		@Override
		protected void onChange(final DocumentEvent e) {
			Modifiable modifiable = modifiableRef.get();
			if (modifiable != null)
				modifiable.setModified(true, e);
		}

		// private

		public StaticModifiableDocumentHandler(final Modifiable modifiable) {
			modifiableRef = new WeakReference<>(modifiable);
		}

	}

}
