-- this is a redo of 21.003-21.004, to handle case where that script did not run (due to PR merge 'problem')
-- ...and a redo of redo 22.001-21.002, which was incorrectly named

ALTER TABLE immport.arm_2_subject ADD COLUMN IF NOT EXISTS subject_location VARCHAR(100);
