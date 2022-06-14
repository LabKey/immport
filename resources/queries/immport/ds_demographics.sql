/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
PARAMETERS($STUDY VARCHAR DEFAULT NULL)
SELECT
subject.subject_accession || '.' || SUBSTRING(study_accession,4) as participantid,
subject.subject_accession,
subject.description,
a2s.subject_phenotype AS phenotype,
a2s.min_subject_age AS age_reported,
a2s.age_unit,
a2s.age_event,
a2s.age_event_specify,
a2s.subject_location as location,
subject.strain,
subject.strain_characteristics,
subject.gender,
subject.ethnicity,
--DR20 subject.population_name,
subject.race,
subject.race_specify,
subject.species,
--DR20 subject.taxonomy_id,
subject.workspace_id
FROM subject INNER JOIN arm_2_subject a2s ON subject.subject_accession = a2s.subject_accession
  INNER JOIN arm_or_cohort arm ON a2s.arm_accession = arm.arm_accession
WHERE $STUDY IS NULL OR arm.study_accession=$STUDY
