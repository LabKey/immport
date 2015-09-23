<%
    /*
     * Copyright (c) 2014-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerFilterable" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.QuerySchema" %>
<%@ page import="org.labkey.api.util.HeartBeat" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.immport.ImmPortController" %>
<%@ page import="org.labkey.immport.data.StudyBean" %>
<%@ page import="org.labkey.immport.view.DataFinderWebPart" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("clientapi/ext4"));
        resources.add(ClientDependency.fromPath("query/olap.js"));
        resources.add(ClientDependency.fromPath("angular"));
        resources.add(ClientDependency.fromPath("immport/dataFinder.js"));

        resources.add(ClientDependency.fromPath("immport/ParticipantGroup.js"));
        return resources;
    }
%>
<%
    DataFinderWebPart me = (DataFinderWebPart)HttpView.currentView();
    ViewContext context = HttpView.currentContext();
    ArrayList<StudyBean> studies = new SqlSelector(DbSchema.get("immport"),"SELECT study.*, P.title as program_title, pi.pi_names\n" +
            "FROM immport.study " +
            "LEFT OUTER JOIN immport.workspace W ON study.workspace_id = W.workspace_id\n" +
            "LEFT OUTER JOIN immport.contract_grant C ON W.contract_id = C.contract_grant_id\n" +
            "LEFT OUTER JOIN immport.program P on C.program_id = P.program_id\n" +
            "LEFT OUTER JOIN\n" +
            "\t(\n" +
            "\tSELECT study_accession, array_to_string(array_agg(first_name || ' ' || last_name),', ') as pi_names\n" +
            "\tFROM immport.study_personnel\n" +
            "\tWHERE role_in_study ilike '%principal%'\n" +
            "\tGROUP BY study_accession) pi ON study.study_accession = pi.study_accession\n").getArrayList(StudyBean.class);

    String hipcImg = request.getContextPath() + "/immport/hipc.png";
    Collections.sort(studies, (o1, o2) -> {
        String a = o1.getStudy_accession();
        String b = o2.getStudy_accession();
        return Integer.parseInt(a.substring(3)) - Integer.parseInt(b.substring(3));
    });

    Map<String,StudyBean> mapOfStudies = new TreeMap<>();
    for (StudyBean sb : studies)
        mapOfStudies.put(sb.getStudy_accession(), sb);
%>

<style>
    DIV.labkey-data-finder-outer {
        min-height:100px;
        min-width:400px;
    }

    .labkey-data-finder-inner
    {
        background-color:#ffffff;
        height:100%;
        width:100%;
    }

    TABLE.labkey-data-finder
    {
        height: 450px;
        width:100%;
        padding:3pt;
    }

    .labkey-filter-message
    {
        float:left;
    }

    /* labkey-study-card */
    .labkey-study-card-highlight
    {
        color:black;
        font-variant:small-caps;
    }


    .labkey-study-card-summary,
    .labkey-study-card-accession
    {
        float:left;
    }

    .labkey-study-card-goto,
    .labkey-study-card-pi
    {
        float:right;
    }
    .labkey-study-card-divider,
    .labkey-study-card-description
    {
        clear:both;
    }
    DIV.labkey-study-card
    {
        background-color:#F8F8F8;
        border:1pt solid #AAAAAA;
        padding: 5pt;
        margin: 5pt;
        float:left;
        position:relative;
        width:160pt;
        height:140pt;
        overflow-y:hidden;
    }

    DIV.loaded,
    SPAN.loaded
    {
        background-color:rgba(81, 158, 218, 0.2);
    }
    SPAN.hipc-label
    {
        border-radius: 10px;
        border: solid 1px #AAAAAA;
        background: #FFFFFF;
        padding: 6px;
    }

    DIV.hipc-label
    {
        width:100%;
        position:absolute;
        bottom:0;
        left:0;
        text-align:center;
    }

    TD.study-panel
    {
        vertical-align: top;
    }

    TD.study-panel > DIV
    {
        height:100%;
        overflow-y:scroll;
    }

    SPAN.labkey-study-search
    {
        position: relative;
    }

    /* search area */
    DIV.search-box
    {
        float:left;
        padding:10pt;
    }
    INPUT.search-box
    {
        width:240pt;
    }

    TD.selection-panel
    {
        vertical-align: text-top;
        width:220pt;
        height:100%;
    }

    TD.selection-panel > DIV
    {
        height:100%;
        overflow-y:auto;
    }

    TD.help-links
    {
        vertical-align: text-top;
        text-align: right;
    }

    DIV.summary LI.member
    {
        clear:both;
        padding:2pt;
        margin:1px 0px 1px 0px;
        border:1px solid #ffffff;
        height:14pt;
        position:relative;
        width:100%;
    }

    /* facets */

    .clear-filter
    {
        float:right;
    }

    DIV.facet
    {
        max-width:200pt;
        border:solid 2pt rgb(220, 220, 220);
    }

    .active
    {
        cursor:pointer;
    }
    DIV.facet-header
    {
        padding:4pt;
        background-color: rgb(240, 240, 240);
    }

    DIV.facet-header .facet-caption
    {
        font-weight:400;
    }

    DIV.facet-header .labkey-filter-options
    {
        font-size: 11px;
        padding: 3px 0px 0px 20px;
    }

    DIV.facet UL
    {
        list-style-type:none;
        padding-left:0;
        padding-right:5pt;
        margin:0;
    }
    DIV.facet LI.member
    {
        clear:both;
        cursor:pointer;
        padding:2pt;
        margin:1px 0px 1px 0px;
        border:1px solid #ffffff;
        height:14pt;
        position:relative;
        width:100%;
    }
    DIV.facet.collapsed LI.member
    {
        display:none;
    }
    DIV.facet.collapsed .fa-plus-square
    {
        float:left;
        display:inline;
    }
    DIV.facet.collapsed .fa-minus-square
    {
        float:left;
        display:none;
    }
    DIV.expanded  .fa-plus-square
    {
        float:left;
        display:none;
    }
    DIV.expanded  .fa-minus-square
    {
        float:left;
        display:inline;
    }
    DIV.facet.collapsed LI.member.selected-member
    {
        display:block;
    }
    DIV.facet LI.member:hover SPAN.bar
    {
        background-color:rgba(81, 158, 218, 0.2);
    }
    DIV.facet LI.empty-member
    {
        color:#888888;
    }
    LI.member .member-indicator
    {
        position:relative;
        display:inline-block;
        width:16px;
        z-index:2;
    }
    .member-indicator.selected:after
    {
        content:"\002713"
    }
    .member-indicator.selected:hover:after
    {
        content:"x";
    }
    .member-indicator.not-selected:after
    {
        content:"\0025FB"
    }
    .member-indicator.not-selected:hover:after
    {
        content:"\002713"
    }
    .member-indicator.not-selected.none-selected:after
    {
        content:"\002713"; color:lightgray;
    }
    LI.member .member-name
    {
        display:inline-block;
        position:relative;
        max-width:150pt;
        overflow-x: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        z-index:2;
    }
    LI.member .member-count
    {
        position:relative;
        float:right;
        z-index:2;
    }

    LI.member SPAN.bar-selected
    {
        background-color:rgba(81, 158, 218, 0.2);
    }

    LI.member .bar
    {
        height:14pt;
        background-color:#DEDEDE;
        position:absolute; right:0;
        z-index:0;
    }

    /* filter type popup */
    DIV.labkey-filter-popup
    {
        position: absolute;
        min-width: 80px;
        margin: 2px 0 0;
        list-style: none;
        background-color: white;
        border-color:#b4b4b4;
        border-width: 1px;
        border-style: solid;
        box-shadow: rgb(136, 136, 136) 0px 0px 6px;
        z-index: 100;
        -webkit-padding-start: 0px
    }


    .labkey-filter-popup > .labkey-dropdown-menu
    {
        display: block;
        min-width: 130px;
    }

    /* menus */
    .nav
    {
        margin: 0;
        padding-left: 0;;
        list-style: none;
    }

    ul.nav
    {
        display:block;
    }

    .navbar-nav > li
    {
        float: left;
    }

    .navbar-nav
    {
        float: left;
        margin: 0;
    }

    .navbar-nav > li > .labkey-dropdown-menu
    {
        margin-top : 0;
    }

    .labkey-filter-options > a.inactive
    {
        color: black;
        cursor: default;
    }

    .labkey-dropdown > a.labkey-disabled-text-link
    {
        color: #C0C0C0;
        cursor: default;
    }

    .labkey-dropdown:hover > .labkey-dropdown-menu-active
    {
        display: block;
    }

    .labkey-dropdown-menu > li {
        margin-right : 0px;
    }

    .labkey-dropdown-menu > li > a
    {
        color: black;
    }

    .labkey-dropdown-menu > li > a.inactive
    {
        color: #C0C0C0;
        cursor: default;
    }

    .labkey-dropdown-menu > li.inactive:hover
    {
        color: #C0C0C0;
        cursor: default;
        background-color: white;
        border-style: none;
        background-image: none;
    }

    .labkey-dropdown-menu > li:hover
    {
        background-image: none;
        background-color: #eaeaea;
        background-image: -webkit-gradient(linear, 50% 0%, 50% 100%, color-stop(0%, #f2f2f2), color-stop(100%, #e0e0e0));
        background-image: -webkit-linear-gradient(top, #f2f2f2, #e0e0e0);
        background-image: -moz-linear-gradient(top, #f2f2f2, #e0e0e0);
        background-image: -o-linear-gradient(top, #f2f2f2, #e0e0e0);
        background-image: linear-gradient(top, #f2f2f2, #e0e0e0);
        border-color: #b4b4b4;
        border-width: 1px;
        border-style: solid;
        padding: 0;
    }

    .labkey-dropdown-menu {
        position: absolute;
        display: none;
        min-width: 80px;
        margin: 2px 0 0;
        list-style: none;
        background-color: white;
        border-color:#b4b4b4;
        border-width: 1px;
        border-style: solid;
        box-shadow: rgb(136, 136, 136) 0px 0px 6px;
        z-index: 100;
        -webkit-padding-start: 0px
    }

    #manageMenu > a {
        color:black;
        padding-right: 5px;
    }

    .menu-item-link {
        padding: 0 8px 0 8px;
    }
</style>


<div id="dataFinderWrapper" class="labkey-data-finder-outer">
<div id="dataFinderApp" class="x-hidden labkey-data-finder-inner" ng-app="dataFinderApp" ng-controller="dataFinder">

    <table border=0 class="labkey-data-finder">
        <tr>
            <td>
                <div ng-controller="SubjectGroupController">
                    <div>{{currentGroup.id != null ? "Saved group: ": ""}}{{currentGroup.label}}</div>

                    <div class="navbar navbar-default ">
                        <ul class="nav navbar-nav">
                            <li id="manageMenu" class="labkey-dropdown" ng-mouseover="openMenu($event, true)">
                                <a href="#"><i class="fa fa-cog"></i></a>
                                <ul class="labkey-dropdown-menu" ng-show="!isGuest">
                                    <li class="x4-menu-item-text"><a class="menu-item-link x4-menu-item-link" href="<%=new ActionURL("study", "manageParticipantCategories", getContainer()).toHString()%>">Manage Groups</a></li>
                                </ul>
                            </li>
                            <li id="loadMenu" class="labkey-dropdown" >
                                <a ng-class="{'labkey-text-link' : loadedStudiesShown(), 'labkey-disabled-text-link': !loadedStudiesShown()} " class="no-arrow" style="margin-right: 0.8em" href="#" ng-mouseover="openMenu($event, false)">Load <i class="fa fa-caret-down"></i></a>
                                <ul class="labkey-dropdown-menu" ng-show="loadedStudiesShown() && groupsAvailable()" >
                                    <li class="x4-menu-item-text" ng-repeat="group in groupList">
                                        <a class="menu-item-link x4-menu-item-link" ng-click="applySubjectGroupFilter(group, $event)">{{group.label}}</a>
                                    </li>
                                </ul>
                            </li>
                            <li id="saveMenu" class="labkey-dropdown">
                                <a ng-class="{'labkey-text-link' : loadedStudiesShown(), 'labkey-disabled-text-link': !loadedStudiesShown()} " class="no-arrow" style="margin-right: 0.8em" href="#" ng-mouseover="openMenu($event, false)" ng-mouseleave="closeMenu($event)">Save <i class="fa fa-caret-down"></i> </a>
                                <ul class="labkey-dropdown-menu" ng-if="!isGuest && loadedStudiesShown()">
                                    <li class="x4-menu-item-text" ng-repeat="opt in saveOptions" ng-class="{'inactive' : !opt.isActive}">
                                        <a class="menu-item-link x4-menu-item-link" ng-class="{'inactive' : !opt.isActive}" ng-click="saveSubjectGroup(opt.id, $event)">{{opt.label}}</a>
                                    </li>
                                </ul>
                                <ul class="labkey-dropdown-menu" ng-if="isGuest">
                                    <li class="x4-menu-item-text">
                                        <span class="menu-item-link x4-menu-item-link">You must be logged in to save a group.</span>
                                    </li>
                                </ul>
                            </li>
                        </ul>
                        <span class="clear-filter active" ng-show="hasFilters()" ng-click="clearAllFilters(true);">[clear all]</span>
                    </div>

                </div>

            </td>
            <td class="help-links">
                <span id="message" class="labkey-filter-message" ng-if="!loadedStudiesShown()">No data are available for participants since you are viewing unloaded studies.</span>
                <%=textLink("quick help", "#", "start_tutorial()", "showTutorial")%>
                <%=textLink("Export Study Datasets", ImmPortController.ExportStudyDatasetsAction.class)%><br>
            </td>
        </tr>
        <tr>
            <td class="selection-panel">
                <div id="selectionPanel">
                    <div>
                        <div class="facet" id="summaryArea" >
                            <div class="facet-header"><span class="facet-caption">Summary</span></div>
                            <ul>
                                <li class="member" style="cursor: default">
                                    <span class="member-name">Studies</span>
                                    <span class="member-count">{{dimStudy.summaryCount}}</span>
                                </li>
                                <li class="member" style="cursor: default">
                                    <span class="member-name">Subjects</span>
                                    <span class="member-count">{{formatNumber(dimSubject.allMemberCount||0)}}</span>
                                </li>
                            </ul>
                        </div>

                        <span id="facetPanel">
                            <div ng-include="'/facet.html'" ng-repeat="dim in [dimSpecies,dimCondition,dimType,dimCategory,dimAssay,dimTimepoint,dimGender,dimRace,dimAge]"></div>
                        </span>
                    </div>
                </div>
            </td>
            <td class="study-panel">
                <div id="studypanel" ng-class="{'x-hidden':(activeTab!=='Studies')}">
                    <div>
                        <span class="search-box">
                            <i class="fa fa-search"></i>&nbsp;
                            <input placeholder="Studies" id="searchTerms" name="q" class="search-box"  ng-model="searchTerms" ng-change="onSearchTermsChanged()">
                        </span>
                        <span class="labkey-study-search">
                            <select ng-model="studySubset" name="studySubsetSelect" ng-change="onStudySubsetChanged()">
                                <option ng-repeat="option in subsetOptions" value="{{option.value}}" ng-selected="{{option.value == studySubset}}">{{option.name}}</option>
                            </select>
                        </span>
                        <span class="study-search">{{searchMessage}}</span>
                    </div>


                    <div ng-include="'/studycard.html'" ng-repeat="study in studies | filter:countForStudy"></div>
                </div>
            </td>
        </tr>
    </table>



<div id="studyPopup"></div>

<div id="filterPopup" class="labkey-filter-popup" style="top:{{filterChoice.y}}px; left:{{filterChoice.x}}px;" ng-if="filterChoice.show" ng-mouseleave="filterChoice.show = false">
    <ul class="labkey-dropdown-menu" ng-if="filterChoice.options.length > 1">
        <li class="x4-menu-item-text" ng-repeat="option in filterChoice.options">
            <a class="menu-item-link x4-menu-item-link" ng-click="setFilterType(filterChoice.dimName,option.type)">{{option.caption}}</a>
        </li>
    </ul>
</div>

<!--
			templates
 -->

<script type="text/ng-template" id="/studycard.html">
    <div class="labkey-study-card" ng-class="{hipc:study.hipc_funded, loaded:study.loaded}">
        <span class="labkey-study-card-highlight labkey-study-card-accession">{{study.study_accession}}</span>
        <span class="labkey-study-card-highlight labkey-study-card-pi">{{study.pi}}</span>
        <hr class="labkey-study-card-divider">
        <div>
            <a class="labkey-text-link labkey-study-card-summary" ng-click="showStudyPopup(study.study_accession)" title="click for more details">view summary</a>
            <a class="labkey-text-link labkey-study-card-goto" ng-if="study.loaded && study.url" href="{{study.url}}">go to study</a>
        </div>
        <div class="labkey-study-card-description">{{study.title}}</div>
        <div ng-if="study.hipc_funded" class="hipc-label" ><span class="hipc-label">HIPC</span></div>
    </div>
</script>


<script type="text/ng-template" id="/facet.html">
    <div id="group_{{dim.name}}" class="facet"
         ng-class="{expanded:dim.expanded, collapsed:!dim.expanded, noneSelected:(0==dim.filters.length)}">
        <div class="facet-header">
            <div class="facet-caption active" ng-click="dim.expanded=!dim.expanded" ng-mouseover="filterChoice.show = false">
                <i class="fa fa-plus-square"></i>
                <i class="fa fa-minus-square"></i>
                &nbsp;
                <span>{{dim.caption || dim.name}}</span>
                <span ng-if="dim.filters.length" class="clear-filter active" ng-click="selectMember(dim.name,null,$event);">[clear]</span>
            </div>
            <div class="labkey-filter-options" ng-if="dim.filters.length > 1 && dim.filterOptions.length > 0" >
                <a ng-click="displayFilterChoice(dim.name, $event)" ng-mouseover="displayFilterChoice(dim.name,$event);"  class="x4-menu-item-text" ng-class="{inactive: dim.filterOptions.length < 2}" href="#">{{dim.filterCaption}} <i ng-if="dim.filterOptions.length > 1" class="fa fa-caret-down"></i></a>
            </div>
        </div>
        <ul>
            <li ng-repeat="member in dim.members" id="m_{{dim.name}}_{{member.uniqueName}}" style="position:relative;" class="member"
                 ng-class="{'selected-member':member.selected, 'empty-member':(!member.selected && member.count == 0)}"
                 ng-click="selectMember(dim.name,member,$event)">
                <span class="active member-indicator" ng-class="{selected:member.selected, 'none-selected':!dim.filters.length, 'not-selected':!member.selected}" ng-click="toggleMember(dim.name,member,$event)">
                </span>
                <span class="member-name">{{member.name}}</span>
                &nbsp;
                <span class="member-count">{{formatNumber(member.count)}}</span>
                <span ng-class="{'bar-selected':member.selected}" class="bar" ng-show="member.count" style="width:{{member.percent}}%;"></span>
            </li>
        </ul>
    </div>
</script>

</div>
</div>


<%--
			controller
 --%>
<%-- N.B. This is not robust enough to have two finder web parts on the same page --%>

<script>
var studyData = [<%
    String comma = "\n  ";
    for (StudyBean study : studies)
    {
        %><%=text(comma)%>[<%=q(study.getStudy_accession())%>,<%=text(study.getStudy_accession().substring(3))%>,<%=q(StringUtils.defaultString(study.getBrief_title(),study.getOfficial_title()))%>,<%=q(study.getPi_names())%>]<%
        comma = ",\n  ";
    }
%>];


var loaded_studies = {
<%
Container c = context.getContainer();
if (!c.isRoot())
{
    comma = "\n";
    Container p = c.getProject();
    QuerySchema s = DefaultSchema.get(context.getUser(), p).getSchema("study");
    TableInfo sp = s.getTable("StudyProperties");
    if (sp.supportsContainerFilter())
    {
        ContainerFilter cf = new ContainerFilter.AllInProject(context.getUser());
        ((ContainerFilterable)sp).setContainerFilter(cf);
    }
    Collection<Map<String, Object>> maps = new TableSelector(sp).getMapCollection();

    long now = HeartBeat.currentTimeMillis();

    for (Map<String, Object> map : maps)
    {
        Container studyContainer = ContainerManager.getForId((String)map.get("container"));
        if (null == studyContainer)
            continue;
        ActionURL url = studyContainer.getStartURL(context.getUser());
        String study_accession = (String)map.get("study_accession");
        String name = (String)map.get("Label");
        Date until = (Date)map.get("highlight_until");
        boolean highlight = null != until && until.getTime() > now;
        if (StringUtils.isEmpty(study_accession) && StringUtils.startsWith(name,"SDY"))
            study_accession = name;
        if (null != study_accession)
        {
            if (StringUtils.endsWithIgnoreCase(study_accession," Study"))
                study_accession = study_accession.substring(0,study_accession.length()-6).trim();
            StudyBean bean = mapOfStudies.get(study_accession);
            if (null == bean)
            {
                continue;
            }
            %><%=text(comma)%><%=q(study_accession)%>:{<%
                %>name:<%=q(study_accession)%>,<%
                %>uniqueName:<%=q("[Study].["+study_accession+"]")%>, <%
                %>hipc_funded:<%=text(isTrue(StringUtils.contains(bean.getProgram_title(),"HIPC")))%>,<%
                %>highlight:<%=text(highlight?"true":"false")%>,<%
                %>containerId:<%=q((String)map.get("container"))%>, url:<%=q(url.getLocalURIString())%>}<%
               comma = ",\n";
           }
       }
   }%>
};


new dataFinder(studyData, loaded_studies, "dataFinderApp");


LABKEY.help.Tour.register({
    id: "immport.dataFinder",
    steps: [
        {
            target: "studypanel",
            title: "Study Panel",
            content: 'This area contains short descriptions of ImmPort studies. ' +
            'The <span class="loaded">blue</span> studies have been loaded by the ImmuneSpace team and can be viewed in more detail.<p/>Click on any study card for more information.',
            placement: "bottom"
        },
        {
            target: 'group_Condition',
            title: "Study Attributes",
            content: "Select items in this area to find studies of interest.  The gray bars show the number of selected participants.<p/>Try " + (Ext4.isMac ? "Command" : "Ctrl") + "-click to multi-select.",
            placement: "right"
        },
        {
            target: 'searchTerms',
            title: "Quick Search",
            content: "Enter terms of interest to search study descriptions.",
            placement: "right"
        },
        {
            target: 'summaryArea',
            title: "Summary",
            content: "Here is a summary of the data in the selected studies. Studies represents the number of studies that contain some participants that match the criteria. Participants is the number of subjects accross all selected studies (including participants that did not match all attributes).",
            placement: "left"
        },
        {
            target: 'filterArea',
            title: "Filter Area",
            content: "See and manage your active filters.",
            placement: "left"
        }
    ]
});


function start_tutorial()
{
    LABKEY.help.Tour.show("immport.dataFinder");
    return false;
}


<% if (me.isAutoResize())
{ %>
    function viewport()
    {
        if ('innerWidth' in window )
            return { width:window.innerWidth, height:window.innerHeight};
        var e = document.documentElement || document.body;
        return {width: e.clientWidth, height:e.clientheight};
    }
    var _resize = function()
    {
        var componentOuter = Ext4.get("dataFinderWrapper");
        if (!componentOuter)
            return;
        var paddingX, paddingY;
        <% if (me.getFrame() == WebPartView.FrameType.PORTAL) {%>
        paddingX = 26;
        paddingY = 95;
        <%}else{%>
        paddingX = 20;
        paddingY = 35;
        <%}%>
        var vpSize = viewport();
        var componentSize = resizeToViewport(componentOuter,
                Math.max(800,vpSize.width), Math.max(600,vpSize.height),
                paddingX, paddingY);
        if (componentSize)
        {
            var bottom = componentOuter.getXY()[1] + componentOuter.getSize().height;
            Ext4.get("selectionpanel").setHeight(bottom - Ext4.get("selectionpanel").getXY()[1]);
            Ext4.get("studypanel").setHeight(bottom - Ext4.get("studypanel").getXY()[1]);
        }
    };
    Ext4.EventManager.onWindowResize(_resize);
    Ext4.defer(_resize, 300);
<%
} %>
</script>


<%!
String isTrue(Object o)
{
    if (null == o)
        return "false";
    if (o instanceof Boolean)
        return o == Boolean.TRUE ? "true" : "false";
    if (o instanceof Number)
        return ((Number) o).intValue() == 0 ? "false" : "true";
    return "false";
}
%>
