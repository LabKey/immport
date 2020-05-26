package org.labkey.immport.data;

/**
 * Created by Marty on 2/19/2016.
 */
public class FCSControlFilesBean implements FileBean
{
    String control_file;
    String study_accession;

    public String getStudy_accession()
    {
        return study_accession;
    }

    public void setStudy_accession(String study_accession)
    {
        this.study_accession = study_accession;
    }

    public String getControl_file()
    {
        return control_file;
    }

    public void setControl_file(String control_file)
    {
        this.control_file = control_file;
    }

    @Override
    public String getFileName()
    {
        return this.control_file;
    }

    @Override
    public String getStudy() { return this.study_accession; }
}
