/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.immport;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.query.QueryView;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.SimpleWebPartFactory;
import org.labkey.api.view.WebPartFactory;
import org.labkey.immport.security.ImmPortAdminRole;
import org.labkey.immport.view.DataFinderWebPart;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ImmPortModule extends DefaultModule
{
    public static String NAME = "ImmPort";

    public static final SearchService.SearchCategory searchCategoryStudy = new SearchService.SearchCategory("immport_study", "ImmPort Study", false);


    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public @Nullable Double getSchemaVersion()
    {
        return 19.12;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        ArrayList<WebPartFactory> list = new ArrayList<>();
        SimpleWebPartFactory factory = new SimpleWebPartFactory("ImmPort Data Finder", WebPartFactory.LOCATION_BODY, DataFinderWebPart.class, null);
        factory.addLegacyNames("ImmPort Study Finder");
        list.add(factory);
        return list;
    }

    @Override
    protected void init()
    {
        addController("immport", ImmPortController.class);
        RoleManager.registerRole(new ImmPortAdminRole());

        // override the base RExportScriptFactory to use a script based on the ImmuneSpaceR package
        QueryView.register(new ImmuneSpaceRExportScriptFactory(), true);

        ModuleProperty proxyTarget = new ModuleProperty(this, "proxyTargetUri", ModuleProperty.InputType.text, "target for RApi proxy servlet (/_rapi/)", "target uri", false);
        proxyTarget.setEditPermissions(Arrays.asList(AdminOperationsPermission.class));
        addModuleProperty(proxyTarget);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        ImmPortSchema.register(this);
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addDocumentProvider(new ImmPortDocumentProvider());
            ss.addResourceResolver("immport",new ImmPortDocumentResolver());
            ss.addSearchCategory(ImmPortModule.searchCategoryStudy);
        }

    	FolderTypeManager.get().registerFolderType(this, new ImmPortFolderType(this));

        RStudioService rstudio = ServiceRegistry.get(RStudioService.class);
        if (null != rstudio)
        {
            try
            {
                rstudio.getClass().getMethod("addRequiredLibrary", String.class).invoke(rstudio,"ImmuneSpaceR");
            }
            catch (NoSuchMethodException|SecurityException|IllegalAccessException|InvocationTargetException x)
            {
                // pass
            }
        }

        // if DifferentialExpressionAnalysis module is registered, add support for folder import/export
        Module deaModule = ModuleLoader.getInstance().getModule(DifferentialExpressionWriterFactory.MODULE_NAME);
        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != deaModule && null != fsr)
        {
            fsr.addFactories(new DifferentialExpressionWriterFactory(), new DifferentialExpressionImporterFactory());
        }
    }


    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("immport");
    }
}
