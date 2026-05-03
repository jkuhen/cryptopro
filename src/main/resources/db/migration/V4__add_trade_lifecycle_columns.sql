-- ============================================================
-- V4: Add lifecycle columns to the trades table
--     direction       – LONG / SHORT / NEUTRAL
--     quantity        – base volume entered
--     trailing_stop   – current trailing stop price (updated by lifecycle manager)
-- ============================================================

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS direction     VARCHAR(10)      NULL,
    ADD COLUMN IF NOT EXISTS quantity      DOUBLE PRECISION NULL CHECK (quantity IS NULL OR quantity >= 0.0),
    ADD COLUMN IF NOT EXISTS trailing_stop DOUBLE PRECISION NULL CHECK (trailing_stop IS NULL OR trailing_stop >= 0.0);

COMMENT ON COLUMN trades.direction     IS 'Trade direction: LONG or SHORT';
COMMENT ON COLUMN trades.quantity      IS 'Base asset quantity entered';
COMMENT ON COLUMN trades.trailing_stop IS 'Current trailing stop price; updated by TradeLifecycleManager';

