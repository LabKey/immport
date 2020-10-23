CREATE TABLE immport.study_2_condition_or_disease
(
    study_accession VARCHAR(15) NOT NULL,
    condition_reported VARCHAR(550) NOT NULL,
    condition_preferred VARCHAR(250),
    PRIMARY KEY (study_accession, condition_reported)
);

DROP TABLE IF EXISTS immport.lk_study_type;
DROP TABLE IF EXISTS immport.performance_metrics;
DROP TABLE IF EXISTS immport.planned_visit_2_arm;
DROP TABLE IF EXISTS immport.study_image;
