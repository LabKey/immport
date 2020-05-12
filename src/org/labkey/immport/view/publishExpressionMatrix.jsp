<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.data.ContainerFilterable" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.RuntimeSQLException" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="org.labkey.api.query.QueryException" %>
<%@ page import="org.labkey.api.query.QuerySchema" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.TreeSet" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<labkey:errors/>

<%
    // guess assay name
    QuerySchema assay = DefaultSchema.get(getUser(), getContainer(), "assay");
    ArrayList<String> assays = QueryService.get()
            .selector(assay, "SELECT name From assay.AssayList WHERE Type.Name = 'Expression Matrix'")
            .getArrayList(String.class);
    String assayName = null;
    if (assays.size() == 0)
    {
        %><%= h("No Expression Matrix assay was found. nothing to do.")  %><%
        return;
    }
    else if (assays.size() == 1)
    {
        assayName = assays.get(0);
    }
    else
    {
        %><%= h("More than one Expression Matrix assay was found. Not supported yet.")  %><%
        return;
    }

    String expression_runs_sql  = "SELECT folder.rowid as folderId, folder.name as folderName, rowid as runId, name as runName, featureSet.Name as featureSetName FROM assay.expressionmatrix.\"" + assayName + "\".Runs";
    UserSchema study = (UserSchema)DefaultSchema.get(getUser(), getContainer(), "study");
    if (null != study && null != study.getTable("participant"))
        expression_runs_sql += "\nWHERE folder IN (SELECT Container FROM study.participant)";
    expression_runs_sql += "\n ORDER BY folderName, runName, runId";
    // ARG need a table info to use container filter
    QueryDefinition qd = QueryService.get().saveSessionQuery(getViewContext(), getContainer(), "study", expression_runs_sql);
    qd.setContainerFilter(ContainerFilter.Type.CurrentAndSubfolders.create(study));
    ArrayList<QueryException> errors = new ArrayList<>();
    TableInfo t = qd.getTable(errors, false);
    if (!errors.isEmpty())
        throw errors.get(0);
    Map<FieldKey,ColumnInfo> cols = QueryService.get().getColumns(t, PageFlowUtil.set(
            FieldKey.fromParts("folderId"),
            FieldKey.fromParts("folderName"),
            FieldKey.fromParts("runId"),
            FieldKey.fromParts("runName"),
            FieldKey.fromParts("featureSetName")
    ));
    Set<Integer> folders = new HashSet<>();
    Set<Integer> runs = new HashSet<>();
    Set<String> featureSets = new TreeSet<>();
    try (ResultSet rs = QueryService.get().select(t, cols.values(), null, null);)
    {
        ColumnInfo runId = cols.get(FieldKey.fromParts("runId"));
        ColumnInfo folderId = cols.get(FieldKey.fromParts("folderId"));
        ColumnInfo featureSetName = cols.get(FieldKey.fromParts("featureSetName"));
        while (rs.next())
        {
            runs.add(runId.getIntValue(rs));
            folders.add(folderId.getIntValue(rs));
            featureSets.add(featureSetName.getStringValue(rs));
        }
    } catch (SQLException x)
    {
        throw new RuntimeSQLException(x);
    }

    if (runs.isEmpty())
    {
        %><%= h("No Expression Matrixes were found, nothing to do.")  %><%
        return;
    }

    %><p><%= h("Found " + runs.size() + " run(s) in " + folders.size() + " folder(s).")%></p><%


    %><labkey:form method="POST" name="publishExpressionMatrix">
    <input type="hidden" name="runList" value="<%= h(StringUtils.join(runs, ",")) %>"><%

    Container root = ContainerManager.getRoot();
    List<Container> targets = ContainerManager.getAllChildren(root, getUser(), AdminPermission.class);
    %><p>Target Folder:&nbsp;<select title="Target Folder" required name="target">
    <option></option><%
    for (Container c : targets)
    {
        %>
        <option><%= h(c.getPath()) %></option>
        <%
    }
    %></select><br>
    <input type="submit" value="Publish Expression Matrix">
    </labkey:form>

Feature Annotation Sets used by these runs:
<ul>
    <%for (String name : featureSets)
    {
        %><li><%=h(name)%></li><%
    }%>
</ul>