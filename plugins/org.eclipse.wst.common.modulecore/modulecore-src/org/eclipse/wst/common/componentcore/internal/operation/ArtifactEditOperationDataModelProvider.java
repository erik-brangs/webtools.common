package org.eclipse.wst.common.componentcore.internal.operation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jem.util.emf.workbench.ProjectUtilities;
import org.eclipse.jem.util.logger.proxy.Logger;
import org.eclipse.wst.common.componentcore.ArtifactEdit;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.UnresolveableURIException;
import org.eclipse.wst.common.componentcore.internal.StructureEdit;
import org.eclipse.wst.common.componentcore.internal.WorkbenchComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.frameworks.datamodel.AbstractDataModelProvider;
import org.eclipse.wst.common.frameworks.datamodel.DataModelPropertyDescriptor;
import org.eclipse.wst.common.frameworks.internal.operations.ProjectCreationDataModel;
import org.eclipse.wst.common.frameworks.internal.plugin.WTPCommonMessages;
import org.eclipse.wst.common.frameworks.internal.plugin.WTPCommonPlugin;

public class ArtifactEditOperationDataModelProvider extends AbstractDataModelProvider implements IArtifactEditOperationDataModelProperties {

	public ArtifactEditOperationDataModelProvider() {
		super();
	}

	public String[] getPropertyNames() {
		return new String[]{TYPE_ID,PROJECT_NAME, COMPONENT_NAME,PROMPT_ON_SAVE};
	}
	
	public IProject getTargetProject() {
		String projectName = (String)model.getProperty(IArtifactEditOperationDataModelProperties.PROJECT_NAME);
		if(projectName != null)
			return ProjectUtilities.getProject(projectName);
		return null;
	}
	
	public String[] addToSuperPropertyNames(String[] propertyNames,String[] superPropertyNames) {
		List allNames = new ArrayList();
		allNames.addAll(Arrays.asList(propertyNames));
		allNames.addAll(Arrays.asList(superPropertyNames));
		String[] allStrings = new String[allNames.size()];
		for(int i = 0; i < allNames.size();i++) {
			allStrings[i] = (String)allNames.get(i);
		}
		return allStrings;
		
	}
	
	public boolean propertySet(String propertyName, Object propertyValue) {
	    boolean status = super.propertySet(propertyName, propertyValue);
//	    if(COMPONENT_NAME.equals(propertyName)){
//	        WorkbenchComponent module = getWorkbenchModule();
//	        IProject proj = getProjectForGivenComponent(module);
//	        if(proj != null)
//	            setProperty(PROJECT_NAME, proj.getName());
//	    }
	    return status;
	}
	
	public Object getDefaultProperty(String propertyName) {
		if (propertyName.equals(PROMPT_ON_SAVE))
			return Boolean.FALSE;
		return super.getDefaultProperty(propertyName);
	}
	
	 /**
     * @return
     */
    public WorkbenchComponent getWorkbenchModule() {
        StructureEdit moduleCore = null;
        WorkbenchComponent module = null;
        try {
            moduleCore = StructureEdit.getStructureEditForRead(getTargetProject());
            module = moduleCore.findComponentByName(getStringProperty(COMPONENT_NAME));
        } finally {
            if (null != moduleCore) {
                moduleCore.dispose();
            }
        }
        return module;
    }
	private IProject getProjectForGivenComponent(WorkbenchComponent wbComp) {
	    IProject modProject = null;
	    try {
		    modProject = StructureEdit.getContainingProject(wbComp.getHandle());
	    } catch (UnresolveableURIException ex) {
			Logger.getLogger().logError(ex);
	    }
	    return modProject;
	}
	
	public ArtifactEdit getArtifactEditForRead(){
		WorkbenchComponent module = getWorkbenchModule(); 
		return ArtifactEdit.getArtifactEditForRead(module);
	}
	
	public IStatus validate(String propertyName) {
		IStatus result = super.validate(propertyName);
		if (result != null && !result.isOK())
			return result;
		if (propertyName.equals(PROJECT_NAME))
			return validateProjectName();
		else if (propertyName.equals(COMPONENT_NAME))
			return validateModuleName();
		return result;
	}
	
	protected IStatus validateProjectName() {
		String projectName = getStringProperty(PROJECT_NAME);
		if (projectName == null || projectName.length()==0)
			return WTPCommonPlugin.createErrorStatus(WTPCommonPlugin.getResourceString(WTPCommonMessages.PROJECT_NAME_EMPTY));
		return WTPCommonPlugin.OK_STATUS;
	}
	
	protected IStatus validateModuleName() {
		String moduleName = getStringProperty(COMPONENT_NAME);
		if (moduleName==null || moduleName.length()==0)
			return WTPCommonPlugin.createErrorStatus(WTPCommonPlugin.getResourceString(WTPCommonMessages.ERR_EMPTY_MODULE_NAME));
		return WTPCommonPlugin.OK_STATUS;
	}

	public IVirtualComponent getTargetComponent() {
		String moduleName = getStringProperty(COMPONENT_NAME);
		return ComponentCore.createComponent(getTargetProject(),moduleName);
			
		
	}
	
}