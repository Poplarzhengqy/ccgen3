/*********************************************************************************
 * Copyright (c) 2006 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
package org.chemomentum.rcp.wfeditor.properties;

import org.chemomentum.rcp.wfeditor.model.DataSourceActivity;
import org.chemomentum.rcp.wfeditor.model.ForEachDataSourceActivity;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertyConstants;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import de.fzj.unicore.rcp.common.utils.ValidityChangeEvent;
import de.fzj.unicore.rcp.gpe4eclipse.gridbeans.stageing.StageListFactory;
import de.fzj.unicore.rcp.gpe4eclipse.gridbeans.stageing.in.StageInList;
import de.fzj.unicore.rcp.gpe4eclipse.gridbeans.stageing.out.StageOutList;
import de.fzj.unicore.rcp.wfeditor.model.variables.WorkflowVariable;
import de.fzj.unicore.rcp.wfeditor.properties.AbstractSection;
import de.fzj.unicore.rcp.wfeditor.ui.IConditionTypeControlProducer;

/**
 * @author demuth
 *
 */
public class ForEachDataSourceSection extends AbstractSection {


	private IConditionTypeControlProducer controlProducer;

	protected Composite loopSettingsParent;
	protected CCombo iterationModeCombo;
	protected Text maxNumTasksText;
	protected Label errorLabel;
	protected SelectionListener selectionListener;
	protected ModifyListener modifyListener;


	public void createControls(Composite parent,
			TabbedPropertySheetPage tabbedPropertySheetPage) {
		super .createControls(parent, tabbedPropertySheetPage);

		FormData data;
		Composite flatForm= getWidgetFactory()
		.createFlatFormComposite(parent);


		Group composite = getWidgetFactory().createGroup(flatForm, "For-Loop settings");
		data = new FormData();
		data.left = new FormAttachment(0, ITabbedPropertyConstants.HSPACE);
		data.right = new FormAttachment(100, -ITabbedPropertyConstants.HSPACE);
		data.top = new FormAttachment(0, ITabbedPropertyConstants.VSPACE);
		data.bottom = new FormAttachment(100, -ITabbedPropertyConstants.VSPACE);
		composite.setLayoutData(data);
		composite.setLayout(new FormLayout());
		controls.add(composite);

		loopSettingsParent = getWidgetFactory().createComposite(composite);

		data = new FormData();
		data.left = new FormAttachment(0, ITabbedPropertyConstants.HSPACE);
		data.right = new FormAttachment(100, -ITabbedPropertyConstants.HSPACE);
		data.top = new FormAttachment(0, ITabbedPropertyConstants.VSPACE);
		data.bottom = new FormAttachment(100, -ITabbedPropertyConstants.VSPACE);

		loopSettingsParent.setLayoutData(data);

		GridLayout layout = new GridLayout(2,false);
		loopSettingsParent.setLayout(layout);
		controls.add(loopSettingsParent);

		GridData gridData = new GridData(SWT.LEFT,SWT.CENTER,false,false);
		Label label = getWidgetFactory().createLabel(loopSettingsParent ,"Iterate over:");
		label.setLayoutData(gridData);
		controls.add(label);

		iterationModeCombo = getWidgetFactory().createCCombo(loopSettingsParent);
		gridData = new GridData(SWT.LEFT,SWT.CENTER,false,false);
		iterationModeCombo.setLayoutData(gridData);
		controls.add(iterationModeCombo);

		Label maxNumTasksLabel = getWidgetFactory().createLabel(loopSettingsParent, "Number of parallel tasks:");
		gridData = new GridData(SWT.LEFT,SWT.CENTER,false,false);
		maxNumTasksLabel.setLayoutData(gridData);
		controls.add(maxNumTasksLabel);

		maxNumTasksText = getWidgetFactory().createText(loopSettingsParent, "",SWT.BORDER|SWT.LEFT);
		gridData = new GridData(SWT.LEFT,SWT.CENTER,true,false);
		gridData.widthHint = 100;
		maxNumTasksText.setLayoutData(gridData);
		controls.add(maxNumTasksText);

		errorLabel = getWidgetFactory().createLabel(loopSettingsParent, "");
		gridData = new GridData(SWT.LEFT,SWT.CENTER,true,true);
		gridData.horizontalSpan = layout.numColumns;
		errorLabel.setLayoutData(gridData);
		errorLabel.setForeground(errorLabel.getDisplay().getSystemColor(SWT.COLOR_RED));
		errorLabel.setVisible(false);
		createListeners();
	}

	public void setInput(IWorkbenchPart part, ISelection selection) {
		super.setInput(part, selection);
		init();
		updateEnabled();
	}

	protected void init()
	{
		if(getElement() == null  || loopSettingsParent == null) return;
		removeListeners();
		DataSourceActivity activity = getActivity();
		if(activity == null) return;
		iterationModeCombo.setItems(getActivity().getIterationModeStrings());
		iterationModeCombo.select(getActivity().getSelectedIterationMode());
		Integer maxNumTasks = (Integer) getActivity().getPropertyValue(ForEachDataSourceActivity.PROP_MAX_PARALLEL_TASKS);
		String maxNumTasksString = maxNumTasks == null ? "" : String.valueOf(maxNumTasks);
		maxNumTasksText.setText(maxNumTasksString);
		addListeners();
	}


	public ForEachDataSourceActivity getActivity()
	{
		return (ForEachDataSourceActivity) getElement();
	}


	public void dispose() {
		removeListeners();
	}




	public boolean checkInputValid()
	{
		return true;

	}



	public void updateParent()
	{
//		getConditionalElement().setPropertyValue(Condition.PROP_CONDITION, getCondition());
//		getElement().getDiagram().setDirty(true);
	}

	public void validityChanged(ValidityChangeEvent e) {
		// TODO Auto-generated method stub

	}


	protected void createListeners()
	{
		selectionListener = new SelectionListener()
		{
			public void widgetDefaultSelected(SelectionEvent e) {
				updateVariableType();
			}

			public void widgetSelected(SelectionEvent e) {
				updateVariableType();
			}

			private void updateVariableType()
			{
				int selected = iterationModeCombo.getSelectionIndex();
				getActivity().setSelectedIterationMode(selected);
				getActivity().getDiagram().setDirty(true);
			}

		};

		modifyListener = new ModifyListener(){
			public void modifyText(ModifyEvent e) {
				String errorMsg = null;
				String s = maxNumTasksText.getText();

				try {
					if(s != null && s.trim().length() > 0)
					{
						Integer value = Integer.parseInt(s);
						if(value <= 0) errorMsg = "Maximum number of parallel tasks must be positive";
						Integer oldValue = (Integer) getActivity().getPropertyValue(ForEachDataSourceActivity.PROP_MAX_PARALLEL_TASKS);
						boolean changed = oldValue == null ? value != null : !oldValue.equals(value);
						if(changed)
						{
							getActivity().setPropertyValue(ForEachDataSourceActivity.PROP_MAX_PARALLEL_TASKS, value);
							getActivity().getDiagram().setDirty(true);
						}
					}
				} catch (Exception ex) {
					errorMsg = "Maximum number of parallel tasks must be an integer value";
				}
				if(errorMsg != null)
				{
					errorLabel.setText(errorMsg);
					errorLabel.setVisible(true);
					errorLabel.pack();
				}
				else errorLabel.setVisible(false);
			}
		};

	}

	protected void addListeners()
	{

		if(iterationModeCombo != null) iterationModeCombo.addSelectionListener(selectionListener);
		maxNumTasksText.addModifyListener(modifyListener);
	}

	protected void removeListeners()
	{
		if(iterationModeCombo != null && !iterationModeCombo.isDisposed()) iterationModeCombo.removeSelectionListener(selectionListener);
		if(!maxNumTasksText.isDisposed()) maxNumTasksText.removeModifyListener(modifyListener);
	}

}
