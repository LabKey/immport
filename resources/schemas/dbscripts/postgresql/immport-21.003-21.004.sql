ALTER TABLE immport.arm_or_cohort RENAME COLUMN type TO type_reported;
ALTER TABLE immport.arm_or_cohort ALTER COLUMN type_reported TYPE VARCHAR(100);
ALTER TABLE immport.arm_or_cohort ADD COLUMN type_preferred VARCHAR(100);