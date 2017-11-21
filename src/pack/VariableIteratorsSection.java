package de.fzj.unicore.rcp.wfeditor.properties;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertyConstants;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import de.fzj.unicore.rcp.common.guicomponents.AbstractListElement;
import de.fzj.unicore.rcp.common.guicomponents.GenericListEditor;
import de.fzj.unicore.rcp.common.guicomponents.IListElement;
import de.fzj.unicore.rcp.common.guicomponents.IListListener;
import de.fzj.unicore.rcp.wfeditor.model.conditions.Condition;
import de.fzj.unicore.rcp.wfeditor.model.conditions.ConditionTypeVariableComparison;
import de.fzj.unicore.rcp.wfeditor.model.conditions.IConditionType;
import de.fzj.unicore.rcp.wfeditor.model.variables.IElementWithVariableIterators;
import de.fzj.unicore.rcp.wfeditor.model.variables.WorkflowVariable;
import de.fzj.unicore.rcp.wfeditor.model.variables.WorkflowVariableIterator;
import de.fzj.unicore.rcp.wfeditor.model.variables.WorkflowVariableIteratorList;
import de.fzj.unicore.rcp.wfeditor.model.variables.WorkflowVariableModifier;
import de.fzj.unicore.rcp.wfeditor.ui.ConditionTypeControlFactory;
import de.fzj.unicore.rcp.wfeditor.ui.IConditionTypeControlProducer;

public class VariableIteratorsSection extends AbstractSection {

	protected static final int VERTICAL_OFFSET = 3*ITabbedPropertyConstants.VSPACE, HORIZONTAL_OFFSET = 2*ITabbedPropertyConstants.HSPACE;

	protected org.eclipse.swt.widgets.List iterators;

	protected GenericListEditor<WorkflowVariableIteratorWrapper> iteratorsEditor;

	protected Composite groupParent;
	protected Label initialValueLabel, expressionLabel, variableTypeLabel, conditionLabel;
	protected Text initialValueText, expressionText, conditionText;
	protected Composite listParent;

	protected CCombo variableTypeCombo, conditionOperatorCombo;
	protected boolean allowAdding = true;

	protected WorkflowVariableIteratorList workflowVariableIteratorList;

	protected ModifyListener expressionModifyListener, variableNameModifyListener, initialValueModifyListener, conditionModifyListener;
	protected PropertyChangeListener iteratorsActiveListener;
	protected IListListener<WorkflowVariableIteratorWrapper> listListener;

	protected SelectionListener variableTypeSelectionListener, conditionOperatorSelectionListener;

	/**
	 * @see org.eclipse.ui.views.properties.tabbed.ITabbedPropertySection#createControls(org.eclipse.swt.widgets.Composite,
	 *      org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage)
	 */
	public void createControls(Composite parent,
			TabbedPropertySheetPage tabbedPropertySheetPage) {
		super .createControls(parent, tabbedPropertySheetPage);
		Composite flatForm= getWidgetFactory()
		.createFlatFormComposite(parent);
		FormData data;
		groupParent = getWidgetFactory().createGroup(flatForm, "Variable Iterators");
		data = new FormData();
		data.left = new FormAttachment(0, ITabbedPropertyConstants.HSPACE);
		data.right = new FormAttachment(100, -ITabbedPropertyConstants.HMARGIN);
		data.top = new FormAttachment(0, ITabbedPropertyConstants.VSPACE);
		data.bottom = new FormAttachment(100, -ITabbedPropertyConstants.VSPACE);
		groupParent.setLayoutData(data);
		groupParent.setLayout(new FormLayout());
		controls.add(groupParent);


		listParent = getWidgetFactory().createComposite(groupParent);
		listParent.setToolTipText("Define workflow variables to be iterated over. A variable can be used as an input parameter for jobs in the loop body. Multiple variables can be defined in order to perform sweeps over multiple parameters (the variables are iterated over independently of each other).");
		data = new FormData();
		data.left = new FormAttachment(0, 3);

		data.top = new FormAttachment(0, VERTICAL_OFFSET);
		listParent.setLayoutData(data);
		iteratorsEditor = new GenericListEditor<WorkflowVariableIteratorWrapper>(listParent, new ArrayList<WorkflowVariableIteratorWrapper>(),new boolean[]{true,true,false,false,true}){
			@Override
			protected WorkflowVariableIteratorWrapper createNewListElement() {
				WorkflowVariableIterator iterator = new WorkflowVariableIterator(getElement());
				iterator.activate();
				iterator.setDisposable(true);
				return new WorkflowVariableIteratorWrapper(iterator);
			}
		};
		controls.add(iteratorsEditor.getControl());
		String variableNameTooltip = "The name of the workflow variable to iterate over. The variable can be referred to in the loop body.";
		iteratorsEditor.getControl().setToolTipText(variableNameTooltip);


		String variableTypeTooltip = "The type of values that the workflow variable can assume.";
		variableTypeLabel = getWidgetFactory().createLabel(groupParent, "Variable Type:");
		variableTypeLabel.setToolTipText(variableTypeTooltip);
		data = new FormData();
		data.left = new FormAttachment(listParent, HORIZONTAL_OFFSET);
		data.top = new FormAttachment(0,VERTICAL_OFFSET);
		variableTypeLabel.setLayoutData(data);
		controls.add(variableTypeLabel);

		variableTypeCombo = getWidgetFactory().createCCombo(groupParent,SWT.FLAT | SWT.READ_ONLY | SWT.BORDER);
		variableTypeCombo.setToolTipText(variableTypeTooltip);
		variableTypeCombo.setBackground(null);
		data = new FormData();
		data.left = new FormAttachment(variableTypeLabel, HORIZONTAL_OFFSET);
		data.right = new FormAttachment(100,-ITabbedPropertyConstants.HMARGIN);
		data.top = new FormAttachment(0, VERTICAL_OFFSET);
		variableTypeCombo.setLayoutData(data);
		variableTypeCombo.setItems(WorkflowVariable.VARIABLE_TYPES);
		controls.add(variableTypeCombo);


		String initialValueTooltip = "Initial value of the variable in the first iteration of the for-loop. The initial value is then modified in each iteration using the epression below.";
		initialValueLabel = getWidgetFactory().createLabel(groupParent, "Initial Value:");
		initialValueLabel.setToolTipText(initialValueTooltip);
		data = new FormData();
		data.left = new FormAttachment(listParent, HORIZONTAL_OFFSET);
		data.top = new FormAttachment(variableTypeCombo,VERTICAL_OFFSET);
		initialValueLabel.setLayoutData(data);
		controls.add(initialValueLabel);

		initialValueText = getWidgetFactory().createText(groupParent, "",SWT.BORDER|SWT.LEFT);
		initialValueText.setToolTipText(initialValueTooltip);
		initialValueText.setSize(100,initialValueText.getSize().y);
		data = new FormData();
		data.left = new FormAttachment(initialValueLabel, HORIZONTAL_OFFSET);
		data.right = new FormAttachment(100,-ITabbedPropertyConstants.HMARGIN);
		data.top = new FormAttachment(variableTypeCombo, VERTICAL_OFFSET);
		initialValueText.setLayoutData(data);
		controls.add(initialValueText);

		String expressionTooltip = "Expression for assigning a new value to the variable in each iteration.\nThe syntax to be used is based on Java.\n" +
		"Examples:\n" +
		"Variable1 ++;\n"+
		"Variable1 = Variable1+5;";

		expressionLabel = getWidgetFactory().createLabel(groupParent, "Value modifier:");
		expressionLabel.setToolTipText(expressionTooltip);
		data = new FormData();
		data.left = new FormAttachment(listParent, HORIZONTAL_OFFSET);
		data.top = new FormAttachment(initialValueText,VERTICAL_OFFSET);
		expressionLabel.setLayoutData(data);
		controls.add(expressionLabel);

		expressionText = getWidgetFactory().createText(groupParent, "",SWT.BORDER|SWT.LEFT);
		expressionText.setSize(100,expressionText.getSize().y);
		data = new FormData();
		data.left = new FormAttachment(expressionLabel, HORIZONTAL_OFFSET);
		data.right = new FormAttachment(100,-ITabbedPropertyConstants.HMARGIN);
		data.top = new FormAttachment(initialValueText, VERTICAL_OFFSET);
		expressionText.setLayoutData(data);
		expressionText.setToolTipText(expressionTooltip);
		controls.add(expressionText);

		String conditionTooltip = "Define a condition that is evaluated in order to determine the number of iterations. Additional iterations are created while the condition holds true. Note that - in contrast to the while-loop - the number of iterations is determined BEFORE a single iteration is executed.";
		conditionLabel = getWidgetFactory().createLabel(groupParent, "While variable value");
		conditionLabel.setToolTipText(conditionTooltip);
		data = new FormData();
		data.left = new FormAttachment(listParent, HORIZONTAL_OFFSET);
		data.top = new FormAttachment(expressionText, VERTICAL_OFFSET);
		data.bottom = new FormAttachment(100, -ITabbedPropertyConstants.VMARGIN);
		conditionLabel.setLayoutData(data);
		controls.add(conditionLabel);

		conditionOperatorCombo = getWidgetFactory().createCCombo(groupParent, SWT.FLAT | SWT.READ_ONLY | SWT.BORDER);
		conditionOperatorCombo.setToolTipText(conditionTooltip);
		data = new FormData();
		data.left = new FormAttachment(conditionLabel, HORIZONTAL_OFFSET);
		data.top = new FormAttachment(expressionText, VERTICAL_OFFSET);
		data.bottom = new FormAttachment(100, -ITabbedPropertyConstants.VMARGIN);
		conditionOperatorCombo.setLayoutData(data);
		controls.add(conditionOperatorCombo);

		conditionText = getWidgetFactory().createText(groupParent, "",SWT.SINGLE|SWT.BORDER);
		conditionText.setToolTipText(conditionTooltip);
		data = new FormData();
		data.left = new FormAttachment(conditionOperatorCombo, HORIZONTAL_OFFSET);
		data.right = new FormAttachment(100, -ITabbedPropertyConstants.HMARGIN);
		data.top = new FormAttachment(expressionText, VERTICAL_OFFSET);
		data.bottom = new FormAttachment(100, -ITabbedPropertyConstants.VMARGIN);
		conditionText.setLayoutData(data);
		controls.add(conditionText);

		createListeners();
	}

	public void dispose()
	{
		removeListeners();
	}

	protected void createListeners()
	{
		iteratorsActiveListener = new PropertyChangeListener(){

			public void propertyChange(PropertyChangeEvent evt) {
				if(!IElementWithVariableIterators.PROP_ITERATORS_USED.equals(evt.getPropertyName())) return;
				boolean enabled = (Boolean) evt.getNewValue();
				setIteratorGroupEnabled(enabled);
				if(enabled && iteratorsEditor.getSelectedElement() == null) iteratorSelectionChanged(null);
				
			}

		};

		expressionModifyListener = new ModifyListener(){

			public void modifyText(ModifyEvent e) {
				String value = expressionText.getText();
				WorkflowVariableIterator iterator = getSelectedIterator();
				if(iterator != null && !value.equals(iterator.getModifier().getInternalExpression())) 
				{
					iterator.getModifier().setInternalExpression(value);
					iterator.getModifier().setDisplayedExpression(value);
					updateElement();
				}
			}
		};

		conditionModifyListener = new ModifyListener(){

			public void modifyText(ModifyEvent e) {
				String value = conditionText.getText();
				WorkflowVariableIterator iterator = getSelectedIterator();
				ConditionTypeVariableComparison conditionType = getVariableComparison();
				if(iterator != null && conditionType != null && !value.equals(conditionType.getValue())) 
				{
					conditionType.setValue(value);
					updateElement();
				}
			}
		};


		variableTypeSelectionListener = new SelectionListener()
		{
			public void widgetDefaultSelected(SelectionEvent e) {
				updateVariableType();
			}

			public void widgetSelected(SelectionEvent e) {
				updateVariableType();
			}

			private void updateVariableType()
			{
				String selected = variableTypeCombo.getItem(variableTypeCombo.getSelectionIndex());
				// update list of available comparators for this variable type
				// try to keep old operator if possible
				int oldIndex = conditionOperatorCombo.getSelectionIndex();
				if(WorkflowVariable.VARIABLE_TYPE_STRING.equals(selected)) conditionOperatorCombo.setItems(IConditionType.STRING_COMPARATORS);
				else if(WorkflowVariable.VARIABLE_TYPE_INTEGER.equals(selected)) conditionOperatorCombo.setItems(IConditionType.COMPARATORS);
				oldIndex = Math.max(oldIndex, 0);
				oldIndex = Math.min(oldIndex, conditionOperatorCombo.getItemCount()-1);
				conditionOperatorCombo.select(oldIndex);
				WorkflowVariableIterator iterator = getSelectedIterator();
				if(iterator == null) { // shouldn't happen!
					return;
				}
				WorkflowVariable var = iterator.getVariable();
				if(var != null)
				{
					var.setType(selected);
					updateElement();
					
				}

			}

		};

		conditionOperatorSelectionListener = new SelectionListener()
		{
			public void widgetDefaultSelected(SelectionEvent e) {
				updateConditionOperator();
			}

			public void widgetSelected(SelectionEvent e) {
				updateConditionOperator();
			}

			private void updateConditionOperator()
			{
				int comparator = conditionOperatorCombo.getSelectionIndex();
				ConditionTypeVariableComparison conditionType = getVariableComparison();
				if(conditionType != null)
				{
					conditionType.setComparator(comparator);
					updateElement();
				}

			}

		};

		initialValueModifyListener = new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				String value = initialValueText.getText();
				WorkflowVariable var = getSelectedIterator().getVariable();
				if(var != null && !value.equals(var.getInitialValue())) 
				{	
					var.setInitialValue(value);
					updateElement();
				}

			}
		};

		listListener = new IListListener<WorkflowVariableIteratorWrapper>(){

			public void selectionChanged(WorkflowVariableIteratorWrapper newSelection) {
				WorkflowVariableIterator newVar = null;
				if(newSelection != null) newVar = newSelection.getIterator();
				iteratorSelectionChanged(newVar);
			}

			public void elementAdded(WorkflowVariableIteratorWrapper newElement) {
				WorkflowVariableIterator it = newElement.getIterator();
				workflowVariableIteratorList.addIterator(it);
				getElement().getVariableList().addVariable(it.getVariable());
				it.getModifier().addPropertyChangeListener(VariableIteratorsSection.this);
				updateElement();
			}

			public void elementRemoved(WorkflowVariableIteratorWrapper removedElement) {
				WorkflowVariableIterator it = removedElement.getIterator();
				workflowVariableIteratorList.removeIterator(it);
				getElement().getVariableList().removeVariable(it.getVariable());
				it.getModifier().removePropertyChangeListener(VariableIteratorsSection.this);
				it.dispose();
				updateElement();
			}

			public void elementChanged(WorkflowVariableIteratorWrapper changedElement) {
				updateElement();
			}
		};
	}

	protected void addListeners()
	{
		iteratorsEditor.addListListener(listListener);
		variableTypeCombo.addSelectionListener(variableTypeSelectionListener);
		initialValueText.addModifyListener(initialValueModifyListener);
		expressionText.addModifyListener(expressionModifyListener);
		conditionText.addModifyListener(conditionModifyListener);
		conditionOperatorCombo.addSelectionListener(conditionOperatorSelectionListener);
		if(getElement() != null && iteratorsActiveListener != null) getElement().addPropertyChangeListener(iteratorsActiveListener);
	}

	protected void removeListeners()
	{
		if(getSelectedIterator() != null) getSelectedIterator().getModifier().removePropertyChangeListener(this);
		iteratorsEditor.removeListListener(listListener);
		if(variableTypeCombo != null && !variableTypeCombo.isDisposed()) variableTypeCombo.removeSelectionListener(variableTypeSelectionListener);
		if(initialValueText != null && !initialValueText.isDisposed()) initialValueText.removeModifyListener(initialValueModifyListener);
		if(expressionText != null && !expressionText.isDisposed()) expressionText.removeModifyListener(expressionModifyListener);
		if(conditionText != null &&!conditionText.isDisposed()) conditionText.removeModifyListener(conditionModifyListener);
		if(conditionOperatorCombo != null &&!conditionOperatorCombo.isDisposed()) conditionOperatorCombo.removeSelectionListener(conditionOperatorSelectionListener);
		if(getElement() != null && iteratorsActiveListener != null) getElement().removePropertyChangeListener(iteratorsActiveListener);
		
	}

	public void setInput(IWorkbenchPart part, ISelection selection) {
		super.setInput(part, selection);
		Object input = ((IStructuredSelection) selection)
		.getFirstElement();
		IElementWithVariableIterators e = (IElementWithVariableIterators) getElement();
		allowAdding = e.allowsAddingIterators();
		initIterators();
		updateEnabled();
	}

	@Override
	protected void updateEnabled()
	{
		super.updateEnabled();
		setIteratorGroupEnabled(getElement().areIteratorsUsed() && canEditModel()); 
	}

	protected void initIterators()
	{
		if(getElement() == null  || iteratorsEditor == null) return;
		removeListeners();
		iteratorsEditor.setAddingAllowed(allowAdding);
		workflowVariableIteratorList = getElement().getVariableIteratorList();
		List<WorkflowVariableIterator> iterators = workflowVariableIteratorList.getIterators();
		List<WorkflowVariableIteratorWrapper> wrappers = new ArrayList<WorkflowVariableIteratorWrapper>();
		for (WorkflowVariableIterator iterator : iterators) {
			wrappers.add(new WorkflowVariableIteratorWrapper(iterator));
		}
		iteratorsEditor.setElements(wrappers);
		WorkflowVariableIterator selected = getSelectedIterator();
		iteratorSelectionChanged(selected);

		addListeners();
	}

	protected ConditionTypeVariableComparison getVariableComparison()
	{
		WorkflowVariableIterator iterator = getSelectedIterator();
		if(iterator == null) { // shouldn't happen!
			return null;
		}
		return (ConditionTypeVariableComparison) iterator.getTerminateCondition().getSelectedType();
	}


	protected void setIteratorGroupEnabled(boolean enabled)
	{
		enabled = enabled && canEditModel(); // check whether model can be edited at all! 
		variableTypeLabel.setEnabled(enabled);
		variableTypeCombo.setEnabled(enabled);
		initialValueLabel.setEnabled(enabled);
		initialValueText.setEnabled(enabled);
		expressionLabel.setEnabled(enabled);
		expressionText.setEnabled(enabled);
		conditionLabel.setEnabled(enabled);
		conditionText.setEnabled(enabled);
		iteratorsEditor.setEnabled(enabled);
		conditionOperatorCombo.setEnabled(enabled);
	}



	public void iteratorSelectionChanged(WorkflowVariableIterator selected)
	{
		if(getSelectedIterator() != null) getSelectedIterator().getModifier().removePropertyChangeListener(this);
		if(selected != null)
		{
			selected.getModifier().addPropertyChangeListener(this);
			WorkflowVariable variable = selected.getVariable();
			WorkflowVariableModifier modifier = selected.getModifier();
			expressionText.setText(modifier.getDisplayedExpression());
			String variableType = variable.getType();
			if(WorkflowVariable.VARIABLE_TYPE_STRING.equals(variableType)) conditionOperatorCombo.setItems(IConditionType.STRING_COMPARATORS);
			else if(WorkflowVariable.VARIABLE_TYPE_INTEGER.equals(variableType)) conditionOperatorCombo.setItems(IConditionType.COMPARATORS);
			variableTypeCombo.select(WorkflowVariable.getIndexFromTypeName(variableType));
			initialValueText.setText(variable.getInitialValue());
			ConditionTypeVariableComparison conditionType = getVariableComparison();
			conditionText.setText(conditionType.getValue());
			if(conditionType != null) conditionOperatorCombo.select(conditionType.getComparator());
		}
		boolean enabled = selected != null && getElement().areIteratorsUsed() && canEditModel();
		expressionText.setEnabled(enabled);
		variableTypeCombo.setEnabled(enabled);
		initialValueText.setEnabled(enabled);
		conditionText.setEnabled(enabled);
		conditionOperatorCombo.setEnabled(enabled);
	}

	public WorkflowVariableIterator getSelectedIterator()
	{
		WorkflowVariableIteratorWrapper wrapper = iteratorsEditor.getSelectedElement();
		if(wrapper != null)
		{
			return wrapper.getIterator();

		}
		return null;
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if(IElementWithVariableIterators.PROP_ITERATOR_LIST.equals(evt.getPropertyName()))
		{
			initIterators();
		}
		else if(WorkflowVariableModifier.PROP_DISPLAYED_EXPRESSION.equals(evt.getPropertyName())) 
		{
			expressionText.removeModifyListener(expressionModifyListener);
			expressionText.setText((String) evt.getNewValue());
			expressionText.addModifyListener(expressionModifyListener);
		}
	}

	private class WorkflowVariableIteratorWrapper extends AbstractListElement 
	{

		private WorkflowVariableIterator iterator;

		public WorkflowVariableIteratorWrapper(WorkflowVariableIterator iterator)
		{
			this.iterator = iterator;
		}
		public String getName() {
			return getIterator().getName();
		}

		public void setName(String name) {
			super.setName(name);
			getIterator().setName(name);
			getIterator().getVariable().setName(name);
			updateElement();

		}
		public WorkflowVariableIterator getIterator() {
			return iterator;
		}
		public String nameValid(String name) {
			boolean valid = getElement().getDiagram().isUniqueVariableName(name);
			if(valid) return null;
			else return "A variable with that name already exists in the workflow.";
		}
		public boolean canBeRemoved() {
			return getIterator().isDisposable();
		}
		public String getId() {
			return iterator.getId();
		}
	}

	public void updateElement()
	{
		getElement().removePropertyChangeListener(this);
		getElement().setVariableIteratorList(workflowVariableIteratorList);
		getElement().addPropertyChangeListener(this);
		getElement().getDiagram().setDirty(true);
	}

	public IElementWithVariableIterators getElement()
	{
		return (IElementWithVariableIterators) super.getElement();
	}
}

