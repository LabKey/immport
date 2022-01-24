/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
*/



PARAMETERS($STUDY VARCHAR DEFAULT NULL)
SELECT DISTINCT
dataset.file_info_name,
dataset.filesize,
dataset.study_accession
FROM(
   SELECT
	file_info.name AS file_info_name,
	file_info.detail AS file_info_purpose,
	file_info.filesize_bytes as filesize,
	expsample_2_biosample.expsample_accession,
	biosample.biosample_accession,
	biosample.subject_accession || '.' || SUBSTRING(biosample.study_accession,4) as participantid,
	biosample.study_accession,
   	expsample.experiment_accession,
	arm_or_cohort.arm_accession,
	arm_or_cohort.name AS arm_name
 	FROM
	biosample, expsample, expsample_2_biosample, expsample_2_file_info, file_info, arm_2_subject, arm_or_cohort
 	WHERE
	biosample.biosample_accession = expsample_2_biosample.biosample_accession AND
	expsample_2_biosample.expsample_accession = expsample_2_file_info.expsample_accession AND
   	expsample.expsample_accession = expsample_2_biosample.expsample_accession AND
	expsample_2_file_info.file_info_id = file_info.file_info_id AND
	biosample.subject_accession = arm_2_subject.subject_accession AND
	arm_2_subject.arm_accession = arm_or_cohort.arm_accession AND
	biosample.study_accession = arm_or_cohort.study_accession AND
	(file_info.name LIKE '%.xml' OR file_info.name LIKE '%.jo') AND
	file_info.detail = 'Flow cytometry workspace' AND 
	($STUDY IS NULL OR $STUDY = biosample.study_accession)) AS dataset