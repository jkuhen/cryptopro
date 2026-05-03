-- V7: Store projected entry price, stop loss, and take profit on the signals row.
--
-- When a signal is generated the strategy engine knows the current price and
-- ATR, so we can immediately record the ATR-based levels.  This lets the
-- Open-Signal dashboard card show Entry / SL / TP even before the signal has
-- been accepted by the execution engine and persisted as a trade.
--
-- All three columns are nullable: historical rows that predate this migration
-- will simply show NULL (displayed as "–" in the UI), and new signals will
-- always populate them.

ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS entry_price  DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS stop_loss    DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS take_profit  DOUBLE PRECISION NULL;

