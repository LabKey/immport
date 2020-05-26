package org.labkey.immport.data;

/**
 * Created by Marty on 2/19/2016.
 */
public class FCSSampleFilesBean implements FileBean
{
    String file_info_name;
    String study_accession;

    public String getStudy_accession()
    {
        return study_accession;
    }

    public void setStudy_accession(String study_accession)
    {
        this.study_accession = study_accession;
    }

    public String getFile_info_name()
    {
        return file_info_name;
    }

    public void setFile_info_name(String file_info_name)
    {
        this.file_info_name = file_info_name;
    }

    @Override
    public String getFileName()
    {
        return this.file_info_name;
    }

    @Override
    public String getStudy() { return this.study_accession; }

}
