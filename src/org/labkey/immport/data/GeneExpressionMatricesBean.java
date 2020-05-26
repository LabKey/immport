package org.labkey.immport.data;

/**
 * Created by Marty on 3/6/2016.
 */
public class GeneExpressionMatricesBean implements FileBean
{
    String Study;
    String download_link;

    @Override
    public String getStudy()
    {
        return Study;
    }

    public void setStudy(String Study)
    {
        this.Study = Study;
    }

    public String getDownload_Link()
    {
        return download_link;
    }

    public void setDownload_Link(String download_link)
    {
        this.download_link = download_link;
    }

    @Override
    public String getFileName()
    {
        return this.download_link;
    }

}
