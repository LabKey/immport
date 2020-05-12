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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.DbSchemaType" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.QuerySchema" %>
<%@ page import="org.labkey.api.rstudio.RStudioService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.HeartBeat" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.immport.ImmPortController" %>
<%@ page import="org.labkey.immport.data.StudyBean" %>
<%@ page import="org.labkey.immport.view.DataFinderWebPart" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="java.util.regex.Matcher" %>
<%@ page import="java.util.regex.Pattern" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Ext4");
        dependencies.add("clientapi/ext4");
        dependencies.add("query/olap.js");
        dependencies.add("angular");
        dependencies.add("dataFinder.css");
        dependencies.add("immport/dataFinder.js");
        dependencies.add("immport/ParticipantGroup.js");
        dependencies.add("immport/hipc.css");
    }
%>
<%
    DataFinderWebPart me = (DataFinderWebPart)HttpView.currentView();
    ViewContext context = HttpView.currentContext();
    // CONSIDER: add this to fn_populateDimensions (maybe just add additional program_id column to dimStudy)
    ArrayList<StudyBean> studies = new SqlSelector(DbSchema.get("immport", DbSchemaType.Module),
            "SELECT study.*, P.name as program_title, pi.pi_names\n" +
            "FROM immport.study " +
//            "LEFT OUTER JOIN immport.contract_grant_2_study CG2S ON study.study_accession = CG2S.study_accession\n" +
            "LEFT OUTER JOIN (SELECT study_accession, MIN(contract_grant_id) as contract_grant_id FROM immport.contract_grant_2_study GROUP BY study_accession) CG2S ON study.study_accession = CG2S.study_accession\n" +
            "LEFT OUTER JOIN immport.contract_grant C ON CG2S.contract_grant_id = C.contract_grant_id\n" +
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

<div id="dataFinderWrapper" class="labkey-data-finder-outer">
<div id="dataFinderApp" class="x-hidden labkey-data-finder-inner" ng-app="dataFinderApp" ng-controller="dataFinder">

    <table id="dataFinderTable" border=0 class="labkey-data-finder">
        <tr>
            <td>
                <div ng-controller="SubjectGroupController" id="filterArea">
                    <div class="labkey-group-label">
                        {{currentGroup.id != null ? "Saved group: ": ""}}{{currentGroup.label}}
                        <span ng-if="isGroupNotFound()" class="fa fa-exclamation-circle" data-qtip="{{currentGroup.groupNotFound}}"></span>
                    </div>

                    <div class="df-navbar df-navbar-default ">
                        <ul class="df-nav df-navbar-nav">
                            <li id="df-manageMenu" class="labkey-dropdown" ng-mouseover="openMenu($event, true)">
                                <a href="#"><i class="fa fa-cog"></i></a>
                                <ul class="labkey-dropdown-menu" ng-show="!isGuest">
                                    <li class="x4-menu-item-text"><a class="df-menu-item-link" href="<%=h(new ActionURL("study", "manageParticipantCategories", getContainer()))%>">Manage Groups</a></li>
                                </ul>
                            </li>
                            <li id="loadMenu" class="labkey-dropdown" >
                                <a ng-class="{'labkey-text-link' : loadedStudiesShown(), 'labkey-disabled-text-link': !loadedStudiesShown()} " class="no-arrow" style="margin-right: 0.8em" href="#" ng-mouseover="openMenu($event, false)">Load <i class="fa fa-caret-down"></i></a>
                                <ul class="labkey-dropdown-menu" ng-show="loadedStudiesShown() && groupsAvailable()" >
                                    <li class="x4-menu-item-text" ng-repeat="group in groupList">
                                        <a class="df-menu-item-link" ng-click="applySubjectGroupFilter(group, $event)">{{group.label}}</a>
                                    </li>
                                </ul>
                            </li>
                            <li id="saveMenu" class="labkey-dropdown">
                                <a ng-class="{'labkey-text-link' : loadedStudiesShown(), 'labkey-disabled-text-link': !loadedStudiesShown()} " class="no-arrow" style="margin-right: 0.8em" href="#" ng-mouseover="openMenu($event, false)" ng-mouseleave="closeMenu($event)">Save <i class="fa fa-caret-down"></i> </a>
                                <ul class="labkey-dropdown-menu" ng-if="!isGuest && loadedStudiesShown()">
                                    <li class="x4-menu-item-text" ng-repeat="opt in saveOptions" ng-class="{'inactive' : !opt.isActive}">
                                        <a class="df-menu-item-link" ng-class="{'inactive' : !opt.isActive}" ng-click="saveSubjectGroup(opt.id, false, $event)">{{opt.label}}</a>
                                    </li>
                                </ul>
                                <ul class="labkey-dropdown-menu" ng-if="isGuest">
                                    <li class="x4-menu-item-text">
                                        <span class="df-menu-item-link">You must be logged in to save a group.</span>
                                    </li>
                                </ul>
                            </li>
                            <li id="sendMenu" class="labkey-dropdown">
                                <a ng-class="{'labkey-text-link' : !isGuest && loadedStudiesShown(), 'labkey-disabled-text-link': isGuest || !loadedStudiesShown()} " class="no-arrow" href="#" ng-click="sendSubjectGroup($event)">Send</a>
                            </li>
                        </ul>
                        <span class="df-clear-filter active" ng-show="hasFilters()" ng-click="clearAllClick();">[clear all]</span>
                    </div>

                </div>

            </td>
            <td>
                <div class="studyfinder-header">
                    <span class="df-search-box">
                        <i class="fa fa-search"></i>&nbsp;
                        <input placeholder="Studies" id="searchTerms" name="q" class="df-search-box"  ng-model="searchTerms" ng-change="onSearchTermsChanged()" type="search">
                    </span>
                    <span class="labkey-study-search">
                        <select ng-model="studySubset" name="studySubsetSelect" ng-change="onStudySubsetChanged()">
                            <option ng-repeat="option in subsetOptions" value="{{option.value}}" ng-selected="{{option.value == studySubset}}">{{option.name}}</option>
                        </select>
                    </span>
                    <span class="study-search">{{searchMessage}}</span>
                </div>
		<div class="df-search-message">
                    <span id="message" class="labkey-filter-message" ng-if="!loadedStudiesShown()">No data are available for participants since you are viewing unloaded studies.</span>
		</div>
		<div class="df-help-links">
                <%=link("quick help").href("#").onClick("start_tutorial()").id("showTutorial")%>
                <%=link("Export Study Datasets", ImmPortController.ExportStudyDatasetsAction.class)%>
                <%
                URLHelper startRstudio = null;
                RStudioService rstudio = ServiceRegistry.get(RStudioService.class);
                if (null != rstudio)
                    startRstudio = rstudio.getRStudioLink(context.getUser(), context.getContainer());
                if (null != startRstudio)
                {
                    %><%=link("RStudio", new ActionURL("rstudio", "start", ContainerManager.getRoot()))%><%
                } %>
                </div>
            </td>
        </tr>
        <tr>
            <td class="df-selection-panel">
                <div id="selectionPanel">
                    <div>
                        <div class="df-facet" id="summaryArea" >
                            <div class="df-facet-header"><span class="df-facet-caption">Summary</span></div>
                            <ul>
                                <li class="df-member" style="cursor: default">
                                    <span class="df-member-name">Studies</span>
                                    <span class="df-member-count">{{dimStudy.summaryCount}}</span>
                                </li>
                                <li class="df-member" style="cursor: default">
                                    <span class="df-member-name">Participants</span>
                                    <span class="df-member-count">{{formatNumber(dimSubject.allMemberCount||0)}}</span>
                                </li>
                            </ul>
                        </div>

                        <span id="facetPanel">
                            <div ng-include="'/facet.html'" ng-repeat="dim in [dimSpecies,dimCondition,dimExposureMaterial,dimExposureProcess,dimCategory,dimAssay,dimTimepoint,dimGender,dimRace,dimAge,dimSampleType,dimStudy]"></div>
                        </span>
                    </div>
                </div>
            </td>
            <td class="study-panel">
                <div id="studypanel" ng-class="{'x-hidden':(activeTab!=='Studies')}">
                    <div id="emptymsg" ng-if="!loading && 0!=dimStudies.members.length && 0==getVisibleStudies().length" >
                        <div style="padding:20px;">
                        No participants match the current criteria.  Edit your filters to select a valid cohort.
                        </div>
                    </div>
                    <div ng-include="'/studycard.html'" ng-repeat="study in getVisibleStudies()"></div>
                </div>
            </td>
        </tr>
    </table>


<div id="studyPopup"></div>

<div id="filterPopup" class="labkey-filter-popup" style="top:{{filterChoice.y}}px; left:{{filterChoice.x}}px;" ng-if="filterChoice.show" ng-mouseleave="filterChoice.show = false">
    <ul class="labkey-dropdown-menu" ng-if="filterChoice.options.length > 1">
        <li class="x4-menu-item-text" ng-repeat="option in filterChoice.options">
            <a class="df-menu-item-link" ng-click="setFilterType(filterChoice.dimName,option.type)">{{option.caption}}</a>
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
    <div id="group_{{dim.name}}" class="df-facet"
         ng-class="{expanded:dim.expanded, collapsed:!dim.expanded, noneSelected:(0==dim.filters.length)}">
        <div class="df-facet-header">
            <div class="df-facet-caption active" ng-click="dim.expanded=!dim.expanded" ng-mouseover="filterChoice.show = false">
                <i class="fa fa-plus-square"></i>
                <i class="fa fa-minus-square"></i>
                &nbsp;
                <span>{{dim.caption || dim.name}}</span>
                <span ng-if="dim.filters.length" class="df-clear-filter active" ng-click="selectMember(dim.name,null,$event);">[clear]</span>
            </div>
            <div class="labkey-filter-options" ng-if="dim.filters.length > 1 && dim.filterOptions.length > 0" >
                <a ng-click="displayFilterChoice(dim.name, $event)" ng-mouseover="displayFilterChoice(dim.name,$event);"  class="df-menu-item-text" ng-class="{inactive: dim.filterOptions.length < 2}" href="#">{{dim.filterCaption}} <i ng-if="dim.filterOptions.length > 1" class="fa fa-caret-down"></i></a>
            </div>
        </div>
        <ul>
            <li ng-repeat="member in dim.members | filter:isMemberVisible" id="m_{{dim.name}}_{{member.uniqueName}}" style="position:relative;" class="df-member"
                 ng-class="{'df-selected-member':member.selected, 'df-empty-member':(!member.selected && member.count == 0)}"
                 ng-click="selectMember(dim.name,member,$event)">
                <span class="active df-member-indicator" ng-class="{selected:member.selected, 'df-none-selected':!dim.filters.length, 'not-selected':!member.selected}" ng-click="toggleMember(dim.name,member,$event)">
                </span>
                <span class="df-member-name">{{member.name}}</span>
                &nbsp;
                <span class="df-member-count">{{formatNumber(member.count)}}</span>
                <span ng-class="{'df-bar-selected':member.selected}" class="df-bar" ng-show="member.count" style="width:{{member.percent}}%;"></span>
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

<script type="text/javascript">

var $=$||jQuery;

var studyData = [<%
    String comma = "\n  ";
    for (StudyBean study : studies)
    {
        %><%=text(comma)%>[<%=q(study.getStudy_accession())%>,<%=text(study.getStudy_accession().substring(3))%>,<%=q(StringUtils.defaultString(study.getBrief_title(),study.getOfficial_title()))%>,<%=q(study.getPi_names())%>,<%=study.getRestricted()?"true":"false"%>]<%
        comma = ",\n  ";
    }
%>];


var loaded_studies = {
<%
Container c = context.getContainer();
if (!c.isRoot())
{
    Pattern sdyPattern = Pattern.compile("(SDY\\d+).*");

    comma = "\n";
    Container p = c.getProject();
    QuerySchema s = DefaultSchema.get(context.getUser(), p).getSchema("study");
    TableInfo sp = s.getTable("StudyProperties", ContainerFilter.Type.AllInProject.create(s));
    Collection<Map<String, Object>> maps = new TableSelector(sp).getMapCollection();

    long now = HeartBeat.currentTimeMillis();

    for (Map<String, Object> map : maps)
    {
        Container studyContainer = ContainerManager.getForId((String)map.get("container"));
        if (null == studyContainer)
            continue;
        ActionURL url = studyContainer.getStartURL(context.getUser());

        String study_accession = null;
        String name = StringUtils.defaultIfBlank((String)map.get("Label"),studyContainer.getName());
        if (null != name)
        {
            Matcher m = sdyPattern.matcher(name);
            if (m.matches())
                study_accession = m.group(1);
        }
        Date until = (Date)map.get("highlight_until");
        boolean highlight = null != until && until.getTime() > now;
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


new dataFinder(studyData, loaded_studies, <%=me.getGroupId()%>, "dataFinderApp");


LABKEY.help.Tour.register({
    id: "immport-dataFinder-tour",
    steps: [
        {
            target: $('[name=webpart]')[0],
            title: "Data Finder",
            content: "Welcome to the Data Finder. A tool for searching, accessing and combining data across studies available on ImmuneSpace.",
            placement: "top",
            yOffset: 40,
            xOffset: 120,
            showNextButton: true
        },{
            target: "studypanel",
            title: "Study Panel",
            content: "This area contains short descriptions of the studies/datasets that match the selected criteria.",
            placement: "top",
            showNextButton: true,
            showPrevButton: true
        },{
            target: "summaryArea",
            title: "Summary",
            content: "This summary area indicates how many participants and studies match the selected criteria.", 
            placement: "right",
            showNextButton: true,
            showPrevButton: true
        },{
            target: "facetPanel",
            title: "Filters",
            content: "This is where filters are selected and applied. The numbers (also represented by the lengths of the gray bars) indicate how many participants will match the search if this filter is added.",
            placement: "right",
            showNextButton: true,
            showPrevButton: true
        },{
            target: "searchTerms",
            title: "Quick Search",
            content: "Enter terms of interest to search study and data descriptions. This will find matches within the selection of filtered studies/datasets.",
            placement: "right",
            yOffset: -25,
            showPrevButton: true
        }
    ]
});


function start_tutorial()
{
    LABKEY.help.Tour.show("immport-dataFinder-tour");
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
        var paddingX=35, paddingY=95;
        <%-- resize down to about a 1200x800 screen size --%>
        var vpSize = viewport();
        var componentSize = resizeToViewport(componentOuter,
                Math.max(1200,vpSize.width), Math.max(750,vpSize.height),
                paddingX, paddingY);
        if (componentSize)
        {
            var bottom = componentOuter.getXY()[1] + componentOuter.getSize().height;
            Ext4.each(["selectionPanel","selection-panel","studypanel","study-panel","dataFinderTable"],function(id){
                var el = Ext4.get(id);
                if (el)
                    el.setHeight(bottom - el.getXY()[1]);
            });
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
