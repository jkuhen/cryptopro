-- ============================================================
-- OHLCV candle storage for Binance WebSocket live feed
--
-- * One row per (symbol, timeframe, open_time) – enforced by
--   the unique constraint and used by the upsert path.
-- * Prices kept as DOUBLE PRECISION to match the in-memory
--   Candle record (double fields).
-- * open_time is stored as TIMESTAMPTZ (UTC) and indexed
--   descending for efficient "last N candles" queries.
-- ============================================================

CREATE TABLE IF NOT EXISTS ohlcv_candle
(
    id          BIGSERIAL        NOT NULL,
    symbol      VARCHAR(20)      NOT NULL,
    timeframe   VARCHAR(10)      NOT NULL,
    open_time   TIMESTAMPTZ      NOT NULL,
    open_price  DOUBLE PRECISION NOT NULL,
    high_price  DOUBLE PRECISION NOT NULL,
    low_price   DOUBLE PRECISION NOT NULL,
    close_price DOUBLE PRECISION NOT NULL,
    volume      DOUBLE PRECISION NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ohlcv_candle PRIMARY KEY (id),
    CONSTRAINT uq_ohlcv_candle UNIQUE (symbol, timeframe, open_time)
);

-- Efficient retrieval: latest N candles per symbol/timeframe
CREATE INDEX IF NOT EXISTS idx_ohlcv_symbol_tf_time
    ON ohlcv_candle (symbol, timeframe, open_time DESC);

