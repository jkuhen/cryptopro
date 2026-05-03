-- ============================================================
-- Market analytics / trading schema
--
-- Goals
-- * Normalise repeated symbol metadata into a single instrument table.
-- * Keep high-volume time-series tables partitionable by event time.
-- * Index latest-by-symbol/time queries efficiently.
-- * Add constrained types for signal / trade semantics.
-- ============================================================

-- --------------------------------------------------------------------------
-- Normalised instrument dimension
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS instrument
(
    id            BIGSERIAL    PRIMARY KEY,
    exchange_name VARCHAR(30)  NOT NULL,
    symbol        VARCHAR(20)  NOT NULL,
    base_asset    VARCHAR(10)  NOT NULL,
    quote_asset   VARCHAR(10)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_instrument_exchange_symbol UNIQUE (exchange_name, symbol)
);

CREATE INDEX IF NOT EXISTS idx_instrument_symbol
    ON instrument (symbol);

INSERT INTO instrument (exchange_name, symbol, base_asset, quote_asset)
VALUES
    ('BINANCE', 'BTCUSDT', 'BTC', 'USDT'),
    ('BINANCE', 'ETHUSDT', 'ETH', 'USDT'),
    ('BINANCE', 'SOLUSDT', 'SOL', 'USDT')
ON CONFLICT (exchange_name, symbol) DO NOTHING;

-- --------------------------------------------------------------------------
-- Domain types
-- --------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'signal_type_enum') THEN
        CREATE TYPE signal_type_enum AS ENUM ('BUY', 'SELL');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'trade_status_enum') THEN
        CREATE TYPE trade_status_enum AS ENUM (
            'OPEN',
            'CLOSED',
            'STOP_LOSS_HIT',
            'TAKE_PROFIT_HIT',
            'CANCELLED'
        );
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'timeframe_enum') THEN
        CREATE TYPE timeframe_enum AS ENUM ('M1', 'M5', 'M15', 'H1');
    END IF;
END $$;

-- --------------------------------------------------------------------------
-- High-volume derivatives snapshots
-- Partition monthly by recorded_at.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS derivatives_data
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    instrument_id  BIGINT           NOT NULL REFERENCES instrument(id),
    recorded_at    TIMESTAMPTZ      NOT NULL,
    open_interest  DOUBLE PRECISION NOT NULL CHECK (open_interest >= 0.0),
    funding_rate   DOUBLE PRECISION NOT NULL,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_derivatives_data PRIMARY KEY (id, recorded_at),
    CONSTRAINT uq_derivatives_data_instrument_time UNIQUE (instrument_id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE INDEX IF NOT EXISTS idx_derivatives_data_instrument_time
    ON derivatives_data (instrument_id, recorded_at DESC);

CREATE TABLE IF NOT EXISTS derivatives_data_default
    PARTITION OF derivatives_data DEFAULT;

-- --------------------------------------------------------------------------
-- Trading signals
-- Non-partitioned for now; much lower write volume than tick/candle/features.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS signals
(
    id               BIGSERIAL         PRIMARY KEY,
    instrument_id    BIGINT            NOT NULL REFERENCES instrument(id),
    timeframe        timeframe_enum    NOT NULL,
    signal_type      signal_type_enum  NOT NULL,
    confidence_score DOUBLE PRECISION  NOT NULL CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0),
    created_at       TIMESTAMPTZ       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_signals_instrument_time
    ON signals (instrument_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_signals_type_time
    ON signals (signal_type, created_at DESC);

-- --------------------------------------------------------------------------
-- Executed / simulated trades
-- Normalised via instrument_id and optional link back to originating signal.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trades
(
    id           BIGSERIAL         PRIMARY KEY,
    instrument_id BIGINT           NOT NULL REFERENCES instrument(id),
    signal_id    BIGINT            NULL REFERENCES signals(id) ON DELETE SET NULL,
    entry_price  DOUBLE PRECISION  NOT NULL CHECK (entry_price > 0.0),
    exit_price   DOUBLE PRECISION  NULL CHECK (exit_price IS NULL OR exit_price > 0.0),
    stop_loss    DOUBLE PRECISION  NULL CHECK (stop_loss IS NULL OR stop_loss > 0.0),
    take_profit  DOUBLE PRECISION  NULL CHECK (take_profit IS NULL OR take_profit > 0.0),
    status       trade_status_enum NOT NULL DEFAULT 'OPEN',
    pnl          DOUBLE PRECISION  NOT NULL DEFAULT 0.0,
    created_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
    closed_at    TIMESTAMPTZ       NULL,

    CONSTRAINT chk_trade_closed_at
        CHECK (
            (status = 'OPEN' AND closed_at IS NULL)
            OR (status <> 'OPEN' AND closed_at IS NOT NULL)
        ),
    CONSTRAINT chk_trade_exit_price
        CHECK (
            (status = 'OPEN' AND exit_price IS NULL)
            OR (status <> 'OPEN' AND exit_price IS NOT NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_trades_instrument_time
    ON trades (instrument_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_trades_status_time
    ON trades (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_trades_signal_id
    ON trades (signal_id);

-- --------------------------------------------------------------------------
-- Model / strategy feature snapshots
-- Partition monthly by recorded_at.
-- Optional link to a signal when a feature row directly fed a decision.
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS features
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    instrument_id  BIGINT           NOT NULL REFERENCES instrument(id),
    signal_id      BIGINT           NULL REFERENCES signals(id) ON DELETE SET NULL,
    recorded_at    TIMESTAMPTZ      NOT NULL,
    ema20          DOUBLE PRECISION NOT NULL,
    ema50          DOUBLE PRECISION NOT NULL,
    ema200         DOUBLE PRECISION NOT NULL,
    rsi            DOUBLE PRECISION NOT NULL CHECK (rsi >= 0.0 AND rsi <= 100.0),
    atr            DOUBLE PRECISION NOT NULL CHECK (atr >= 0.0),
    volume_ma      DOUBLE PRECISION NOT NULL CHECK (volume_ma >= 0.0),
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_features PRIMARY KEY (id, recorded_at),
    CONSTRAINT uq_features_instrument_time UNIQUE (instrument_id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE INDEX IF NOT EXISTS idx_features_instrument_time
    ON features (instrument_id, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_features_signal_id
    ON features (signal_id);

CREATE TABLE IF NOT EXISTS features_default
    PARTITION OF features DEFAULT;

-- --------------------------------------------------------------------------
-- Partition helper: create monthly partitions from previous month through the
-- next 12 months so the system is ready immediately while still having a
-- DEFAULT partition as a safety net.
-- --------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION ensure_monthly_partitions(
    p_parent_table TEXT,
    p_partition_prefix TEXT,
    p_start_month DATE,
    p_month_count INTEGER
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_month_start DATE;
    v_month_end   DATE;
    v_partition_name TEXT;
    i INTEGER;
BEGIN
    FOR i IN 0..(p_month_count - 1) LOOP
        v_month_start := (date_trunc('month', p_start_month)::date + make_interval(months => i))::date;
        v_month_end := (v_month_start + INTERVAL '1 month')::date;
        v_partition_name := format('%s_%s', p_partition_prefix, to_char(v_month_start, 'YYYYMM'));

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
            v_partition_name,
            p_parent_table,
            v_month_start::timestamptz,
            v_month_end::timestamptz
        );
    END LOOP;
END;
$$;

SELECT ensure_monthly_partitions(
    'derivatives_data',
    'derivatives_data_p',
    (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month')::date,
    14
);

SELECT ensure_monthly_partitions(
    'features',
    'features_p',
    (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month')::date,
    14
);

DROP FUNCTION ensure_monthly_partitions(TEXT, TEXT, DATE, INTEGER);


