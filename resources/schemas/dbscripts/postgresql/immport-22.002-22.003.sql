-- this is a redo of 21.003-21.004, to handle case where that script did not run (due to PR merge 'problem')
-- ...and a redo of redo 22.001-21.002, which was incorrectly named

ALTER TABLE immport.arm_or_cohort DROP COLUMN IF EXISTS type;
ALTER TABLE immport.arm_or_cohort ADD COLUMN IF NOT EXISTS type_reported VARCHAR(100);
ALTER TABLE immport.arm_or_cohort ADD COLUMN IF NOT EXISTS type_preferred VARCHAR(100);