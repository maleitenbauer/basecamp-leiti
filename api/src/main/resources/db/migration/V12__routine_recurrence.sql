-- Calendar-style recurrence for routines (replaces the plain DAILY/WEEKLY switch from V11).
--
-- A routine is either on a SCHEDULE ('RECURRING': due on specific days, like a calendar event) or flexible
-- ('TIMES_PER_WEEK': N times in a Monday-Sunday week, on whichever days suit). Existing rows are converted:
-- DAILY becomes "every day", WEEKLY becomes "N times a week" exactly as it behaved before.

ALTER TABLE logbook.routine
    ADD COLUMN schedule_type  varchar(20) NOT NULL DEFAULT 'RECURRING'
        CHECK (schedule_type IN ('RECURRING', 'TIMES_PER_WEEK')),
    ADD COLUMN recur_unit     varchar(10) CHECK (recur_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    ADD COLUMN recur_interval integer     NOT NULL DEFAULT 1 CHECK (recur_interval BETWEEN 1 AND 999),
    -- WEEK: which weekdays, as a bitmask (bit 0 = Monday ... bit 6 = Sunday)
    ADD COLUMN recur_weekdays integer     CHECK (recur_weekdays BETWEEN 1 AND 127),
    -- MONTH: on a day of the month, on the last day, or on the nth weekday (nth 5 = the last one)
    ADD COLUMN month_mode     varchar(15) CHECK (month_mode IN ('DAY_OF_MONTH', 'LAST_DAY', 'NTH_WEEKDAY')),
    ADD COLUMN month_day      integer     CHECK (month_day BETWEEN 1 AND 31), -- MONTH day-of-month, and YEAR's day
    ADD COLUMN month_nth      integer     CHECK (month_nth BETWEEN 1 AND 5),
    ADD COLUMN month_weekday  integer     CHECK (month_weekday BETWEEN 1 AND 7), -- ISO: 1 = Monday ... 7 = Sunday
    ADD COLUMN year_month     integer     CHECK (year_month BETWEEN 1 AND 12),
    ADD COLUMN start_date     date,
    ADD COLUMN end_type       varchar(15) NOT NULL DEFAULT 'NEVER' CHECK (end_type IN ('NEVER', 'ON_DATE', 'AFTER_COUNT')),
    ADD COLUMN end_on         date,       -- for AFTER_COUNT this is the date of the last occurrence, worked out when saved
    ADD COLUMN end_count      integer     CHECK (end_count BETWEEN 1 AND 1000),
    -- an unfinished occurrence stays open (and in the reminder) until it's done, instead of lapsing at midnight
    ADD COLUMN carry_over     boolean     NOT NULL DEFAULT false;

UPDATE logbook.routine
SET schedule_type = CASE WHEN frequency = 'WEEKLY' THEN 'TIMES_PER_WEEK' ELSE 'RECURRING' END,
    recur_unit    = CASE WHEN frequency = 'DAILY' THEN 'DAY' END,
    start_date    = created_at::date;

ALTER TABLE logbook.routine DROP COLUMN frequency;

ALTER TABLE logbook.routine ADD CONSTRAINT routine_schedule_ck
    CHECK (schedule_type = 'TIMES_PER_WEEK' OR (recur_unit IS NOT NULL AND start_date IS NOT NULL));
