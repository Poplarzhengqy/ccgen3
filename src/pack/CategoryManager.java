// Copyright 2008 Konrad Twardowski
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

package org.makagiga.commons.category;

import static org.makagiga.commons.UI._;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.makagiga.commons.Config;
import org.makagiga.commons.FS;
import org.makagiga.commons.MAction;
import org.makagiga.commons.MActionInfo;
import org.makagiga.commons.MColor;
import org.makagiga.commons.MDisposable;
import org.makagiga.commons.TK;
import org.makagiga.commons.UI;
import org.makagiga.commons.ValueEvent;
import org.makagiga.commons.ValueListener;
import org.makagiga.commons.color.MSmallColorChooser;
import org.makagiga.commons.icons.MIconButton;
import org.makagiga.commons.icons.MIconChooser;
import org.makagiga.commons.mv.MRenderer;
import org.makagiga.commons.script.ScriptYourself;
import org.makagiga.commons.swing.ActionGroup;
import org.makagiga.commons.swing.MComponent;
import org.makagiga.commons.swing.MDialog;
import org.makagiga.commons.swing.MGroupLayout;
import org.makagiga.commons.swing.MList;
import org.makagiga.commons.swing.MMessage;
import org.makagiga.commons.swing.MPanel;
import org.makagiga.commons.swing.MTextField;
import org.makagiga.commons.swing.MToolBar;
import org.makagiga.commons.validator.TextComponentValidator;
import org.makagiga.commons.xml.XMLHelper;

/**
 * @since 3.0
 */
public class CategoryManager implements Iterable<Category> {

	// private

	private boolean editable;
	private final CategoryList set = new CategoryList();
	private final EventListenerList eventListenerList = new EventListenerList();
	private File file;
	private final Map<String, Category> map = new HashMap<>();
	private final Set<Category> readOnlySet = Collections.unmodifiableSet(set);
	
	// public

	public void addCategory(final Category category) {
		set.add(category);
		map.put(category.getName(), category);
	}

	public void addChangeListener(final ChangeListener l) {
		eventListenerList.add(ChangeListener.class, l);
	}

	public ChangeListener[] getChangeListeners() {
		return eventListenerList.getListeners(ChangeListener.class);
	}

	public void removeChangeListener(final ChangeListener l) {
		eventListenerList.remove(ChangeListener.class, l);
	}

	/**
	 * @since 3.2
	 */
	public boolean isEditable() { return editable; }

	/**
	 * @since 3.2
	 */
	public void setEditable(final boolean value) { editable = value; }

	/**
	 * @since 4.0
	 */
	public boolean isEmpty() {
		return set.isEmpty();
	}

	public void removeAll() {
		set.clear();
		map.clear();
	}

	public Category getCategory(final String name) {
		return map.get(name);
	}

	public Set<Category> getCategorySet() { return readOnlySet; }

	public static CategoryManager getSharedInstance() {
		return LazyCategoryManagerHolder.INSTANCE;
	}

	public boolean read(final File file) {
		this.file = Objects.requireNonNull(file, "file");

		Config config = new Config(file.getPath());
		load(config);

		return !config.isError();
	}

	public boolean showDialog(final Window owner) {
		File evolutionCategories = new File(FS.getUserDir(), ".evolution/categories.xml");

		int dialogFlags = MDialog.STANDARD_DIALOG;
		if (evolutionCategories.exists())
			dialogFlags |= MDialog.USER_BUTTON;
		CategoryManagerDialog dialog = new CategoryManagerDialog(owner, dialogFlags, set, evolutionCategories);

		if (dialog.exec(dialog.categoryList)) {
			removeAll();
			for (Category i : dialog.categoryList)
				addCategory(i);
			sync();
			fireStateChanged();

			return true;
		}

		return false;
	}

	public boolean sync() {
		Config config = new Config(file.getPath());
		store(config);

		return config.sync();
	}

	// Iterable

	@Override
	public Iterator<Category> iterator() {
		return readOnlySet.iterator();
	}

	// protected

	protected CategoryManager() { }

	protected void fireStateChanged() {
		TK.fireStateChanged(this, getChangeListeners());
	}

	// private

	private CategoryManager(final File file) {
		setEditable(true);
		read(file);

		Config config = Config.getDefault();
		if (isEmpty() && config.read("Categories.createSamples", true)) {
			config.write("Categories.createSamples", false);
			addCategory(new Category("Starred", MColor.SUN_YELLOW, "ui/star"));
			addCategory(new Category("Todo", MColor.FOREST_GREEN, "labels/todo"));
			addCategory(new Category("Work"));
			sync();
		}
	}

	private static void importFromEvolution(final Window owner, final File file, final MList<Category> categoryList) {
		try {
			EvolutionCategories c = XMLHelper.unmarshal(EvolutionCategories.class, file);
			XMLHelper.clearContextCache(EvolutionCategories.class);
			if (!TK.isEmpty(c.list)) {
				if (!MMessage.simpleConfirm(
					owner,
					MActionInfo.IMPORT,
					c.list.toArray()
				))
					return;

				for (EvolutionCategory i : c.list) {
					if (!TK.isEmpty(i.name) && i.searchable) {
						if (!TK.isEmpty(i.icon)) {
							URL url = FS.toURL(new File(i.icon));
							i.icon = Objects.toString(url, null);
						}
						i.name = i.name.replace(CategoryList.DEFAULT_SEPARATOR, '_');
						categoryList.addItem(new Category(i.name, null, i.icon));
					}
				}
			}
		}
		catch (FileNotFoundException | JAXBException exception) {
			MMessage.error(owner, exception);
		}
	}

	private void load(final Config config) {
		removeAll();

		int count = config.readInt("Category.count", 0, 0);

		if (count == 0)
			return;

		for (int index = 0; index < count; index++) {
			String name = config.read("Category.name." + index, null);

			if (TK.isEmpty(name))
				continue; // for

			addCategory(new Category(
				name,
				config.readColor("Category.color." + index, null),
				config.read("Category.icon." + index, null)
			));
		}
	}

	private void store(final Config config) {
		config.removeAll();

		config.write("Category.count", set.size());
		int index = 0;
		for (Category i : this) {
			config.write("Category.color." + index, i.getColor());
			config.write("Category.icon." + index, i.getIconName());
			config.write("Category.name." + index, i.getName());
			index++;
		}
	}
	
	// public classes
	
	/**
	 * @since 4.0
	 */
	public static final class AutoRepaint implements ChangeListener {
	
		// private
		
		private final WeakReference<Component> componentRef;
	
		// public
		
		public AutoRepaint(final Component c) {
			componentRef = new WeakReference<>(c);
		}
	
		@Override
		public void stateChanged(final ChangeEvent e) {
			Component c = componentRef.get();
			if (c != null)
				c.repaint();
		}
	
	}

	// private classes

	private static final class CategoryManagerDialog extends MDialog {

		// private

		private ActionGroup actionGroup;
		private ActionListener actionListener;
		private final File evolutionCategories;
		private ListSelectionListener listSelectionListener;
		private final MList<Category> categoryList;

		// protected

		@Override
		protected void onClose() {
			super.onClose();
			actionGroup.clear();
			actionGroup = null;

			categoryList.removeActionListener(actionListener);
			actionListener = null;

			categoryList.getSelectionModel().removeListSelectionListener(listSelectionListener);
			listSelectionListener = null;
		}

		@Override
		protected void onUserClick() {
			CategoryManager.importFromEvolution(this, evolutionCategories, categoryList);
		}

		// private

		private CategoryManagerDialog(final Window owner, final int dialogFlags, final CategoryList set, final File evolutionCategories) {
			super(owner, _("Edit Categories"), dialogFlags);
			this.evolutionCategories = evolutionCategories;
			if (getUserButton() != null)
				changeButton(getUserButton(), _("Import from {0}", "Evolution"));

			categoryList = new MList<>();

			Category prototype = new Category("FAKE", null, "ui/misc");
			categoryList.setCellRenderer(new MRenderer<Category>(2), prototype);

			listSelectionListener = new ListSelectionListener() {
				@Override
				public void valueChanged(final ListSelectionEvent e) {
					actionGroup.update();
				}
			};
			categoryList.getSelectionModel().addListSelectionListener(listSelectionListener);
		
			categoryList.setSingleSelectionMode();
			categoryList.setText(MList.AUTO_TEXT);
			categoryList.addAllItems(set);

			actionListener = new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					if (categoryList.getActionType() == MList.ActionType.TRIGGER) {
						actionGroup.fire("properties", categoryList);
					}
				}
			};
			categoryList.addActionListener(actionListener);

			addCenter(MPanel.createVLabelPanel(categoryList, _("Categories:")));

			actionGroup = new ActionGroup() {
				@Override
				public void update() {
					boolean isSelection = !categoryList.isSelectionEmpty();
					this.getAction("delete").setEnabled(isSelection);
					this.getAction("properties").setEnabled(isSelection);
				}
			};
			actionGroup.add("new", new MAction(MActionInfo.NEW) {
				@Override
				public void onAction() {
					CategoryPropertiesPanel cpp = new CategoryPropertiesPanel(null);
					Category newCategory = cpp.showDialog(this.getSourceWindow());
					if (newCategory != null) {
						categoryList.addItem(newCategory);
						categoryList.setSelectedItem(newCategory, true);
						actionGroup.update();
					}
					cpp.dispose();
				}
			} );
			actionGroup.addSeparator();
			actionGroup.add("delete", new MAction(MActionInfo.DELETE) {
				@Override
				public void onAction() {
					Category category = categoryList.getSelectedItem();
					if (category != null)
						categoryList.removeItem(category);
					actionGroup.update();
				}
			} );
			actionGroup.addSeparator();
			actionGroup.add("properties", new MAction(MActionInfo.PROPERTIES) {
				@Override
				public void onAction() {
					Category oldCategory = categoryList.getSelectedItem();
					if (oldCategory != null) {
						CategoryPropertiesPanel cpp = new CategoryPropertiesPanel(oldCategory);
						Category newCategory = cpp.showDialog(this.getSourceWindow());
						if ((newCategory != null) && !newCategory.equals(oldCategory)) {
							categoryList.removeItem(oldCategory);
							categoryList.addItem(newCategory, 0);
							categoryList.setSelectedItem(newCategory, true);
						}
						cpp.dispose();
					}
					actionGroup.update();
				}
			} );

			actionGroup.installPopupMenu(categoryList);

			actionGroup.update();

			MToolBar toolBar = new MToolBar();
			actionGroup.updateToolBar(toolBar);
			ScriptYourself.install(toolBar, "category-manager");
			setToolBar(toolBar);

			packFixed(UI.WindowSize.MEDIUM);
		}

	}

	private static final class CategoryPropertiesPanel extends MPanel implements MDisposable {

		// private

		private CategoryPreview categoryPreview;
		private MIconButton iconField;
		private MSmallColorChooser colorField;
		private MTextField nameField;
		
		// public
		
		// MDisposable
		
		@Override
		public void dispose() {
			categoryPreview = null;
			iconField = null;
			colorField = null;
			nameField = null;
		}

		// private

		private CategoryPropertiesPanel(final Category category) {
			super(false);

			iconField = new MIconButton(null);
			iconField.addValueListener(new ValueListener<Icon>() {
				@Override
				public void valueChanged(final ValueEvent<Icon> e) {
					updatePreview();
				}
			} );
			iconField.setIconChooserFlags(MIconChooser.SHOW_DEFAULT_ICON);
			
			nameField = new MTextField() {
				@Override
				protected void onChange(final DocumentEvent e) {
					updatePreview();
				}
			};
			
			colorField = new MSmallColorChooser();
			colorField.addValueListener(new ValueListener<Color>() {
				@Override
				public void valueChanged(final ValueEvent<Color> e) {
					updatePreview();
				}
			} );
			colorField.setResetActionVisible(true);
			
			if (category == null) {
				colorField.setValue(null);
			}
			else {
				iconField.setIconName(category.getIconName());
				nameField.setText(category.getName());
				colorField.setValue(category.getColor());
			}
			nameField.setAutoCompletion("categoryname");
			
			categoryPreview = new CategoryPreview(); // last
			
			MGroupLayout l = getGroupLayout();
			l.setAutoCreateContainerGaps(true);
			l
				.addComponent(iconField, MGroupLayout.Alignment.LEADING)
				.addGap(getContentMargin() * 2)
				.beginRows()
					.addComponent(nameField, _("Category Name:"))
					.addGap(getContentMargin() * 2)
					.addComponent(colorField)
					.addGap(getContentMargin() * 2)
					.addComponent(categoryPreview, _("Preview:"))
				.end();
			
			updatePreview();
		}

		private Category showDialog(final Window owner) {
			MDialog dialog = new MDialog(owner, _("Properties"), MActionInfo.PROPERTIES.getIconName());
			dialog.getValidatorSupport().add(new TextComponentValidator(nameField) {
				@Override
				protected boolean isValid() throws Exception {
					Category.validateName(this.getText());

					return true;
				}
			} );

			dialog.addCenter(this);
			dialog.installValidatorMessage();

			dialog.packFixed(UI.WindowSize.MEDIUM);

			if (dialog.exec(nameField)) {
				nameField.saveAutoCompletion();
				
				return createCategory();
			}

			return null;
		}
		
		private Category createCategory() {
			return new Category(
				nameField.isEmpty() ? "fake" : nameField.getText(),
				colorField.getValue(),
				iconField.getIconName()
			);
		}
		
		private void updatePreview() {
			if (categoryPreview != null)
				categoryPreview.update(createCategory());
		}

	}
	
	private static final class CategoryPreview extends MPanel {
	
		// private
		
		private CategoryList categoryList = new CategoryList();
		
		// protected
	
		@Override
		protected void paintComponent(final Graphics graphics) {
			CategoryListRenderer.paint(this, (Graphics2D)graphics, categoryList, MColor.WHITE);
		}
		
		// private
		
		private void update(final Category category) {
			categoryList.clear();
			categoryList.add(category);
			MComponent.setFixedSize(this, CategoryListRenderer.getPreferredSize(this, categoryList));
			repaint();
		}
	
	}

	@XmlRootElement(name = "categories")
	private static final class EvolutionCategories {

		// private

		@XmlElement(name = "category")
		private List<EvolutionCategory> list;

	}

	private static final class EvolutionCategory {

		// private

		@XmlAttribute(name = "searchable")
		private boolean searchable;

		@XmlAttribute(name = "icon")
		private String icon;

		@XmlAttribute(name = "a")
		private String name;

		// public

		@Override
		public String toString() { return name; }

	}

	private static final class LazyCategoryManagerHolder {

		// private

		private static final CategoryManager INSTANCE = new CategoryManager(FS.makeConfigFile("categories.properties"));

		// private

		private LazyCategoryManagerHolder() { }

	}

}
