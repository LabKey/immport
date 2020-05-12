<%
/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.QuerySchema" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.immport.ImmPortController" %>
<%@ page import="org.labkey.immport.data.StudyPersonnelBean" %>
<%@ page import="org.labkey.immport.data.StudyPubmedBean" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("dataFinder.css");
        dependencies.add("immport/hipc.css");
    }
%>
<%
    JspView<ImmPortController.StudyDetails> me = (JspView) HttpView.currentView();
    ViewContext context = HttpView.currentContext();
    Container c = context.getContainer();
    ImmPortController.StudyDetails details = me.getModelBean();
    String descriptionHTML;
    if (!StringUtils.isEmpty(details.study.getDescription()))
        descriptionHTML= details.study.getDescription();
    else
        descriptionHTML = PageFlowUtil.filter(details.study.getBrief_description());

    ActionURL studyUrl = null;
    if (!c.isRoot())
    {
        Container p = c.getProject();
        QuerySchema s = DefaultSchema.get(context.getUser(), p).getSchema("study");
        TableInfo sp = s.getTable("StudyProperties", ContainerFilter.Type.AllInProject.create(s));
        Collection<Map<String, Object>> maps = new TableSelector(sp).getMapCollection();
        for (Map<String, Object> map : maps)
        {
            Container studyContainer = ContainerManager.getForId((String) map.get("container"));
            if (null == studyContainer)
                continue;
            String study_accession = (String)map.get("study_accession");
            String name = (String)map.get("Label");
            if (null == study_accession && StringUtils.startsWith(name,"SDY"))
                study_accession = name;
            if (null == study_accession && StringUtils.startsWith(studyContainer.getName(),"SDY"))
                study_accession = studyContainer.getName();
            if (StringUtils.equalsIgnoreCase(details.study.getStudy_accession(), study_accession))
            {
                studyUrl = studyContainer.getStartURL(context.getUser());
                break;
            }
        }
    }

    Map<String, String> linkProps = new HashMap<>();
    linkProps.put("target", "_blank");
%>

<div id="demographics" class="study-demographics">
<h2 class="study-accession"><% if (null!=studyUrl) {%><a style="color:#fff" href="<%=h(studyUrl)%>"><%}%><%=h(details.study.getStudy_accession())%><% if (null!=studyUrl) {%></a><%}%></h2>
<div id="demographics-content">
<h3 class="study-title"><%=h(details.study.getOfficial_title())%></h3>
    <div><%
        if (null != details.personnel)
        {
            for (StudyPersonnelBean p : details.personnel)
            {
                if ("Principal Investigator".equals(p.getRole_in_study()))
                {
                    %><div>
                        <span class="immport-highlight study-pi"><%=h(p.getHonorific())%> <%=h(p.getFirst_name())%> <%=h(p.getLast_name())%></span>
                        <span class="immport-highlight study-organization" style="float: right"><%=h(p.getOrganization())%></span>
                    </div><%
                }
            }
        }
        %><div class="study-description"><%=text(descriptionHTML)%></div>
        <div class="study-papers"><%
        if (null != details.pubmed && details.pubmed.size() > 0)
        {
            %><span class="immport-highlight">Papers</span><%
            for (StudyPubmedBean pub : details.pubmed)
            {
                %><p><span style="font-size:80%;"><span class="pub-journal" style="text-decoration:underline;"><%=h(pub.getJournal())%></span> <span class="pub-year"><%=h(pub.getYear())%></span></span><br/><%
                %><span class="pub-title"><%=h(pub.getTitle())%></span><%
                    if (!StringUtils.isEmpty(pub.getPubmed_id()))
                    {
                        %><br/><%=link("PubMed").href("http://www.ncbi.nlm.nih.gov/pubmed/?term=" + pub.getPubmed_id()).onClick(null).id(null).attributes(linkProps)%><%
                    }
                %></p><%
            }
        }
        %></div>
    </div>

    <%=link("ImmPort").href("https://immport.niaid.nih.gov/immportWeb/clinical/study/displayStudyDetails.do?itemList=" + details.study.getStudy_accession()).onClick(null).id(null).attributes(linkProps)%><br>
    <% if (null != studyUrl) { %>
        <%= link("View study " + details.study.getStudy_accession(), studyUrl).onClick(null).id(null).attributes(linkProps)%><br>
    <% } %>
</div>
</div>
