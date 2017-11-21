/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package de.fzj.unicore.rcp.gpe4eclipse.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.gef.commands.Command;

import com.intel.gpe.clients.api.JobClient;
import com.intel.gpe.clients.api.transfers.IGridFileSetTransfer;
import com.intel.gpe.clients.common.clientwrapper.ClientWrapper;
import com.intel.gpe.gridbeans.parameters.processing.ProcessingConstants;
import com.intel.gpe.util.observer.IObserver;

import de.fzj.unicore.rcp.common.Constants;
import de.fzj.unicore.rcp.gpe4eclipse.GPE4EclipseConstants;
import de.fzj.unicore.rcp.gpe4eclipse.GPEActivator;
import de.fzj.unicore.rcp.gpe4eclipse.extensions.wfeditor.GridBeanActivity;
import de.fzj.unicore.rcp.gpe4eclipse.utils.GPEPathUtils;
import de.fzj.unicore.rcp.gpe4eclipse.views.JobOutcomeView;
import de.fzj.unicore.rcp.wfeditor.model.WorkflowDiagram;
import de.fzj.unicore.rcp.wfeditor.model.structures.StructuredActivity;

/**
 * @author demuth
 */
public class FetchActivityOutcomeCommand extends Command {

	private GridBeanActivity gridBeanActivity;


	public FetchActivityOutcomeCommand(GridBeanActivity gridBeanActivity)
	{
		this.gridBeanActivity= gridBeanActivity;
	}


	/**
	 * @see org.eclipse.gef.commands.Command#execute()
	 */
	public void execute() {
		redo();
	}


	public void redo() {	


		Job todo = new Job("Downloading output files to workspace...") {
			public IStatus run(final IProgressMonitor progress)
			{
				try {
					ClientWrapper<JobClient, String> jobWrapper = new ClientWrapper<JobClient, String>(gridBeanActivity.getJobClient(),"");
					final WorkflowDiagram diagram = gridBeanActivity.getDiagram();
					String workflowID = diagram.getExecutionData().getSubmissionID();

					final Map<String,Object> processorParams = new HashMap<String, Object>();
					processorParams.put(ProcessingConstants.WORKFLOW_ID, workflowID);
					processorParams.put(GPE4EclipseConstants.ACTIVITY_ID, gridBeanActivity.getName());
					
					// set iterationId correctly in order to complete file addresses
					String iterationId = gridBeanActivity.getCurrentIterationId() == null ? "" : Constants.ITERATION_ID_SEPERATOR + gridBeanActivity.getCurrentIterationId();
					processorParams.put(GPE4EclipseConstants.ITERATION_ID, iterationId);
					String parentDir = diagram.getParentFolder() == null ? "" : diagram.getParentFolder();
					final IFolder parentFolder = diagram.getProject().getFolder(Path.fromPortableString(parentDir));
					
					IPath parentPath = GPEPathUtils.outputFileDirForPath(parentFolder.getLocation());
					parentPath = parentPath.append(gridBeanActivity.getName());
					String downloadDir = parentPath.toOSString()+File.separator; 
					StructuredActivity parent = gridBeanActivity.getParent();
					while(parent != null)
					{
						if(parent.isLoop())
						{
							iterationId = gridBeanActivity.getCurrentIterationId();
							if(iterationId.trim().length() > 0) 
							{
								downloadDir+="iteration_"+iterationId+File.separator;
							}
							break;
						}
						parent = parent.getParent();
					}
					
					processorParams.put(ProcessingConstants.DOWNLOAD_DIR,downloadDir);
					
					List<Integer> processingSteps = new ArrayList<Integer>();
					processingSteps.add(ProcessingConstants.REMOVE_UNAVAILABLE_OUTCOMES);
					processingSteps.add(ProcessingConstants.RESOLVE_OUTPUT_VARIABLES_AND_FILES);					
					processingSteps.add(ProcessingConstants.PREPARE_DOWNLOADS);
					processingSteps.add(ProcessingConstants.LET_USER_SELECT_OUTCOMES);
					processingSteps.add(ProcessingConstants.LET_USER_SELECT_DOWLOAD_DIR);
					processingSteps.add(ProcessingConstants.CLEAN_UP_AFTER_FETCHING_OUTCOMES);

					if(gridBeanActivity.getGridBeanClient().getInternalGridBean() == null)
					{
						gridBeanActivity.deserializeAndGetReady();
					}
					gridBeanActivity.getGridBeanClient().fetchOutcome(jobWrapper, new IObserver() {
						public void observableUpdate(Object arg0, Object arg1) {	
							try {
								
								if(arg1 instanceof Throwable)
								{
									((Throwable) arg1).printStackTrace();
									GPEActivator.log(Status.ERROR,"Unable to fetch output files for activity "+gridBeanActivity.getName(),new Exception((Throwable) arg1));
									return;
								}
								
								IGridFileSetTransfer transfer = (IGridFileSetTransfer) processorParams.get(ProcessingConstants.FILE_TRANSFERS);
								if(transfer.getFiles().size() > 0)
								{
									JobOutcomeView view = (JobOutcomeView) gridBeanActivity.getGridBeanClient().openGridBeanOutputPanel().getComponent();
									String name;
									try {
										name = gridBeanActivity.getGridBeanClient().getGridBeanJob().getName();
										name = name.replace(Constants.CURRENT_TOTAL_ITERATOR, gridBeanActivity.getCurrentIterationId());
										view.setJobName(name);
									} catch (Exception e) {

									} 
									
									parentFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
								}
				
							} catch (CoreException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						}},processorParams,processingSteps );
					return Status.OK_STATUS;

				} catch (Exception e) {
					GPEActivator.log("Unable to fetch outcomes for activity "+gridBeanActivity.getName(),e);
					return Status.CANCEL_STATUS;
				}
			}
		};
		todo.schedule();
	}


	public boolean canUndo() {
		return false;
	}

	/**
	 * @see org.eclipse.gef.commands.Command#undo()
	 */
	public void undo() {

	}

}
