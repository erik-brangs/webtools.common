/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.wst.common.componentcore.internal.builder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.URI;
import org.eclipse.wst.common.componentcore.StructureEdit;
import org.eclipse.wst.common.componentcore.internal.WorkbenchComponent;
import org.eclipse.wst.common.componentcore.internal.util.IModuleConstants;
import org.eclipse.wst.common.componentcore.internal.util.ZipFileExporter;
import org.eclipse.wst.common.frameworks.internal.operations.WTPOperation;
import org.eclipse.wst.common.internal.emfworkbench.integration.EMFWorkbenchEditPlugin;

public class ComponentStructuralDependentBuilderOperation extends WTPOperation {
	private static String ERROR_EXPORTING_MSG = "Zip Error Message"; //$NON-NLS-1$

	private ComponentStructuralDependentBuilderDataModel depDataModel = null;

	private ZipFileExporter exporter = null;

	private List errorTable = new ArrayList(1); // IStatus

	private boolean useCompression = true;

	// private boolean createLeadupStructure = false;
	private boolean generateManifestFile = false;

	private IProgressMonitor monitor;

	private int inputContainerSegmentCount;

	/**
	 * @param operationDataModel
	 */
	public ComponentStructuralDependentBuilderOperation(ComponentStructuralDependentBuilderDataModel operationDataModel) {
		super(operationDataModel);
		depDataModel = (ComponentStructuralDependentBuilderDataModel) operationDataModel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.wst.common.frameworks.internal.operations.WTPOperation#execute(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void execute(IProgressMonitor monitor) throws CoreException, InvocationTargetException, InterruptedException {
		this.monitor = monitor;
		IPath absoluteOutputContainer = getAbsoluteOutputContainer();
		// create output container folder if it does not exist
		IFolder outputContainerFolder = createFolder(absoluteOutputContainer);
		IPath absoluteInputContainer = getAbsoluteInputContainer();

		if (absoluteOutputContainer == null || absoluteInputContainer == null)
			return;

		if (depDataModel.getBooleanProperty(ComponentStructuralDependentBuilderDataModel.DOES_CONSUME)) {
			// if consumes simply copy resources to output directory
			IResource sourceResource = getResource(absoluteInputContainer);
			if (sourceResource == null)
				return;
			ComponentStructuralBuilder.smartCopy(sourceResource, absoluteOutputContainer, new NullProgressMonitor());
		} else {
			String zipName = getZipFileName();
			IPath zipNameDestination = absoluteOutputContainer.append(zipName);
			IResource dependentZip = getResource(zipNameDestination);
			//TODO: this is needed to stop the copying of large dependent module.  Incremental build in M4 should allow for this
			//code to be removed.
			if(dependentZip == null || dependentZip.exists()) return;
			zipAndCopyResource(getResource(absoluteInputContainer), dependentZip);
			getResource(absoluteOutputContainer).refreshLocal(IResource.DEPTH_INFINITE, monitor);
		}
	}

	/**
	 * @param inputResource
	 * @param zipName
	 * @return
	 */
	private void zipAndCopyResource(IResource inputResource, IResource outputResource) throws InterruptedException {
		try {
			String osPath = outputResource.getLocation().toOSString();
			exporter = new ZipFileExporter(osPath, true, true);
			inputContainerSegmentCount = inputResource.getFullPath().segmentCount();
			exportResource(inputResource);
			exporter.finished();
		} catch (IOException ioEx) {
		}
	}

	/**
	 * @return
	 */
	private IPath getAbsoluteOutputContainer() {
		WorkbenchComponent workbenchModule = (WorkbenchComponent) depDataModel.getProperty(ComponentStructuralDependentBuilderDataModel.CONTAINING_WBMODULE);
		IFolder localWorkbenchModuleOuptutContainer = null;
		if (workbenchModule != null)
			localWorkbenchModuleOuptutContainer = StructureEdit.getOutputContainerRoot(workbenchModule);

		IPath localWorkbenchModuleOuptutContainerPath = localWorkbenchModuleOuptutContainer.getFullPath();
		URI deployPath = (URI) depDataModel.getProperty(ComponentStructuralDependentBuilderDataModel.OUTPUT_CONTAINER);
		return localWorkbenchModuleOuptutContainerPath.append(deployPath.toString()); 
	}

	/**
	 * @return
	 */
	private IPath getAbsoluteInputContainer() {
		WorkbenchComponent depWBModule = (WorkbenchComponent) depDataModel.getProperty(ComponentStructuralDependentBuilderDataModel.DEPENDENT_WBMODULE);
		if (depWBModule != null)
			return StructureEdit.getOutputContainerRoot(depWBModule).getFullPath();
		return null;
	}

	private String getZipFileName() {
		WorkbenchComponent depWBModule = (WorkbenchComponent) depDataModel.getProperty(ComponentStructuralDependentBuilderDataModel.DEPENDENT_WBMODULE);
		String typeID = depWBModule.getComponentType().getComponentTypeId();
		String zipFileName = depWBModule.getName();
		zipFileName = zipFileName.replace('.', '_');
		if(typeID == null) return zipFileName;
		if(typeID.equals(IModuleConstants.JST_APPCLIENT_MODULE) || typeID.equals(IModuleConstants.JST_EJB_MODULE) || typeID.equals(IModuleConstants.JST_UTILITY_MODULE))
		    return zipFileName + ".jar";
		else if(typeID.equals(IModuleConstants.JST_WEB_MODULE))
		    return zipFileName + ".war";
		else if(typeID.equals(IModuleConstants.JST_CONNECTOR_MODULE))
		    return zipFileName + ".rar";
		else if(typeID.equals(IModuleConstants.JST_EAR_MODULE))
		    return zipFileName + ".ear";
		return zipFileName;
	}

	/**
	 * Get resource for given absolute path
	 * 
	 * @exception com.ibm.itp.core.api.resources.CoreException
	 */
	private IResource getResource(IPath absolutePath) throws CoreException {
		IResource resource = null;
		if (absolutePath != null && !absolutePath.isEmpty()) {
			resource = getWorkspace().getRoot().getFolder(absolutePath);
			if (resource == null || !(resource instanceof IFolder)) {
				resource = getWorkspace().getRoot().getFile(absolutePath);
			}
		}
		return resource;
	}

	/**
	 * Create a folder for given absolute path
	 * 
	 * @exception com.ibm.itp.core.api.resources.CoreException
	 */
	public IFolder createFolder(IPath absolutePath) throws CoreException {
		if (absolutePath == null || absolutePath.isEmpty())
			return null;
		IFolder folder = getWorkspace().getRoot().getFolder(absolutePath);
		// check if the parent is there
		IContainer parent = folder.getParent();
		if (parent != null && !parent.exists() && (parent instanceof IFolder))
			createFolder(parent.getFullPath());
		if (!folder.exists())
			folder.create(true, true, new NullProgressMonitor());
		return folder;
	}

	/**
	 * Export the passed resource to the destination .zip
	 * 
	 * @param resource
	 *            org.eclipse.core.resources.IResource
	 * @param depth -
	 *            the number of resource levels to be included in the path including the resourse
	 *            itself.
	 */
	protected boolean exportResource(IResource resource) throws InterruptedException {
		if (!resource.isAccessible())
			return false;

		if (resource.getType() == IResource.FILE) {
			return writeResource(resource);
		} else {
			IResource[] children = null;

			try {
				children = ((IContainer) resource).members();
			} catch (CoreException e) {
				// this should never happen because an #isAccessible check is
				// done before #members is invoked
				addError(format(ERROR_EXPORTING_MSG, new Object[]{resource.getFullPath()}), e); //$NON-NLS-1$
			}

			boolean writeFolder = true;
			for (int i = 0; i < children.length; i++) {
				writeFolder = !exportResource(children[i]) && writeFolder;
			}
			if (writeFolder) {
				writeResource(resource);
			}
			return true;

		}
	}

	private boolean writeResource(IResource resource) throws InterruptedException {
		// if (resource.isDerived())
		// return false;
		String destinationName;
		IPath fullPath = resource.getFullPath();
		destinationName = fullPath.removeFirstSegments(inputContainerSegmentCount).toString();
		monitor.subTask(destinationName);

		try {
			if (resource.getType() == IResource.FILE)
				exporter.write((IFile) resource, destinationName);
			else
				exporter.writeFolder(destinationName);
		} catch (IOException e) {
			addError(format(ERROR_EXPORTING_MSG, //$NON-NLS-1$
						new Object[]{resource.getFullPath().makeRelative(), e.getMessage()}), e);
			return false;
		} catch (CoreException e) {
			addError(format(ERROR_EXPORTING_MSG, //$NON-NLS-1$
						new Object[]{resource.getFullPath().makeRelative(), e.getMessage()}), e);
			return false;
		}

		monitor.worked(1);
		return true;
	}

	/**
	 * @param ERROR_EXPORTING_MSG
	 * @param objects
	 * @return
	 */
	private String format(String pattern, Object[] arguments) {
		return MessageFormat.format(pattern, arguments);
	}

	/**
	 * Add a new entry to the error table with the passed information
	 */
	protected void addError(String message, Throwable e) {
		errorTable.add(new Status(IStatus.ERROR, EMFWorkbenchEditPlugin.ID, 0, message, e));
	}
}