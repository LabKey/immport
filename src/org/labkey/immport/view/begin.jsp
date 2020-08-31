<%
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
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.immport.ImmPortController" %>
<%@ page import="org.labkey.immport.ImmPortModule" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
    URLHelper dataFinder = ImmPortModule.getDataFinderURL(getContainer(), getUser());
%>
<h3>Data loading</h3>

<p>
<b>To load new ImmPort archive</b><br>
<%=link("Import Archive", new ActionURL(ImmPortController.ImportArchiveAction.class, c))%><br>
<%=link("Populate cube", new ActionURL(ImmPortController.PopulateCubeAction.class, c))%><br>
<%= null==dataFinder ? HtmlString.EMPTY_STRING : link("Data Finder", dataFinder) %><br>
<%=link("Public/Restricted Studies", new ActionURL(ImmPortController.RestrictedStudiesAction.class, c))%><br>
</p>
<p>
<b>Load or refresh data in a new study</b><br>
<%=link("Copy datasets for one study in this folder", new ActionURL("immport", "copyImmPortStudy", c))%><br>
</p>
<p>
<b>Reload all data (should be executed from the /Studies container):</b><br>
<%=link("Copy datasets for multiple child studies", new ActionURL("immport", "reimportStudies", c))%><br>
</p>
<h3>Post loading tasks</h3>
  <%=link("Hide empty datasets", new ActionURL("study", "datasetVisibility", c))%><br>
  <%=link("Highligh study", new ActionURL("study", "manageStudyProperties", c))%><br>
  <%=link("Update modules", new ActionURL("admin", "folderType", c).addParameter("tabId", "folderType"))%><br>
</p>

<h3>Expression Matrix publish/import</h3>
  <%=link("Export selected expression matrices", new ActionURL(ImmPortController.PublishExpressionMatrixAction.class, c))%><br>
  <%=link("Import published expression matrices", new ActionURL(ImmPortController.ImportExpressionMatrixAction.class, c))%><br>
