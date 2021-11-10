 /*
 * Copyright (c) 2013-2017 LabKey Corporation
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

DROP VIEW IF EXISTS immport.v_results_union;

CREATE OR REPLACE VIEW immport.v_results_union AS

SELECT
  subject_accession || '.' || SUBSTRING(study_accession,4) as subjectid,
  assay,arm_accession,biosample_accession,(SELECT type from immport.biosample where biosample.biosample_accession=X.biosample_accession),expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id,
  CASE study_time_collected_unit
    WHEN 'Days' THEN FLOOR(study_time_collected)
    WHEN 'Hours' THEN FLOOR(study_time_collected/24)
    ELSE NULL
  END as study_day
FROM (

SELECT 'ELISA' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.elisa_result

UNION ALL

SELECT 'ELISPOT' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.elispot_result

UNION ALL

SELECT 'Flow Cytometry' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.fcs_analyzed_result

UNION ALL

SELECT 'HAI' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.hai_result

UNION ALL

SELECT 'HLA Typing' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.hla_typing_result

UNION ALL

SELECT 'KIR' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.kir_typing_result

UNION ALL

SELECT 'MBAA' AS assay,
arm_accession,biosample_accession,NULL AS expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.mbaa_result


UNION ALL

SELECT 'Neutralizing Antibody' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.neut_ab_titer_result

UNION ALL

SELECT 'PCR' AS assay,
arm_accession,biosample_accession,expsample_accession,experiment_accession,study_accession,study_time_collected,study_time_collected_unit,subject_accession,workspace_id
FROM immport.pcr_result

UNION ALL

SELECT
   CASE
     WHEN file_info.detail IN ('Affymetrix CEL', 'Affymetrix other', 'Gene expression result', 'Illumina BeadArray', 'TPM', 'RNA sequencing result') THEN 'Gene Expression'
     WHEN file_info.detail IN ('CyTOF result') THEN 'CyTOF'
     ELSE 'UNKNOWN'
   END as assay,
   arm_or_cohort.arm_accession,
   biosample.biosample_accession,
   expsample_2_biosample.expsample_accession,
   expsample.experiment_accession,
   biosample.study_accession,
   biosample.study_time_collected,
   biosample.study_time_collected_unit,
   biosample.subject_accession,
   biosample.workspace_id
FROM
  immport.biosample
  JOIN immport.expsample_2_biosample ON biosample.biosample_accession = expsample_2_biosample.biosample_accession
  JOIN immport.expsample ON expsample_2_biosample.expsample_accession = expsample.expsample_accession
  JOIN immport.arm_2_subject ON biosample.subject_accession = arm_2_subject.subject_accession
  JOIN immport.arm_or_cohort ON arm_2_subject.arm_accession = arm_or_cohort.arm_accession AND biosample.study_accession = arm_or_cohort.study_accession
  JOIN (
    SELECT
      file_info.detail, expsample_2_file_info.expsample_accession
    FROM
      immport.file_info INNER JOIN immport.expsample_2_file_info ON file_info.file_info_id = expsample_2_file_info.file_info_id
    WHERE
      file_info.detail IN ('Affymetrix CEL', 'Affymetrix other', 'Gene expression result', 'Illumina BeadArray', 'TPM', 'RNA sequencing result', 'CyTOF result')
  UNION
    SELECT
      'Gene expression result' as detail,
      expsample_accession
    FROM
      immport.expsample_public_repository
    WHERE
      repository_name = 'GEO' AND repository_accession like 'GSM%'
  ) file_info ON file_info.expsample_accession = expsample.expsample_accession
) X;


CREATE OR REPLACE VIEW immport.v_results_summary AS

  SELECT 'ELISA' AS assay, 'elisa' AS name, 'Enzyme-linked immunosorbent assay (ELISA)' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.elisa_result) _

  UNION ALL

  SELECT 'ELISPOT' AS assay, 'elispot' AS name, 'Enzyme-Linked ImmunoSpot (ELISPOT)' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.elispot_result) _

  UNION ALL

  SELECT 'Flow Cytometry' AS assay, 'fcs_analyzed_result' AS name, 'Flow cytometry analyzed results' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.fcs_analyzed_result) _

  UNION ALL

  SELECT 'HAI' AS assay, 'hai' AS name, 'Hemagglutination inhibition (HAI)' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.hai_result) _

  UNION ALL

  SELECT 'HLA Typing' AS assay, 'hla_typing' As name, 'Human leukocyte antigen (HLA) typing' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.hla_typing_result) _

  UNION ALL

  SELECT 'KIR' AS assay, 'kir_typing' AS name, 'Killer cell immunoglobulin-like receptors (KIR) typing' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.kir_typing_result) _

  UNION ALL

  SELECT 'MBAA' AS assay, 'mbaa' AS name, 'Multiplex bead array asssay' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.mbaa_result) _


  UNION ALL

  SELECT 'Neutralizing Antibody' AS assay, 'neut_ab_titer' AS name, 'Neutralizing antibody titer' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.neut_ab_titer_result) _

  UNION ALL

  SELECT 'PCR' AS assay, 'pcr' AS name, 'Polymerisation chain reaction (PCR)' AS label, study_accession
  FROM (SELECT DISTINCT study_accession FROM immport.pcr_result) _

  UNION ALL

  SELECT DISTINCT
       'Gene Expression' AS assay,
       'gene_expression_files' AS name,
       'Gene expression microarray data files' AS label,
     biosample.study_accession
  FROM
    immport.biosample
    JOIN immport.expsample_2_biosample ON biosample.biosample_accession = expsample_2_biosample.biosample_accession
  WHERE expsample_2_biosample.expsample_accession IN
  (
      SELECT expsample_2_file_info.expsample_accession
      FROM immport.expsample_2_file_info JOIN immport.file_info ON expsample_2_file_info.file_info_id = file_info.file_info_id
      WHERE
        file_info.detail IN ('Affymetrix CEL', 'Affymetrix other', 'Gene expression result', 'Illumina BeadArray', 'TPM', 'RNA sequencing result')
    UNION
      SELECT
         expsample_public_repository.expsample_accession
      FROM
        immport.expsample_public_repository
      WHERE
        repository_name = 'GEO' AND repository_accession LIKE 'GSM%'
  )

  UNION ALL

  SELECT DISTINCT
       'FCS sample files' AS assay,
       'fcs_sample_files'  AS name,
       'FCS sample files' AS label,
     biosample.study_accession
  FROM
    immport.biosample
    JOIN immport.expsample_2_biosample ON biosample.biosample_accession = expsample_2_biosample.biosample_accession
    JOIN immport.expsample ON expsample_2_biosample.expsample_accession = expsample.expsample_accession
    JOIN immport.expsample_2_file_info ON expsample_2_biosample.expsample_accession = expsample_2_file_info.expsample_accession
    JOIN immport.file_info ON expsample_2_file_info.file_info_id = file_info.file_info_id
--    JOIN immport.arm_2_subject ON biosample.subject_accession = arm_2_subject.subject_accession
--    JOIN immport.arm_or_cohort ON arm_2_subject.arm_accession = arm_or_cohort.arm_accession AND biosample.study_accession = arm_or_cohort.study_accession
  WHERE
    file_info.name LIKE '%.fcs' AND
    file_info.detail IN ('Flow cytometry result', 'CyTOF result')

UNION ALL

  SELECT DISTINCT
       'FCS control files' AS assay,
       'fcs_control_files' AS name,
       'FCS control files' AS label,
     biosample.study_accession
  FROM
    immport.biosample
    JOIN immport.expsample_2_biosample ON biosample.biosample_accession = expsample_2_biosample.biosample_accession
    JOIN immport.expsample ON expsample_2_biosample.expsample_accession = expsample.expsample_accession
    JOIN immport.expsample_2_file_info ON expsample_2_biosample.expsample_accession = expsample_2_file_info.expsample_accession
    JOIN immport.file_info ON expsample_2_file_info.file_info_id = file_info.file_info_id
--    JOIN immport.arm_2_subject ON biosample.subject_accession = arm_2_subject.subject_accession
--    JOIN immport.arm_or_cohort ON arm_2_subject.arm_accession = arm_or_cohort.arm_accession AND biosample.study_accession = arm_or_cohort.study_accession
  WHERE
    file_info.name LIKE '%.fcs' AND
    file_info.detail = 'Flow cytometry compensation or control'
;


-- SELECT immport.fn_populateDimensions()

CREATE OR REPLACE FUNCTION immport.fn_populateDimensions() RETURNS INTEGER AS $$
BEGIN

  DROP TABLE IF EXISTS temp_results_union;
  CREATE TEMPORARY TABLE temp_results_union AS
  SELECT *
  FROM immport.v_results_union;

  -- dimStudyAssay

  DELETE FROM immport.dimStudyAssay;
  INSERT INTO immport.dimStudyAssay (Study, Assay, Name, Label, CategoryLabel)
  SELECT DISTINCT
    study_accession as Study, assay, name, label,
    CASE name
      WHEN 'demographics' THEN 'Demographics'
      WHEN 'cohort_membership' THEN 'Demographics'
      WHEN 'gene_expression_files' THEN 'Raw data files'
      WHEN 'fcs_sample_files' THEN 'Raw data files'
      WHEN 'fcs_control_files' THEN 'Raw data files'
      ELSE 'Assays'
    END AS CategoryLabel
  FROM immport.v_results_summary
  WHERE study_accession IS NOT NULL;


  -- dimAssay

  DELETE FROM immport.dimAssay;
  INSERT INTO immport.dimAssay (SubjectId, Assay)
  SELECT DISTINCT
    subject_accession || '.' || SUBSTRING(study_accession,4) AS SubjectId,
    assay as Assay
  FROM temp_results_union
  WHERE subject_accession IS NOT NULL AND study_accession IS NOT NULL;


  -- dimDemographic

  DELETE FROM immport.dimDemographic;
  INSERT INTO immport.dimDemographic (SubjectId, Study, AgeInYears, Species, Gender, Race, Age, exposure_material, exposure_process)
  SELECT DISTINCT
    subject.subject_accession || '.' || SUBSTRING(arm_or_cohort.study_accession,4) AS SubjectId,
    arm_or_cohort.study_accession AS Study,
    CASE age_unit
    WHEN 'Years' THEN floor(min_subject_age)
      WHEN 'Weeks' THEN 0
      WHEN 'Months' THEN 0
      ELSE NULL
    END as AgeInYears,
    species As Species,
    CASE 
      WHEN gender = 'Not Specified' THEN 'Unknown'
      ELSE gender
    END AS Gender,
    CASE
      WHEN race = 'Not_Specified' THEN 'Unknown'
      WHEN race = 'Not Specified' THEN 'Unknown'
      WHEN race IS NULL THEN 'Unknown'
      ELSE race
    END AS Race,
    CASE
      WHEN floor(min_subject_age) < 10 THEN '0-10'
      WHEN floor(min_subject_age) < 20 THEN '11-20'
      WHEN floor(min_subject_age) < 30 THEN '21-30'
      WHEN floor(min_subject_age) < 40 THEN '31-40'
      WHEN floor(min_subject_age) < 50 THEN '41-50'
      WHEN floor(min_subject_age) < 60 THEN '51-60'
      WHEN floor(min_subject_age) < 70 THEN '61-70'
      WHEN floor(min_subject_age) >= 70 THEN '> 70'
      ELSE 'Unknown'
    END AS Age,
    -- NOTE: using SELECT here instead of LOJ in FROM so that this will error if there are ever multiple immune_exposure rows for one study/subject
    -- BUG: this failed in DR29 added MIN() as temporary fix see https://www.labkey.org/HIPC/Support%20Tickets/issues-details.view?issueId=36663
    coalesce((SELECT MIN(exposure_material_reported) FROM immport.immune_exposure WHERE arm_2_subject.arm_accession = immune_exposure.arm_accession AND arm_2_subject.subject_accession = immune_exposure.subject_accession), 'Unknown') as exposure_material,
    coalesce((SELECT MIN(exposure_process_preferred) FROM immport.immune_exposure WHERE arm_2_subject.arm_accession = immune_exposure.arm_accession AND arm_2_subject.subject_accession = immune_exposure.subject_accession), 'unknown') as exposure_process
  FROM immport.subject
      INNER JOIN immport.arm_2_subject arm_2_subject ON subject.subject_accession = arm_2_subject.subject_accession 
      INNER JOIN immport.arm_or_cohort ON arm_2_subject.arm_accession = arm_or_cohort.arm_accession
     -- NOTE this join assumes only one immune_exposure row per study/subject (which is true in DR28)
     ---LEFT OUTER JOIN immport.immune_exposure ON arm_2_subject.arm_accession = immune_exposure.arm_accession AND arm_2_subject.subject_accession = immune_exposure.subject_accession
  ;


  -- dimStudyCondition

  DELETE FROM immport.dimStudyCondition;
  WITH study_ AS (
      SELECT study_accession, lower(official_title || ' ' || condition_studied) as description
      FROM immport.study 
      WHERE study_accession NOT IN (SELECT study_accession FROM immport.immune_exposure WHERE disease_preferred IS NOT NULL)
  )
  INSERT INTO immport.dimStudyCondition (Study, Condition)

      SELECT arm_or_cohort.study_accession AS Study, COALESCE(disease_preferred,disease_reported) as Condition 
      FROM immport.immune_exposure JOIN immport.arm_or_cohort ON immune_exposure.arm_accession = arm_or_cohort.arm_accession
      WHERE (disease_preferred IS NOT NULL OR disease_reported IS NOT NULL) 

      UNION

      SELECT study_accession AS Study, 'Ragweed Allergy' as Condition
      FROM study_
      WHERE description like '%ragweed%'

      UNION

      SELECT study_accession AS Study, 'Atopic Dermatitis' as Condition
      FROM study_
      WHERE description like '%atopic dermatitis%'

      UNION

      SELECT study_accession AS Study, 'Clostridium difficile' as Condition
      FROM study_
      WHERE
        description like '%clostridium difficile%' OR
        description like '%c. difficile%'

      UNION

      SELECT study_accession AS Study, 'Renal transplant' as Condition
      FROM study_
      WHERE
        (description like '%renal%' OR
         description like '%kidney%') AND
        description like '%transplant%'

      UNION

      SELECT study_accession AS Study, 'Arthritis' as Condition
      FROM study_
      WHERE description like '%arthritis%'

      UNION

      SELECT study_accession AS Study, 'Hepatitis C' as Condition
      FROM study_
      WHERE description like '%hepatitis c%'

      UNION

      SELECT study_accession AS Study, 'Influenza' as Condition
      FROM study_
      WHERE
        description like '%flu%'

      UNION

      SELECT study_accession AS Study, 'Smallpox' as Condition
      FROM study_
      WHERE description like '%smallpox%'

      UNION

      SELECT study_accession AS Study, 'Tuberculosis' as Condition
      FROM study_
      WHERE description like '%tuberculosis%'

      UNION

      SELECT study_accession AS Study, 'Lupus' as Condition
      FROM study_
      WHERE description like '%lupus%'

      UNION

      SELECT study_accession AS Study, 'West Nile virus' as Condition
      FROM study_
      WHERE description like '%west nile%' OR
        description like '%wnv%'

      UNION

      SELECT study_accession AS Study, 'Asthma' as Condition
      FROM study_
      WHERE
        description like '%asthma%'

      UNION

      SELECT study_accession AS Study, 'Typhoid' as Condition
      FROM study_
      WHERE
        description like '%typhoid%'

      UNION

      SELECT study_accession AS Study, 'Cholera' as Condition
      FROM study_
      WHERE description like '%cholera%'

      UNION

      SELECT study_accession AS Study, 'Vasculitis' as Condition
      FROM study_
      WHERE
        description like '%vasculitis%'

      UNION

      SELECT study_accession AS Study, 'Diabetes' as Condition
      FROM study_
      WHERE description like '%diabet%'

      UNION

      SELECT study_accession AS Study, 'Vaccinia' as Condition
      FROM study_
      WHERE description like '%vaccinia%'

      UNION

      SELECT study_accession AS Study, 'Helicobacter pylori' as Condition
      FROM study_
      WHERE description like '%pylori%'

      UNION

      SELECT study_accession AS Study, 'Escherichia coli' as Condition
      FROM study_
      WHERE description like '%escherichia coli%'
  ;

  INSERT INTO immport.dimStudyCondition (Study, Condition)
  SELECT study_accession AS Study, 'Other' as Condition
  FROM immport.study
  WHERE study_accession NOT IN (SELECT Study FROM immport.dimStudyCondition);



  -- dimStudyTimepoint

  DELETE FROM immport.dimStudyTimepoint;
  INSERT INTO immport.dimStudyTimepoint (Study, Timepoint, SortOrder)
   SELECT DISTINCT
    study_accession as Study,
    CASE
      WHEN study_day < 0 THEN '<0'
      WHEN study_day <= 14 THEN CAST(study_day AS VARCHAR)
      WHEN study_day < 28 THEN '15-27'
      WHEN study_day = 28 THEN '28'
      WHEN study_day < 56 THEN '29-55'
      WHEN study_day = 56 THEN '56'
      WHEN study_day > 56 THEN '>56'
      ELSE 'Unknown'
    END as Timepoint,
    CASE
      WHEN study_day < 0 THEN -1
      WHEN study_day <= 14 THEN study_day
      WHEN study_day < 28 THEN 15
      WHEN study_day = 28 THEN 28
      WHEN study_day < 56 THEN 29
      WHEN study_day = 56 THEN 56
      WHEN study_day > 56 THEN 57
      ELSE 99
    END as sortorder
  FROM temp_results_union
  ORDER BY study_accession, sortorder;

/*
  -- summarySubjectAssayStudy
  DELETE FROM immport.summarySubjectAssayStudy;
  INSERT INTO  immport.summarySubjectAssayStudy (subject_accession, assay, study_accession)
  SELECT DISTINCT subject_accession, assay, study_accession
  FROM temp_results_union
  WHERE subject_accession IS NOT NULL AND assay IS NOT NULL AND study_accession IS NOT NULL;
*/

  -- dimSampleType
  INSERT INTO immport.dimSampleType (subjectid, type)
  SELECT DISTINCT subject_accession || '.' || SUBSTRING(study_accession,4) as subjectid, type from immport.biosample
  WHERE type IS NOT NULL;



  -- dimSample
  DELETE FROM immport.dimSample;
  INSERT INTO immport.dimSample (SampleId, SubjectId, Type, Timepoint, Timepoint_SortOrder)
  SELECT DISTINCT
    biosample_accession as SampleId,
    subject_accession || '.' || SUBSTRING(study_accession,4) AS SubjectId,
    type as Type,
    CASE
      WHEN study_day < 0 THEN '<0'
      WHEN study_day <= 14 THEN CAST(study_day AS VARCHAR)
      WHEN study_day < 28 THEN '15-27'
      WHEN study_day = 28 THEN '28'
      WHEN study_day < 56 THEN '29-55'
      WHEN study_day = 56 THEN '56'
      WHEN study_day > 56 THEN '>56'
      ELSE 'Unknown'
      END as Timepoint,
    CASE
      WHEN study_day < 0 THEN -1
      WHEN study_day <= 14 THEN study_day
      WHEN study_day < 28 THEN 15
      WHEN study_day = 28 THEN 28
      WHEN study_day < 56 THEN 29
      WHEN study_day = 56 THEN 56
      WHEN study_day > 56 THEN 57
      ELSE 99
      END as Timepoint_SortOrder
  FROM temp_results_union
  WHERE biosample_accession IS NOT NULL AND subject_accession IS NOT NULL;


  -- dimSampleAssay
  DELETE FROM immport.dimSampleAssay;
  INSERT INTO immport.dimSampleAssay (SampleId, Assay)
  SELECT DISTINCT
    biosample_accession as SampleId, assay as Assay
  FROM temp_results_union
  WHERE biosample_accession IS NOT NULL AND assay IS NOT NULL;

  -- dimStudy
  -- this table is really doing double duty as a CUBE input table (Study, Type, Program, Sort Order) as well as
  -- a place to put summary information for the "study card" (brief_title, shared_study, restricted, research_focus, pi_names, assays, sample_types)
  -- NOTE: join to study.StudyProperties for                 (restricted, container)

  DELETE FROM immport.dimStudy;

  -- study x contract_grant_2_study is MxM, even though it is 1xM 99% of the time
  -- we're going to force this to me 1xM, with a synthetic junction table
  INSERT INTO immport.dimStudy (Study, Type, Program, SortOrder, brief_title, shared_study, restricted, research_focus, pi_names, assays, sample_types)
  SELECT DISTINCT
      study.study_accession as Study,
      study.type as Type,
      P.name as Program,
      cast(substring(study.study_accession,4) as integer) as SortOrder,
      study.brief_title,
      study.shared_study,
      study.restricted,
      study_categorization.research_focus,
      pi.pi_names,
      A.assays,
      st.sample_types
  FROM immport.study
      LEFT OUTER JOIN (SELECT study_accession, MIN(contract_grant_id) as contract_grant_id FROM immport.contract_grant_2_study GROUP BY study_accession) cg2s ON study.study_accession = cg2s.study_accession
      LEFT OUTER JOIN immport.contract_grant C ON cg2s.contract_grant_id = C.contract_grant_id
      LEFT OUTER JOIN immport.program P on C.program_id = P.program_id
-- NOTE: In DR41 SDY1774 has TWO rows in study_categorization.  This is not currently supported by the datafinder
      LEFT OUTER JOIN (SELECT study_accession,  MIN(research_focus) AS research_focus FROM immport.study_categorization GROUP BY study_accession) study_categorization ON study.study_accession = study_categorization.study_accession
      LEFT OUTER JOIN
       (
           SELECT array_to_string(array_agg(DISTINCT first_name || ' ' || last_name), ', ') AS pi_names, study_accession
           FROM immport.study_personnel
           WHERE role_in_study LIKE '%Principal%'
           GROUP BY study_accession
       ) pi
       ON study.study_accession = pi.study_accession
      LEFT OUTER JOIN
       (
           SELECT array_to_string(array_agg(DISTINCT assay), ', ') AS assays, study
           FROM immport.dimstudyassay
           GROUP BY study
       ) A
       ON study.study_accession = A.study
      LEFT OUTER JOIN
       (
           SELECT array_to_string(array_agg(DISTINCT sample_type), ', ') as sample_types, studyid as study_accession
           FROM
               (
                   SELECT ('SDY' || split_part(subjectid, '.', 2)) as studyid, type as sample_type
                   FROM immport.dimsampletype
               ) st_inner
           GROUP BY studyid
       ) st
       ON study.study_accession = st.study_accession
;

  RETURN 1;
  END;
$$ LANGUAGE plpgsql;
