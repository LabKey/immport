/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

PARAMETERS($STUDY VARCHAR DEFAULT NULL)
SELECT
file_info.name AS file_info_name,
file_info.detail AS file_info_purpose,
file_info.filesize_bytes as filesize,
expsample_2_biosample.expsample_accession,
biosample.biosample_accession,
biosample.subject_accession || '.' || SUBSTRING(biosample.study_accession,4) as participantid,
COALESCE(biosample.study_time_collected,9999.0000) as sequencenum,
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
 FROM
biosample, expsample_2_biosample, expsample_2_file_info, file_info, arm_2_subject, arm_or_cohort
 WHERE
biosample.biosample_accession = expsample_2_biosample.biosample_accession AND
expsample_2_biosample.expsample_accession = expsample_2_file_info.expsample_accession AND
expsample_2_file_info.file_info_id = file_info.file_info_id AND
biosample.subject_accession = arm_2_subject.subject_accession AND
arm_2_subject.arm_accession = arm_or_cohort.arm_accession AND
biosample.study_accession = arm_or_cohort.study_accession AND
lcase(file_info.name) LIKE '%.fcs' AND
(file_info.detail = 'Flow cytometry result' OR  file_info.detail = 'CyTOF result')
AND ($STUDY IS NULL OR $STUDY = biosample.study_accession)
