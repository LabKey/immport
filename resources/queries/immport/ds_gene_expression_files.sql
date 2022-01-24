-- Parameter used by the ETL
PARAMETERS($STUDY VARCHAR DEFAULT NULL)
SELECT DISTINCT
ge_links.name AS name,
file_info_name,
filesize,
geo_accession,
ge_links.expsample_accession,
-- ParticipantId and SequenceNum required for LK  datasets
biosample.subject_accession || '.' || SUBSTRING(biosample.study_accession,4) as participantid,
COALESCE(biosample.study_time_collected,9999.0000) as sequencenum,
-- Phenodata and mapping
biosample.biosample_accession,
--DR20 biosample.biosampling_accession,
biosample.description,
biosample.name AS biosample_name,
biosample.type,
biosample.subtype,
biosample.study_time_collected,
biosample.study_time_collected_unit,
biosample.study_time_t0_event,
biosample.study_time_t0_event_specify,
biosample.study_accession,
arm_or_cohort.arm_accession,
arm_or_cohort.name AS arm_name
FROM (
  SELECT
  (CASE WHEN (gef_name IS NULL OR gef_name = '') THEN geo_name ELSE gef_name END) AS name,
  (CASE WHEN (gef_es IS NULL OR gef_es = '') THEN geo_es ELSE gef_es END) AS expsample_accession,
  file_info_name,
  geo_name AS geo_accession,
  filesize
  FROM (
    SELECT
      q_GEF.expsample_accession AS gef_es,
      q_GEF.name AS gef_name,
      q_GEF.file_info_name AS file_info_name,
      q_GEF.filesize AS filesize,
      q_GEO.expsample_accession AS geo_es,
      q_GEO.name AS geo_name
    FROM q_GEF FULL OUTER JOIN q_GEO ON q_GEF.name = q_GEO.name
                                    AND q_GEF.expsample_accession = q_GEO.expsample_accession
  ) AS ge_links
) AS ge_links,
biosample, expsample_2_biosample, arm_2_subject, arm_or_cohort
WHERE
biosample.biosample_accession = expsample_2_biosample.biosample_accession AND
expsample_2_biosample.expsample_accession = ge_links.expsample_accession AND
biosample.subject_accession = arm_2_subject.subject_accession AND
arm_2_subject.arm_accession = arm_or_cohort.arm_accession AND
biosample.study_accession = arm_or_cohort.study_accession
AND ($STUDY IS NULL OR $STUDY = biosample.study_accession)