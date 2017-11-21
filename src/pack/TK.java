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

package org.makagiga.commons;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.makagiga.commons.annotation.Obsolete;
import org.makagiga.commons.annotation.Uninstantiable;

/** Low-level tool kit. */
public final class TK {
	
	// public
	
	/**
	 * An empty @c byte array.
	 * 
	 * @since 3.0
	 */
	public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

	/**
	 * An empty @c ChangeListener array.
	 * 
	 * @since 3.0
	 */
	public static final ChangeListener[] EMPTY_CHANGE_LISTENER_ARRAY = new ChangeListener[0];

	/**
	 * An empty @c char array.
	 * 
	 * @since 3.0
	 */
	public static final char[] EMPTY_CHAR_ARRAY = new char[0];

	/**
	 * An empty {@code int} array.
	 *
	 * @since 4.2
	 */
	public static final int[] EMPTY_INT_ARRAY = new int[0];

	/**
	 * An empty @c Object array.
	 * 
	 * @since 3.0
	 */
	public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

	/**
	 * An empty @c PropertyChangeListener array.
	 * 
	 * @since 3.0
	 */
	public static final PropertyChangeListener[] EMPTY_PROPERTY_CHANGE_LISTENER_ARRAY = new PropertyChangeListener[0];

	/**
	 * An empty @c String array.
	 * 
	 * @since 3.0
	 */
	public static final String[] EMPTY_STRING_ARRAY = new String[0];

	/**
	 * Escape all <code>*</code> characters ("*" -> "%2A").
	 *
	 * @see #escapeURL(String, int)
	 *
	 * @since 4.0
	 */
	public static final int ESCAPE_ASTERISK = 1;

	/**
	 * Escape all space (' ') characters (" " -> "+" -> "%20").
	 *
	 * @see #escapeURL(String, int)
	 */
	public static final int ESCAPE_SPACE = 1 << 1;
	
	/**
	 * Automatically trim the {@code String} result.
	 *
	 * @since 4.2
	 */
	public static final int SPLIT_PAIR_TRIM = 1;
	
	/**
	 * Return {@code null} to indicate parse error (e.g. missing {@code separator}).
	 *
	 * @since 4.2
	 */
	public static final int SPLIT_PAIR_NULL_ERROR = 1 << 1;

	/**
	 * A workaround for <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4513622">Bug #4513622</a>.
	 * It simply creates a new {@code String} copy without unwanted {@code char[]} data.
	 *
	 * @since 4.2
	 */
	public static final int SPLIT_PAIR_NO_MEMORY_LEAK = 1 << 2;

	/**
	 * @since 4.4
	 */
	public static final int TEXT_SEARCH_PATTERN_CASE_SENSITIVE = 1;

	// private

	private static Iterable<Object> EMPTY_ITERABLE = new Iterable<Object>() {
		@Override
		public Iterator<Object> iterator() {
			return Collections.emptyIterator();
		}
	};
	private static Pattern validateIDPattern = Pattern.compile("(\\w|\\-)+");

	// public

	/**
	 * Emits a system "beep" notification/sound.
	 * This feature depends on the current operating system configuration.
	 */
	public static void beep() {
		Toolkit.getDefaultToolkit().beep();
	}

	/**
	 * Returns a non-{@code null} error message from the {@code throwable}.
	 * The result includes exception class name, localized message,
	 * and all other exceptions that caused this error.
	 *
	 * @param throwable the throwable
	 *
	 * @throws NullPointerException If {@code throwable} is {@code null}
	 *
	 * @mg.output
	 * <pre>
	 * Example result for new IOException("Foo", new IllegalArgumentException("Bar")):
	 * "java.io.IOException: Foo\n
	 * - java.lang.IllegalArgumentException: Bar"
	 * </pre>
	 *
	 * @since 3.8.7
	 */
	public static String buildErrorMessage(final Throwable throwable) {
		Objects.requireNonNull(throwable);

		int level = 0;
		String lastCauseString = null;
		MStringBuilder s = new MStringBuilder();
		Throwable cause = throwable;
		do {
			String causeString = cause.toString();

			// skip duplicated message
			if (Objects.equals(lastCauseString, causeString))
				continue; // do

			lastCauseString = causeString;

			if (!s.isEmpty())
				s.n().n();

			if (level > 0)
				s.append("- ");
			s.append(identifierToDisplayString(cause.getClass().getSimpleName()));
			
			String message = cause.getLocalizedMessage();
			if (!isEmpty(message)) {
				s.append(": ");
				s.append(message);
			}
			s.append(" (")
			.append(cause.getClass().getName())
			.append(')');
			level++;
		} while ((cause = cause.getCause()) != null);

		return s.toString();
	}

	/**
	 * Returns a {@code String} with capitalized (upper case) the first character.
	 *
	 * @param s the {@code String} to capitalize
	 *
	 * @return unchanged {@code s} if {@code s} is {@code null} or empty
	 *
	 * @since 3.8.6
	 */
	public static String capitalize(final String s) {
		if (isEmpty(s))
			return s;

		int l = s.length();
		StringBuilder result = new StringBuilder(l);
		result.append(Character.toUpperCase(s.charAt(0)));
		if (l > 1)
			result.append(s.substring(1));

		return result.toString();
	}

	/**
	 * @since 3.0
	 *
	 * @deprecated As of 4.4, replaced by {@link java.util.Objects#requireNonNull(Object)}
	 */
	@Deprecated
	public static <T> T checkNull(final T object) {
		return Objects.requireNonNull(object);
	}

	/**
	 * @since 2.0
	 *
	 * @deprecated As of 4.4, replaced by {@link java.util.Objects#requireNonNull(Object, String)}
	 */
	@Deprecated
	public static <T> T checkNull(final T object, final String name) {
		return Objects.requireNonNull(object, name);
	}

	/**
	 * Throws @c IllegalArgumentException if @p string is @c null or empty.
	 * 
	 * @return @p string
	 * 
	 * @throws IllegalArgumentException if @p string is @c null or empty
	 * 
	 * @since 3.0
	 */
	public static String checkNullOrEmpty(final String string) {
		if (isEmpty(string))
			throw new IllegalArgumentException("String is null or empty");
		
		return string;
	}

	/**
	 * Throws @c IllegalArgumentException if @p string is @c null or empty.
	 * 
	 * @param string the string to test
	 * @param name the string name (can be @c null)
	 * 
	 * @return @p string
	 * 
	 * @throws IllegalArgumentException if @p string is @c null or empty
	 * 
	 * @since 2.2
	 */
	public static String checkNullOrEmpty(final String string, final String name) {
		if (isEmpty(string))
			throw new IllegalArgumentException("\"" + name + "\" string is null or empty");
		
		return string;
	}

	/**
	 * @since 2.0
	 *
	 * @deprecated Since 4.6
	 */
	@Deprecated
	public static <T extends Comparable<T>> int compare(final T o1, final T o2, final T defaultValue) {
		T item1 = (o1 == null) ? defaultValue : o1;
		T item2 = (o2 == null) ? defaultValue : o2;

		return item1.compareTo(item2);
	}

	/**
	 * @since 4.6
	 */
	public static int compareFirst(final boolean exp1, final boolean exp2) {
		if (exp1 && !exp2)
			return -1;

		if (exp2 && !exp1)
			return 1;

		return 0;
	}

	/**
	 * Returns a cloned shallow copy of {@code array}.
	 * The returned copy is equal to {@code array}.
	 * 
	 * @return {@code null} if {@code array} is {@code null}
	 * @return the original object if {@code array} is empty (since 4.0)
	 * 
	 * @since 2.0
	 */
	public static <T> T[] copyOf(final T[] array) {
		if (array == null)
			return null;
		
		if (array.length == 0)
			return array;
		
		return array.clone();
	}

	/**
	 * Returns a random and unique UUID surrounded with "{" and "}".
	 *
	 * @mg.output
	 * <pre>
	 * "{086d4839-705c-4c96-93aa-d96a0ce39ad2}" // random example
	 * </pre>
	 * 
	 * @since 2.4
	 */
	public static String createRandomUUID() {
		return "{" + UUID.randomUUID() + "}";
	}

	/**
	 * Deserializes and returns a named object.
	 * Returns @c null if object does not exist.
	 * 
	 * @param input the object input stream
	 * @param name the object name stored in the @p input stream
	 * 
	 * @throws ClassNotFoundException If error; see @c java.io.ObjectInputStream.readObject for more info
	 * @throws IOException If error; see @c java.io.ObjectInputStream.readObject for more info
	 * @throws NullPointerException If @p input is @c null
	 * @throws NullPointerException If @p name is @c null
	 * 
	 * @since 2.0
	 */
	@Obsolete
	@edu.umd.cs.findbugs.annotation.SuppressWarnings("CLI_CONSTANT_LIST_INDEX")
	public static Object deserialize(final ObjectInputStream input, final String name) throws ClassNotFoundException, IOException {
		Objects.requireNonNull(name);
		
		// based on the DefaultMutableTreeNode class
		Object[] data = (Object[])input.readObject();
		
		if ((data.length == 2) && data[0].equals(name))
			return data[1];
		
		return null;
	}

	/**
	 * Disposes the {@code o} object.
	 * 
	 * @return {@code null}
	 * 
	 * @see MDisposable#dispose()
	 *
	 * @since 4.0
	 */
	public static <T> T dispose(final MDisposable o) {
		if (o != null)
			o.dispose();

		return null;
	}
	
	/**
	 * @since 4.4
	 */
	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> emptyIterable() {
		return (Iterable<T>)EMPTY_ITERABLE;
	}

	/**
	 * Returns an escaped URL using UTF-8 encoding. (e.g. "\\ " ->  "%5C+")
	 * 
	 * @param value the URL to escape
	 *
	 * @throws NullPointerException If @p value is @c null
	 * 
	 * @see #escapeURL(String, int)
	 * @see #unescapeURL(String)
	 * @see java.net.URLEncoder
	 */
	public static String escapeURL(final String value) {
		return escapeURL(value, 0);
	}

	/**
	 * Returns an escaped URL using UTF-8 encoding. (e.g. "\\ " ->  "%5C+")
	 *
	 * @param value the URL to escape
	 * @param flags the flags (@ref ESCAPE_ASTERISK or @ref ESCAPE_SPACE)
	 *
	 * @throws NullPointerException If @p value is @c null
	 * 
	 * @see #escapeURL(String)
	 * @see #unescapeURL(String)
	 * @see java.net.URLEncoder
	 */
	public static String escapeURL(final String value, final int flags) {
		try {
			if (flags == 0)
				return URLEncoder.encode(value, "UTF-8");
		
			StringBuilder result = new StringBuilder(value.length() * 2);
			result.append(URLEncoder.encode(value, "UTF-8"));

			if ((flags & ESCAPE_ASTERISK) != 0)
				fastReplace(result, "*", "%2A");

			if ((flags & ESCAPE_SPACE) != 0)
				fastReplace(result, "+", "%20");

			return result.toString();
		}
		catch (UnsupportedEncodingException exception) {
			MLogger.exception(exception);

			return value;
		}
	}

	/**
	 * Escapes an XML code.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * TK.escapeXML("&lt;foo /&gt;"); // "&amp;lt;foo /&amp;gt;"
	 * </pre>
	 *
	 * @mg.note "\'" character is escaped as "&#039;"
	 *
	 * @return @p value if @p value is @c null or empty
	 *
	 * @param value the value to escape
	 */
	public static String escapeXML(final String value) {
		StringBuilder s = escapeXML(null, value);
		
		return (s == null) ? value : s.toString();
	}

	/**
	 * @since 4.0
	 */
	public static StringBuilder escapeXML(StringBuilder buf, final CharSequence value) {
		if (value == null)
			return buf;

		int len = value.length();

		if (len == 0)
			return buf;

		if (buf == null)
			buf = new StringBuilder(len * 2);
		for (int i = 0; i < len; i++) {
			char c = value.charAt(i);
			switch (c) {
				case '<':
					buf.append("&lt;");
					break;
				case '>':
					buf.append("&gt;");
					break;
				case '&':
					buf.append("&amp;");
					break;
				case '\'':
					// HACK: It seems that "&apos;" is sometimes handled incorectly
					// by the Swing's HTML renderer (?)
					buf.append("&#039;");
					break;
				case '\"':
					buf.append("&quot;");
					break;
				default:
					/*
					if (c > 128)
						buf.append("&#").append((int)c).append(';');
					else
					*/
					buf.append(c);
					break;
			}
		}

		return buf;
	}

	/**
	 * Returns a string of @p length filled with @p fill character.
	 *
	 * @throws IllegalArgumentException If @p length is less than zero
	 *
	 * @since 3.0
	 */
	public static String filler(final char fill, final int length) {
		if (length < 0)
			throw new IllegalArgumentException("\"length\" cannot be less than zero");

		char[] c = new char[length];
		Arrays.fill(c, fill);

		return new String(c);
	}

	/**
	 * Invokes @c actionPerformed with @c ActionEvent.ACTION_PERFORMED ID and empty command
	 * for each @p al element.
	 * Does nothing if @p al array is empty.
	 * 
	 * @param source the event source
	 * @param al the array of action listeners
	 * 
	 * @throws IllegalArgumentException If @p source is @c null
	 * @throws NullPointerException If @p al is @c null
	 * 
	 * @since 2.0
	 */
	public static void fireActionPerformed(final Object source, final ActionListener[] al) {
		if (al.length > 0) {
			ActionEvent ae = new ActionEvent(source, ActionEvent.ACTION_PERFORMED, "");
			for (ActionListener i : al)
				i.actionPerformed(ae);
		}
	}

	/**
	 * Invokes @c stateChanged for each @p cl element.
	 * Does nothing if @p cl array is empty.
	 * 
	 * @param source the event source
	 * @param cl the array of change listeners
	 * 
	 * @throws IllegalArgumentException If @p source is @c null
	 * @throws NullPointerException If @p cl is @c null
	 * 
	 * @since 2.0
	 */
	public static void fireStateChanged(final Object source, final ChangeListener[] cl) {
		if (cl.length > 0) {
			ChangeEvent ce = new ChangeEvent(source);
			for (ChangeListener i : cl)
				i.stateChanged(ce);
		}
	}

	/**
	 * Invokes @c valueChanged with @c null <i>reason</i> for each @p vl element.
	 * Does nothing if @p vl array is empty.
	 * 
	 * @param source the event source
	 * @param vl the array of value listeners
	 * @param oldValue the old value (can be @c null)
	 * @param newValue the new value (can be @c null)
	 * 
	 * @throws IllegalArgumentException If @p source is @c null
	 * @throws NullPointerException If @p vl is @c null
	 * 
	 * @since 2.4
	 */
	public static <T> void fireValueChanged(final Object source, final ValueListener<T>[] vl, final T oldValue, final T newValue) {
		fireValueChanged(source, vl, oldValue, newValue, null);
	}

	/**
	 * Invokes @c valueChanged for each @p vl element.
	 * Does nothing if @p vl array is empty.
	 * 
	 * @param source the event source
	 * @param vl the array of value listeners
	 * @param oldValue the old value (can be @c null)
	 * @param newValue the new value (can be @c null)
	 * @param reason the event reason (can be @c null)
	 * 
	 * @throws IllegalArgumentException If @p source is @c null
	 * @throws NullPointerException If @p vl is @c null
	 * 
	 * @since 2.4
	 */
	public static <T> void fireValueChanged(final Object source, final ValueListener<T>[] vl, final T oldValue, final T newValue, final Object reason) {
		if (vl.length > 0) {
			ValueEvent<T> ve = new ValueEvent<>(source, oldValue, newValue, reason);
			for (ValueListener<T> i : vl)
				i.valueChanged(ve);
		}
	}

	/**
	 * Returns the value holded by {@code ref} or {@code null}.
	 *
	 * @param ref the reference
	 *
	 * @return {@code null} if {@code ref} is {@code null}
	 *
	 * @see java.lang.ref.Reference#get()
	 *
	 * @since 3.8.9
	 */
	public static <T> T get(final Reference<T> ref) {
		return (ref == null) ? null : ref.get();
	}

	/**
	 * Converts a "program identifier" to a human readable text.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	assertNull(TK.identifierToDisplayString(null));
	assertEmpty(TK.identifierToDisplayString(""));
	assertEquals("FOO BAR", TK.identifierToDisplayString("FOO_BAR"));
	assertEquals("Foo Bar", TK.identifierToDisplayString("foo_bar"));
	assertEquals("Foo Bar", TK.identifierToDisplayString("fooBar"));
	assertEquals("Listen To All AWT Events", TK.identifierToDisplayString("listenToAllAWTEvents"));
	assertEquals("Set Security Manager", TK.identifierToDisplayString("setSecurityManager"));
	assertEquals("Exit VM 1", TK.identifierToDisplayString("exitVM.1"));
	assertEquals("Set IO", TK.identifierToDisplayString("setIO"));
	assertEquals("Read", TK.identifierToDisplayString("read"));
	assertEquals("Read", TK.identifierToDisplayString("Read"));
	assertEquals("READ", TK.identifierToDisplayString("READ"));
	assertEquals("Get X Number", TK.identifierToDisplayString("GetXNumber"));
	 * </pre>
	 *
	 * @since 4.4
	 */
	public static String identifierToDisplayString(final String identifier) {
		if (identifier == null)
			return null;
	
		int len = identifier.length();
		
		if (len == 0)
			return identifier;
	
		boolean nextUpper = false;
		boolean previousLower = false;
		boolean previousUpper = false;
		int extra = 0;
		StringBuilder s = new StringBuilder(len + 10/* for extra spaces */);
		for (int i = 0; i < len; i++) {
			char c = identifier.charAt(i);
			// start with an upper letter
			if (s.length() == 0) {
				s.append(Character.toUpperCase(c));
				nextUpper = false;
				previousLower = false;
				previousUpper = true;
			}
			// normalize separators
			else if ((c == '_') || (c == ' ') || (c == '.')) {
				s.append(' ');
				nextUpper = true;
				previousLower = false;
				previousUpper = false;
			}
			else {
				// next word?
				if (Character.isUpperCase(c)) {
					// x[]Y -> x[SPACE]Y
					if (previousLower) {
						s.append(' ');
						extra++;
						previousLower = false;
						previousUpper = false;
					}
					else {
						previousUpper = true;
					}

					s.append(c);
					nextUpper = false;
				}
				// upper letter after previous space
				else if (nextUpper) {
					s.append(Character.toUpperCase(c));
					nextUpper = false;
					previousLower = false;
					previousUpper = true;
				}
				else if (Character.isLowerCase(c)) {
					// FOOBar -> Foo Bar
					if (previousUpper) {
						int insertAt = (i + extra) - 1;
						if ((insertAt > 0) && (s.charAt(Math.max(0, insertAt - 1)) != ' ')) {
							s.insert(insertAt, ' ');
							extra++;
						}
						previousUpper = false;
					}

					s.append(c);
					nextUpper = false;
					previousLower = true;
				}
				else {
					s.append(Character.toLowerCase(c));
					nextUpper = false;
					previousLower = true;
					previousUpper = false;
				}
			}
		}

		return s.toString();
	}
	
	/**
	 * @since 2.0
	 *
	 * @deprecated As of 4.4, replaced by {@link java.util.Objects#equals(Object, Object)}
	 */
	@Deprecated
	public static boolean isChange(final Object oldValue, final Object newValue) {
		return !Objects.equals(oldValue, newValue);
	}

	/**
	 * Returns @c true if @p value is @c null or empty.
	 * 
	 * @param value the array of @c char elements
	 */
	public static boolean isEmpty(final char[] value) {
		return (value == null) || (value.length == 0);
	}

	/**
	 * Returns {@code true} if {@code value} is {@code null} or empty (zero length).
	 *
	 * @param value the character sequence to test
	 *
	 * @since 3.8.1
	 */
	public static boolean isEmpty(final CharSequence value) {
		return (value == null) || (value.length() == 0);
	}

	/**
	 * Returns @c true if @p value is @c null or empty.
	 */
	public static boolean isEmpty(final Collection<?> value) {
		return (value == null) || value.isEmpty();
	}

	/**
	 * Returns @c true if @p value is @c null or empty.
	 * 
	 * @param value the array of @c java.lang.Object elements
	 */
	public static boolean isEmpty(final Object[] value) {
		return (value == null) || (value.length == 0);
	}

	/**
	 * Returns {@code true} if {@code value} is {@code null} or empty (zero length).
	 *
	 * @param value the string to test
	 */
	public static boolean isEmpty(final String value) {
		return (value == null) || value.isEmpty();
	}

	/**
	 * Returns @c true if key event matches the specified key code.
	 *
	 * @param e the key event
	 * @param keyCode the key code (e.g. @c KeyEvent.VK_ENTER)
	 *
	 * @throws NullPointerException If @p e is @c null
	 */
	public static boolean isKeyStroke(final KeyEvent e, final int keyCode) {
		return isKeyStroke(e, keyCode, 0);
	}

	/**
	 * Returns @c true if key event matches the specified key code and modifiers.
	 *
	 * @param e the key event
	 * @param keyCode the key code (e.g. @c KeyEvent.VK_ENTER)
	 * @param modifiers the modifiers (e.g. @c KeyEvent.ALT_MASK)
	 *
	 * @throws NullPointerException If @p e is @c null
	 */
	public static boolean isKeyStroke(final KeyEvent e, final int keyCode, final int modifiers) {
		return (e.getKeyCode() == keyCode) && (e.getModifiers() == modifiers);
	}
	
	/**
	 * @since 4.2
	 */
	public static boolean isProperty(final PropertyChangeEvent e, final String propertyName, final Object expectedNewValue) {
		return propertyName.equals(e.getPropertyName()) && Objects.equals(expectedNewValue, e.getNewValue());
	}
	
	/**
	 * @since 4.4
	 */
	public static <T> Iterable<T> iterable(final Collection<T> c) {
		if (isEmpty(c))
			return emptyIterable();
		
		return c;
	}

	/**
	 * Returns a new @c Iterable wrapper for @p e enumeration.
	 *
	 * @throws NullPointerException If @p e is @c null
	 *
	 * @since 3.2
	 */
	public static <T> Iterable<T> iterable(final Enumeration<?> e) {
		Objects.requireNonNull(e);

		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return TK.iterator(e);
			}
		};
	}

	/**
	 * @since 4.2
	 */
	public static Iterable<Node> iterable(final Node node) {
		if (!node.hasChildNodes())
			return emptyIterable();
	
		final NodeList list = node.getChildNodes();

		return new AbstractIterator<Node>(list.getLength()) {
			@Override
			public Node getObjectAt(final int index) {
				return list.item(index);
			}
		};
	}

	/**
	 * Returns a new @c Iterator wrapper for @p e enumeration.
	 *
	 * @mg.note {@code Iterator.remove()} method is not implemented
	 * and throws {@code UnsupportedOperationException}
	 *
	 * @throws NullPointerException If @p e is @c null
	 *
	 * @since 3.2
	 */
	public static <T> Iterator<T> iterator(final Enumeration<?> e) {
		Objects.requireNonNull(e);

		return new Iterator<T>() {
			@Override
			public boolean hasNext() {
				return e.hasMoreElements();
			}
			@Override
			@SuppressWarnings("unchecked")
			public T next() {
				return (T)e.nextElement();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Returns the maximum length of {@code it}.
	 *
	 * @param it the {@code String}s
	 *
	 * @throws NullPointerException If {@code it} is {@code null}
	 *
	 * @since 3.8.6
	 */
	public static int maxLength(final Iterable<String> it) {
		Objects.requireNonNull(it);

		int result = 0;
		for (String i : it)
			result = Math.max(result, i.length());

		return result;
	}

	/**
	 * Converts @p elements array to a new generic array of type <code>T[]</code>.
	 * 
	 * @return An empty array if @p elements is @c null or empty
	 * 
	 * @throws ArrayStoreException if @p clazz type is incompatible with @p elements type
	 * 
	 * @since 3.0
	 */
	@SuppressWarnings("unchecked")
	public static <T> T[] newArray(final Class<T> clazz, final Object[] elements) {
		if (isEmpty(elements))
			return (T[])Array.newInstance(clazz, 0);
		
		T[] result = (T[])Array.newInstance(clazz, elements.length);
		System.arraycopy(elements, 0, result, 0, elements.length);
		
		return result;
	}
	
	/**
	 * Returns a new, immutable, <b>not</b> thread-safe {@code java.util.Set} of enum values.
	 *
	 * @param values the enum values
	 *
	 * @return An empty set if {@code values} is {@code null}
	 *
	 * @since 4.0
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	@edu.umd.cs.findbugs.annotation.SuppressWarnings("CLI_CONSTANT_LIST_INDEX")
	public static <T extends Enum<T>> Set<T> newEnumSet(final T... values) {
		if (isEmpty(values))
			return Collections.emptySet();
		
		switch (values.length) {
			case 1: return Collections.unmodifiableSet(EnumSet.of(values[0]));
			case 2: return Collections.unmodifiableSet(EnumSet.of(values[0], values[1]));
			case 3: return Collections.unmodifiableSet(EnumSet.of(values[0], values[1], values[2]));
			case 4: return Collections.unmodifiableSet(EnumSet.of(values[0], values[1], values[2], values[3]));
			case 5: return Collections.unmodifiableSet(EnumSet.of(values[0], values[1], values[2], values[3], values[4]));
			default: return Collections.unmodifiableSet(EnumSet.of(values[0], values));
		}
	}

	/**
	 * Returns a new hash map (@c java.util.HashMap).
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * Map&lt;String, Integer&gt; map = TK.newHashMap(
	 *   "one", 1,
	 *   "two", 2
	 * );
	 * assert map.get("one") == 1;
	 * assert map.get("two") == 2;
	 * </pre>
	 *
	 * @throws IllegalArgumentException If @p keysValues contains key without value
	 * @throws NullPointerException If @p keysValues is @c null
	 *
	 * @since 3.4
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> newHashMap(final Object... keysValues) {
		if ((keysValues.length % 2) != 0)
			throw new IllegalArgumentException("Key without value");

		Map<K, V> result = new HashMap<>(keysValues.length / 2);

		for (int i = 0; i < keysValues.length; i += 2)
			result.put((K)keysValues[i], (V)keysValues[i + 1]);

		return result;
	}

	/**
	 * Returns a new hash set ({@code java.util.HashSet}).
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * Set&lt;String&gt; set = TK.newHashSet("foo", "bar");
	 * assert set.size() == 2;
	 * assert set.contains("foo") && set.contains("bar");
	 * </pre>
	 *
	 * @since 3.4
	 */
	@SafeVarargs
	@SuppressWarnings("varargs")
	public static <T> Set<T> newHashSet(final T... values) {
		if (isEmpty(values))
			return new HashSet<>();

		Set<T> result = new HashSet<>(values.length);
		Collections.addAll(result, values);

		return result;
	}

	/**
	 * Creates and returns a new instance of the specified class
	 * using the current/default class loader.
	 * 
	 * @param className the class to instantiate
	 *
	 * @throws ClassNotFoundException If the class cannot be located
	 * @throws IllegalAccessException If the class or its nullary constructor is not accessible
	 * @throws InstantiationException See @c java.lang.Class newInstance() description
	 * @throws NullPointerException If @p className is @c null
	 * 
	 * @since 1.2
	 */
	public static <T> T newInstance(final String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		@SuppressWarnings("unchecked")
		Class<T> clazz = (Class<T>)Class.forName(className);
		
		return clazz.newInstance();
	}

	/**
	 * Creates and returns a new instance of the specified class
	 * using the {@code classLoader}.
	 *
	 * @param className the class to initialize and instantiate
	 * @param classLoader the class loader (can be {@code null})
	 *
	 * @throws ClassNotFoundException If the class cannot be located
	 * @throws IllegalAccessException If the class or its nullary constructor is not accessible
	 * @throws InstantiationException See {@code java.lang.Class.newInstance()} description
	 * @throws NullPointerException If {@code className} is {@code null}
	 *
	 * @see Class#forName(String, boolean, ClassLoader)
	 * @see Class#newInstance()
	 *
	 * @since 3.8.4
	 */
	public static <T> T newInstance(final String className, final ClassLoader classLoader) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		@SuppressWarnings("unchecked")
		Class<T> clazz = (Class<T>)Class.forName(className, true, classLoader);

		return clazz.newInstance();
	}
	
	/**
	 * @since 4.4
	 */
	public static Pattern newTextSearchPattern(final CharSequence text, final int options) {
		Objects.requireNonNull(text);
	
		int len = text.length();
		StringBuilder s = new StringBuilder(len + len / 2);
		boolean isAlphanum = false;
		for (int i = 0; i < len; i++) {
			char c = text.charAt(i);
			if (Character.isLetterOrDigit(c) || (c == '%')) {
				isAlphanum = true;
				s.append(c);
			}
			else if (Character.isWhitespace(c))
				s.append("(.|\\s)");
			else
				s.append('.');
		}
		
		if (!isAlphanum) {
			s.setLength(0);
			s.append(Pattern.quote(text.toString()));
		}

		return Pattern.compile(
			s.toString(),
			((options & TEXT_SEARCH_PATTERN_CASE_SENSITIVE) != 0) ? 0 : Pattern.CASE_INSENSITIVE
		);
	}

	/**
	 * @since 3.8.4
	 */
	@SafeVarargs
	@SuppressWarnings({ "PMD.LooseCoupling", "varargs" })
	public static <K, V> List<V> order(final LinkedHashMap<K, V> values, final K... order) {
		MArrayList<V> result = new MArrayList<>(values.size());

		if (isEmpty(order)) {
			result.addAll(values.values());

			return result;
		}
		
		// add values in the specified order
		for (K i : order) {
			V v = values.remove(i);
			if (v == null)
				MLogger.warning("core", "TK.order: \"%s\" key not found", i);
			else
				result.add(v);
		}

		// add other values
		for (V v : values.values())
			result.add(v);

		return result;
	}

	/**
	 * Parses arguments into a list of Strings.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * TK.parseArguments(" "); // returns an empty list
	 * TK.parseArguments("foo bar"); // returns two Strings: "foo" and "bar"
	 * TK.parseArguments("foo  bar"); // returns two Strings: "foo" and "bar"
	 * TK.parseArguments("foo \"two words\""); // returns two Strings: "foo" and "two words"
	 * TK.parseArguments("foo \"two \\\"words\\\"\""); // returns two Strings: "foo" and "two \"words\""
	 * </pre>
	 *
	 * @param arguments the arguments to parse
	 *
	 * @return An empty (and immutable) list if @p args is @c null or empty
	 *
	 * @throws ParseException If parse error
	 *
	 * @since 3.0
	 */
	public static List<String> parseArguments(final String arguments) throws ParseException {
		if (arguments == null)
			return Collections.emptyList();

		int len = arguments.length();

		if (len == 0)
			return Collections.emptyList();

		boolean inEscape = false;
		boolean inQuote = false;
		StringList result = new StringList();
		StringBuilder arg = new StringBuilder();
		for (int i = 0; i < len; i++) {
			char c = arguments.charAt(i);
			switch (c) {
				// separator
				case ' ':
				case '\n':
				case '\t':
					if (inQuote) {
						arg.append(c);
					}
					else {
						if (arg.length() > 0) {
							result.add(arg.toString()); // add arg
							arg.setLength(0); // reset arg
						}
					}
					break;

				// escape character
				case '\\':
					if (inQuote) {
						if (inEscape) {
							arg.append('\\');
							inEscape = false;
						}
						else {
							inEscape = true;
						}
					}
					else {
						arg.append(c);
					}
					break;

				// quote
				case '"':
					if (inQuote) {
						if (inEscape) {
							arg.append('"');
							inEscape = false;
						}
						else {
							result.add(arg.toString()); // add arg
							arg.setLength(0); // reset arg
							inQuote = false;
						}
					}
					else {
						inQuote = true;
					}
					break;

				default:
					if (inEscape) {
						switch (c) {
							case 'n':
								arg.append('\n');
								break;
							case 't':
								arg.append('\t');
								break;
							default:
								throwInvalidEscapeSequence(i);
						}
						inEscape = false;
					}
					else {
						arg.append(c);
					}
			}

			// end
			if (i == len - 1) {
				if (inEscape) {
					throwInvalidEscapeSequence(i + 1);
				}

				if (inQuote) {
					throwMissingQuoteCharacter(i + 1);
				}

				if (arg.length() > 0) {
					result.add(arg.toString()); // add arg
					// no need to reset arg
				}
			}
		}

		return result;
	}

	/**
	 * Limits the {@code value}.
	 *
	 * @return {@code minValue} if {@code value} is less than {@code minValue}.
	 * @return {@code maxValue} if {@code value} is greater than {@code maxValue}.
	 * @return {@code value} - otherwise
	 *
	 * @throws IllegalArgumentException If {@code minValue} is greater than {@code maxValue}
	 */
	public static int limit(final int value, final int minValue, final int maxValue) {
		if (minValue > maxValue)
			throw new IllegalArgumentException(String.format("minValue (%d) > maxValue (%d)", minValue, maxValue));

		return Math.max(minValue, Math.min(maxValue, value));
	}

	/**
	 * Serializes an object and its associated name.
	 * If @p value is not serializable an empty @c java.lang.Object array is stored.
	 * 
	 * @param output the object output stream
	 * @param name the object name stored in the @p output stream
	 * @param value the object to serialize
	 * 
	 * @throws IOException If error; see @c java.io.ObjectOutputStream.writeObject for more info
	 * @throws NullPointerException If @p name is @c null
	 * @throws NullPointerException If @p output is @c null
	 * 
	 * @since 2.0
	 */
	@Obsolete
	public static void serialize(final ObjectOutputStream output, final String name, final Object value) throws IOException {
		Objects.requireNonNull(name);
		
		// based on the DefaultMutableTreeNode class
		Object[] data;
		if (value instanceof Serializable)
			data = new Object[] { name, value };
		else
			data = EMPTY_OBJECT_ARRAY;
		output.writeObject(data);
	}

	/**
	 * Causes the currently executing thread to sleep for @p milliseconds.
	 * 
	 * @return @c false if sleep was interrupted (@c InterruptedException); otherwise @c true
	 * 
	 * @throws IllegalArgumentException If @p milliseconds is negative
	 * 
	 * @since 2.0
	 */
	public static boolean sleep(final long milliseconds) {
		try {
			Thread.sleep(milliseconds);
			
			return true;
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			
			//MLogger.warning("core", "%s: %s", Thread.currentThread(), exception.getMessage());
			
			return false;
		}
	}

	/**
	 * Sorts @p map by its keys using @p comparator.
	 * Use @c null @p comparator for <i>natural ordering</i>.
	 * 
	 * @return a new map of sorted entries
	 * 
	 * @throws NullPointerException If @p map is @c null
	 */
	public static <K extends Comparable<?>, V> Map<K, V> sortByKey(final Map<K, V> map, final Comparator<K> comparator) {
		Map<K, V> result = new TreeMap<>(comparator);
		result.putAll(map);
		
		return result;
	}

	/**
	 * Returns a new value list sorted using @p comparator.
	 * Use @c null @p comparator for <i>natural ordering</i>.
	 * 
	 * @throws NullPointerException If @p map is @c null
	 * 
	 * @since 2.0
	 */
	public static <K, V> List<V> sortByValue(final Map<K, V> map, final Comparator<V> comparator) {
		MArrayList<V> sorted = new MArrayList<>(map.values());
		sorted.sort(comparator);
		
		return sorted;
	}
	
	/**
	 * Splits the line into a two {@code String} elements.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * String[] result = TK.splitPair("key = value", '=', TK.SPLIT_PAIR_TRIM);
	 * assert result.length == 2;
	 * assert result[0].equals("key");
	 * assert result[1].equals("value");
	 * </pre>
	 *
	 * @param line the line to split
	 * @param separator the line separator
	 * @param options the options ({@link #SPLIT_PAIR_TRIM})
	 *
	 * @see Tuple
	 *
	 * @since 4.2
	 */
	@edu.umd.cs.findbugs.annotation.SuppressWarnings("DM_STRING_CTOR")
	public static String[] splitPair(final String line, final char separator, final int options) {
		if (isEmpty(line))
			return new String[] { "", "" };
		
		int i = line.indexOf(separator);
		String[] s;
		
		if (i == -1) {
			if ((options & SPLIT_PAIR_NULL_ERROR) != 0)
				return null;
		
			s = new String[] { line, "" };
		}
		else {
			s = new String[] { line.substring(0, i), line.substring(i + 1) };

// TODO: test new JDK 8 String impl. <http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6924259>
			if ((options & SPLIT_PAIR_NO_MEMORY_LEAK) != 0) {
				s[0] = new String(s[0]);
				s[1] = new String(s[1]);
			}
		}

		if ((options & SPLIT_PAIR_TRIM) != 0) {
			s[0] = s[0].trim();
			s[1] = s[1].trim();
		}
		
		return s;
	}

	/**
	 * Returns @c true if @p s starts with @p c.
	 *
	 * @throws NullPointerException If @p s is @c null
	 *
	 * @since 3.0
	 */
	public static boolean startsWith(final String s, final char c) {
		return !s.isEmpty() && (s.charAt(0) == c);
	}

	/**
	 * Strips all markup tags from @p s.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * assert TK.stripMarkupTags("<html>foo</html>").equals("foo");
	 * assert TK.stripMarkupTags("<html></html>").isEmpty();
	 * assert TK.stripMarkupTags("<html><body>foo<br><b>bar</b></body></html>").equals("foobar");
	 * </pre>
	 *
	 * @return @p s if @p s if @c null or empty
	 *
	 * @since 3.2
	 */
	public static String stripMarkupTags(final String s) {
		if (isEmpty(s))
			return s;

		boolean inTag = false;
		int len = s.length();
		StringBuilder result = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			char c = s.charAt(i);
			switch (c) {
				case '<':
					inTag = true;
					break;
				case '>':
					inTag = false;
					break;
				default:
					if (!inTag)
						result.append(c);
			}
		}

		return result.toString();
	}

	/**
	 * @since 3.8.9
	 */
	public static String substring(final String s, final String start, final String end, final boolean includeStartAndEnd) throws ParseException {
		int startIndex = s.indexOf(start);

		if (startIndex == -1)
			throw new ParseException("\"" + start + "\" start substring not found", 0);

		int endIndex = s.indexOf(end);

		if (endIndex == -1)
			throw new ParseException("\"" + end + "\" end substring not found", 0);

		if (endIndex <= startIndex)
			throw new ParseException("End index <= start index", 0);

		if (includeStartAndEnd)
			endIndex += end.length();
		else
			startIndex += start.length();

		return s.substring(startIndex, endIndex);
	}

	/**
	 * Converts an @c int @p value to a hexadecimal String value.
	 * The returned value is prefixed with leading zero
	 * so the returned String length is always greater or equal 2.
	 * 
	 * @mg.note The returned value for negative numbers is undefined.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * assert TK.toByteHex(15).equals("0f");
	 * </pre>
	 */
	public static String toByteHex(final int value) {
		return
			(value < 16)
			? '0' + Integer.toHexString(value)
			: Integer.toHexString(value);
	}
	
	/**
	 * Returns an empty/unmodifiable list if {@code list} is {@code null};
	 * otherwise - the original {@code list}.
	 *
	 * @since 4.2
	 */
	public static <T> List<T> toList(final List<T> list) {
		return (list == null) ? Collections.<T>emptyList() : list;
	}

	/**
	 * Converts @p s String to lower case using @c Locale.ENGLISH.
	 * 
	 * @return The converted @p s String
	 * 
	 * @throws NullPointerException If @p s is @c null
	 * 
	 * @since 2.0
	 */
	public static String toLowerCase(final String s) {
		return s.toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Converts key code and modifiers to @c String.
	 *
	 * @param aKeyCode the key code
	 * @param aModifiers the modifiers
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * assert TK.toString(KeyEvent.VK_F1, KeyEvent.CTRL_MASK).equals("Ctrl+F1");
	 * assert TK.toString(KeyEvent.VK_F2, 0).equals("F2");
	 * </pre>
	 *
	 * @mg.note
	 * The returned String names are localized.
	 *
	 * @see #toString(KeyStroke)
	 */
	public static String toString(final int aKeyCode, final int aModifiers) {
		String keyCode = KeyEvent.getKeyText(aKeyCode);
		String modifiers = KeyEvent.getKeyModifiersText(aModifiers);

		if (isEmpty(modifiers))
			return keyCode;

		if (isEmpty(keyCode))
			return modifiers;

		return modifiers + "+" + keyCode;
	}

	/**
	 * Converts @p keyStroke to @c String.
	 *
	 * @throws NullPointerException If @p keyStroke is @c null
	 *
	 * @see #toString(int, int)
	 */
	public static String toString(final KeyStroke keyStroke) {
		return toString(keyStroke.getKeyCode(), keyStroke.getModifiers());
	}

	/**
	 * Joins a collection of {@code values} into one string.
	 * The values are separated using {@code delimeter}.
	 *
	 * @param values the values to join
	 * @param delimeter the separator
	 *
	 * @return An empty {@code String} If {@code values} is {@code null} or empty
	 */
	public static String toString(final Collection<?> values, final String delimeter) {
		if (isEmpty(values))
			return "";

		boolean wasFirst = false;
		StringBuilder buf = new StringBuilder(1024);
		for (Object i : values) {
			if (wasFirst)
				buf.append(delimeter).append(i);
			else
				buf.append(i);
			wasFirst = true;
		}

		return buf.toString();
	}

	/**
	 * Joins an array of {@code values} into one string.
	 * The values are separated using {@code delimeter}.
	 *
	 * @mg.note {@code null} array elements are ignored.
	 *
	 * @param values the values to join
	 * @param delimeter the separator
	 *
	 * @return An empty {@code String} If {@code values} is {@code null} or empty
	 */
	public static String toString(final Object[] values, final String delimeter) {
		if (isEmpty(values))
			return "";

		boolean wasFirst = false;
		StringBuilder buf = new StringBuilder(1024);
		for (Object i : values) {
			if (i != null) {
				if (wasFirst)
					buf.append(delimeter).append(i);
				else
					buf.append(i);
				wasFirst = true;
			}
		}

		return buf.toString();
	}

	/**
	 * Converts an array of objects to @c String.
	 * The array elements are converted to @c String using @c toString() method
	 * and separated using <code>", "</code> delimenter.
	 * 
	 * @param values the array of objects to convert
	 * 
	 * @return An empty string if @p values is @c null or empty
	 * 
	 * @throws NullPointerException If @p values contains a @c null element
	 */
	public static String toString(final Object[] values) {
		if (isEmpty(values))
			return "";

		boolean addComma = false;
		StringBuilder buf = new StringBuilder(1024);
		for (Object i : values) {
			if (addComma)
				buf.append(", ");
			buf.append(i.toString());
			addComma = true;
		}

		return buf.toString();
	}

	/**
	 * Converts @p s String to upper case using @c Locale.ENGLISH.
	 * 
	 * @return The converted @p s String
	 * 
	 * @throws NullPointerException If @p s is @c null
	 * 
	 * @since 2.0
	 */
	public static String toUpperCase(final String s) {
		return s.toUpperCase(Locale.ENGLISH);
	}

	/**
	 * Unescape/decode an URL @p value using "UTF-8" encoding.
	 *
	 * @mg.example
	 * <pre class="brush: java">
	 * assert TK.unescapeURL("%5C+foo").equals("\\ foo");
	 * </pre>
	 *
	 * @throws IllegalArgumentException If escape pattern is inavalid
	 * @throws NullPointerException If @p value is @c null
	 *
	 * @see #escapeURL(String)
	 * @see #escapeURL(String, int)
	 * @see java.net.URLDecoder
	 */
	public static String unescapeURL(final String value) {
		try {
			return URLDecoder.decode(value, "UTF-8");
		}
		catch (UnsupportedEncodingException exception) {
			MLogger.exception(exception);

			return value;
		}
	}

	/**
	 * Replaces standard XML entities with its applicable character (e.g. "&amp;" -> "&").
	 *
	 * @param value the XML value to unescape
	 *
	 * @return unchanged {@code value} if {@code value} is {@code null} or empty
	 *
	 * @since 1.2
	 */
	public static String unescapeXML(final String value) {
		if (isEmpty(value))
			return value;

		StringBuilder s = new StringBuilder(value);
		fastReplace(s, "&#039;", "\'");
		fastReplace(s, "&apos;", "\'");
		fastReplace(s, "&quot;", "\"");
		fastReplace(s, "&lt;", "<");
		fastReplace(s, "&gt;", ">");
		fastReplace(s, "&amp;", "&");

		return s.toString();
	}

	/**
	 * Returns an <i>unmodifiable</i> view of {@code it}.
	 *
	 * @mg.note {@code Iterator.remove()} method is not implemented
	 * and throws {@code UnsupportedOperationException}
	 *
	 * @param it the modifiable iterator
	 *
	 * @throws NullPointerException If {@code it} is {@code null}
	 *
	 * @since 3.8.6
	 */
	public static <T> Iterator<T> unmodifiableIterator(final Iterator<T> it) {
		Objects.requireNonNull(it);

		return new Iterator<T>() {
			@Override
			public boolean hasNext() {
				return it.hasNext();
			}
			@Override
			public T next() {
				return it.next();
			}
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
	}

	/**
	 * Checks whether or not @p id matches a pattern.
	 * The pattern is <code>"(\\w|\\-)+"</code> (a-zA-Z_0-9 or "-").
	 * 
	 * @return the unmodified @p id
	 * 
	 * @throws IllegalArgumentException If @p id is @c null, empty, or if it does not match the pattern
	 *
	 * @since 2.0
	 */
	public static String validateID(final String id) {
		if (isEmpty(id) || !validateIDPattern.matcher(id).matches())
			throw new IllegalArgumentException(String.format("Invalid \"id\": \"%s\". Valid IDs are \"%s\".", id, validateIDPattern));

		return id;
	}

	/**
	 * Checks whether or not @p id matches regular expression @p pattern.
	 * 
	 * @return the unmodified @p id
	 * 
	 * @throws IllegalArgumentException If @p id is @c null, empty, or if it does not match the pattern
	 * @throws NullPointerException If @p pattern is @c null
	 * @throws PatternSyntaxException If regular expression @p pattern is invalid
	 *
	 * @since 2.0
	 */
	public static String validateID(final String id, final String pattern) {
		if (isEmpty(id) || !id.matches(pattern))
			throw new IllegalArgumentException(String.format("Invalid \"id\": \"%s\". Valid IDs are \"%s\".", id, pattern));
		
		return id;
	}

	public static String centerSqueeze(final String text) {
		return centerSqueeze(text, 50);
	}
	
	public static String centerSqueeze(final String text, final int maxLength) {
		if (text == null)
			return null;
		
		int max = Math.max(3, maxLength);
		
		if (text.length() <= max)
			return text;

		if ((max == 3) || (text.length() < 4))
			return "...";
		
		int mid = text.length() / 2;
		int midCut = (text.length() - max) / 2;
		
		return text.substring(0, mid - midCut - 2) + "..." + text.substring(mid + midCut + 2);
	}

	/**
	 * @since 2.0
	 *
	 * @deprecated Since 4.6
	 */
	@Deprecated
	public static int compareIgnoreCase(final String o1, final String o2, final String defaultValue) {
		String item1 = (o1 == null) ? defaultValue : o1;
		String item2 = (o2 == null) ? defaultValue : o2;
		
		return item1.compareToIgnoreCase(item2);
	}

	/**
	 * Compares the two strings, ignoring case differences.
	 *
	 * @mg.note
	 * This method uses the default locale.
	 *
	 * @return {@code true} if {@code string} contains {@code substring};
	 * {@code false} if {@code string} or {@code substring} is {@code null}.
	 *
	 * @see #containsIgnoreCase(String, String, java.util.Locale)
	 */
	public static boolean containsIgnoreCase(final String string, final String substring) {
		return containsIgnoreCase(string, substring, OS.getLocale());
	}

	/**
	 * Compares the two strings, ignoring case differences.
	 *
	 * @param locale the locale used by {@link java.lang.String#toUpperCase(java.util.Locale)}
	 *
	 * @return {@code true} if {@code string} contains {@code substring};
	 * {@code false} if {@code string} or {@code substring} is {@code null}.
	 *
	 * @throws NullPointerException If {@code locale} is {@code null}
	 *
	 * @see #containsIgnoreCase(String, String)
	 */
	public static boolean containsIgnoreCase(final String string, final String substring, final Locale locale) {
		if ((string == null) || (substring == null) || (substring.length() > string.length()))
			return false;

		return string.toUpperCase(locale).indexOf(substring.toUpperCase(locale)) != -1;
	}

	/**
	 * @since 3.8.12
	 */
	public static String fastReplace(final String s, final String from, final String to) {
		StringBuilder buf = new StringBuilder(s);
		
		if (fastReplace(buf, from, to))
			return buf.toString();

		return s;
	}

	/**
	 * @since 3.8.12
	 */
	public static boolean fastReplace(final StringBuilder buf, final String from, final String to) {
		int fromLen = from.length();
		int toLen = to.length();
		boolean useReplace = (fromLen == toLen);

		boolean changed = false;
		int i = 0;
		while ((i = buf.indexOf(from, i)) != -1) {
			changed = true;
			if (useReplace) {
				buf.replace(i, i + toLen, to);
			}
			else {
				buf.delete(i, i + fromLen);
				buf.insert(i, to);
			}
			i += toLen;
		}

		return changed;
	}

	/**
	 * @mg.warning This method is <b>not</b> compatible with <code>java.lang.String#split(String)</code>!
	 *
	 * @since 3.0
	 */
	public static List<String> fastSplit(final String text, final char delimeter) {
		if (text == null)
			return Collections.emptyList();
		
		int len = text.length();

		if (len == 0)
			return Collections.emptyList();
		
		MArrayList<String> result = new MArrayList<>();
		
		int fromIndex = 0;
		int lastIndex = -1;
		while (true) {
			fromIndex = text.indexOf(delimeter, fromIndex);
			if (fromIndex == -1) { // last item
				if (len == lastIndex + 1)
					return result;
				
				if (lastIndex == -1)
					result.add(text);
				else
					result.add(text.substring(lastIndex + 1, len));
					
				return result;
			}
			else {
				if (fromIndex == lastIndex + 1) { // skip delimeters
					fromIndex++;
					lastIndex++;

					continue; // while
				}

				if (lastIndex == -1)
					result.add(text.substring(0, fromIndex)); // first item
				else
					result.add(text.substring(lastIndex + 1, fromIndex));
			}
			lastIndex = fromIndex++;
		}
	}

	public static String rightSqueeze(final String text, final int maxLength) {
		if (text == null)
			return null;
		
		if (text.length() > maxLength)
			return text.substring(0, maxLength - 1) + "...";
		
		return text;
	}

	// private

	@Uninstantiable
	private TK() { }

	private static void throwInvalidEscapeSequence(final int errorOffset) throws ParseException {
		throw new ParseException(
			"Invalid escape sequence.\n" +
			"Valid escape characters are:\n" +
			"\t\\\\ - \\ character\n" +
			"\t\\\" - \" character\n" +
			"\t\\n - new line\n" +
			"\t\\t - tab character",
			errorOffset
		);
	}

	private static void throwMissingQuoteCharacter(final int errorOffset) throws ParseException {
		throw new ParseException("Missing quote character", errorOffset);
	}

}
