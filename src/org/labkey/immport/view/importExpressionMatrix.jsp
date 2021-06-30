<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.apache.commons.io.FileUtils" %>
<%@ page import="org.apache.commons.io.IOCase" %>
<%@ page import="org.apache.commons.io.filefilter.DirectoryFileFilter" %>
<%@ page import="org.apache.commons.io.filefilter.SuffixFileFilter" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.BadRequestException" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.immport.ImmPortController" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>

<labkey:errors/>

<%
    ImmPortController.ImportExpressionMatrixForm form = (ImmPortController.ImportExpressionMatrixForm)HttpView.currentModel();
    PipeRoot pipe = PipelineService.get().findPipelineRoot(getContainer(),PipelineService.PRIMARY_ROOT);
    if (null == pipe)
    {
        %>No pipeline directory found in current folder. Nothing to do.<%
        return;
    }
    File base = pipe.getRootPath();
    Set<String> paths = new LinkedHashSet<>();
    String selectedPath = null;

    // first see if there is a target on the url
    if (!StringUtils.isBlank(form.getXarPath()))
    {
        selectedPath = StringUtils.trimToEmpty(form.getXarPath());
        Path p = Path.parse(selectedPath).normalize();
        if (null == p)
            throw new BadRequestException();
        File selectedFile = new File(base,p.toString());
        if (selectedFile.isFile())
        {
            paths.add(selectedPath);
        }
    }
    if (paths.isEmpty())
    {
        paths.add("");
    }
    paths.addAll(xarListing(base));

%>
    <labkey:form method="POST" name="importExpressionMatrix" action="<%=getViewContext().cloneActionURL().deleteParameters()%>">
<%
    boolean foundSelected = false;
    %><p>Xar file:&nbsp;<select title="Xar File" required name="xarPath"><%
        for (String path : paths)
        {
            boolean selectedOption = false;
            if (!foundSelected && StringUtils.equals(path,selectedPath))
                foundSelected = selectedOption = true;
            %><option <%=selected(selectedOption)%>><%=h(path)%></option><%
        }
    %></select><br>
    <input type="submit" value="Import Expression Matrix">
    </labkey:form><%
%>

<%!
    List<String> xarListing(File base)
    {
        Collection<File> files = FileUtils.listFiles(
                base,
                new SuffixFileFilter(".xar.xml", IOCase.INSENSITIVE),
                DirectoryFileFilter.DIRECTORY
        );
        ArrayList<String> ret = new ArrayList<>(files.size());
        String basePath = base.getPath() + "/";
        for (File f : files)
        {
            if (StringUtils.startsWith(f.getPath(),basePath))
                ret.add(f.getPath().substring(basePath.length()));
        }
        return ret;
    };
%>