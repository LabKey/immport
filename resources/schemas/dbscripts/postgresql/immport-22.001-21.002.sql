-- this is a redo of 21.003-21.004, to handle case where that script did not run (due to PR merge 'problem')

ALTER TABLE immport.arm_or_cohort DROP COLUMN IF EXISTS type;
ALTER TABLE immport.arm_or_cohort ADD COLUMN IF NOT EXISTS type_reported VARCHAR(100);
ALTER TABLE immport.arm_or_cohort ADD COLUMN IF NOT EXISTS type_preferred VARCHAR(100);