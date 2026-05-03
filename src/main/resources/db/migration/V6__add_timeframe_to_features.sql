-- ============================================================
-- V6: Add timeframe column to features table
--
-- The features table previously had a unique key on
-- (instrument_id, recorded_at) which means only one feature
-- row could exist per instrument per timestamp. This makes it
-- impossible to distinguish H1, M15, and M5 features computed
-- at the same wall-clock time.
--
-- This migration:
--   1. Drops the old unique constraint on the table/partitions.
--   2. Adds a timeframe VARCHAR(4) column (NOT NULL with default
--      'M5' to satisfy existing rows).
--   3. Re-creates the unique constraint as
--      (instrument_id, timeframe, recorded_at).
-- ============================================================

-- Step 1: Add the timeframe column to the parent table.
-- We use NOT NULL with a DEFAULT so that existing rows get a
-- sensible value instead of violating the constraint.
ALTER TABLE features
    ADD COLUMN IF NOT EXISTS timeframe VARCHAR(4) NOT NULL DEFAULT 'M5';

-- Step 2: Drop the old unique constraint (must be done on the
-- parent; Postgres propagates to partitions automatically).
ALTER TABLE features
    DROP CONSTRAINT IF EXISTS uq_features_instrument_time;

-- Step 3: Add the new unique constraint that includes timeframe.
-- Because features is a partitioned table the unique constraint
-- must include the partition key (recorded_at).
ALTER TABLE features
    ADD CONSTRAINT uq_features_instrument_timeframe_time
        UNIQUE (instrument_id, timeframe, recorded_at);

-- Step 4: Add an index on the new column for fast per-timeframe lookups.
CREATE INDEX IF NOT EXISTS idx_features_instrument_timeframe_time
    ON features (instrument_id, timeframe, recorded_at DESC);

