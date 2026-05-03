-- ============================================================
-- V5: Analytics tables
-- ============================================================

-- ------------------------------------------------------------------
-- Per-trade analytics row, written when a trade is closed
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trade_analytics
(
    id                  BIGSERIAL        PRIMARY KEY,
    trade_uuid          VARCHAR(36)      NOT NULL,
    symbol              VARCHAR(20)      NOT NULL,
    direction           VARCHAR(10)      NOT NULL,
    quantity            DOUBLE PRECISION NOT NULL CHECK (quantity >= 0),
    entry_price         DOUBLE PRECISION NOT NULL,
    close_price         DOUBLE PRECISION NOT NULL,
    pnl                 DOUBLE PRECISION NOT NULL,
    pnl_percent         DOUBLE PRECISION NOT NULL,
    close_reason        VARCHAR(30)      NOT NULL,
    regime              VARCHAR(30)      NULL,
    liquidity_sweep     BOOLEAN          NOT NULL DEFAULT FALSE,
    volume_spike        BOOLEAN          NOT NULL DEFAULT FALSE,
    oi_confirmation     BOOLEAN          NOT NULL DEFAULT FALSE,
    hold_duration_sec   BIGINT           NOT NULL DEFAULT 0,
    opened_at           TIMESTAMPTZ      NOT NULL,
    closed_at           TIMESTAMPTZ      NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trade_analytics_symbol_closed
    ON trade_analytics (symbol, closed_at DESC);

CREATE INDEX IF NOT EXISTS idx_trade_analytics_regime
    ON trade_analytics (regime, closed_at DESC);

CREATE INDEX IF NOT EXISTS idx_trade_analytics_close_reason
    ON trade_analytics (close_reason, closed_at DESC);

-- ------------------------------------------------------------------
-- Aggregated performance snapshots computed on-demand and cached
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS strategy_performance_snapshot
(
    id                  BIGSERIAL        PRIMARY KEY,
    symbol              VARCHAR(20)      NOT NULL,
    window_label        VARCHAR(20)      NOT NULL,   -- ALL_TIME | DAILY | WEEKLY
    window_start        TIMESTAMPTZ      NULL,
    window_end          TIMESTAMPTZ      NULL,
    total_trades        INT              NOT NULL DEFAULT 0,
    wins                INT              NOT NULL DEFAULT 0,
    losses              INT              NOT NULL DEFAULT 0,
    win_rate_percent    DOUBLE PRECISION NOT NULL DEFAULT 0,
    gross_profit        DOUBLE PRECISION NOT NULL DEFAULT 0,
    gross_loss          DOUBLE PRECISION NOT NULL DEFAULT 0,
    profit_factor       DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_pnl           DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_drawdown        DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_drawdown_pct    DOUBLE PRECISION NOT NULL DEFAULT 0,
    computed_at         TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_strategy_perf_symbol_window
    ON strategy_performance_snapshot (symbol, window_label, computed_at DESC);

