CREATE TABLE IF NOT EXISTS ai_signal_prediction (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instrument(id),
    signal_id BIGINT NULL REFERENCES signals(id) ON DELETE SET NULL,
    predicted_at TIMESTAMPTZ NOT NULL,
    trend_score DOUBLE PRECISION NOT NULL,
    rsi DOUBLE PRECISION NOT NULL CHECK (rsi >= 0.0 AND rsi <= 100.0),
    volume_spike DOUBLE PRECISION NOT NULL,
    open_interest_change DOUBLE PRECISION NOT NULL,
    funding_rate DOUBLE PRECISION NOT NULL,
    probability_score DOUBLE PRECISION NOT NULL CHECK (probability_score >= 0.0 AND probability_score <= 1.0),
    confidence_score DOUBLE PRECISION NOT NULL CHECK (confidence_score >= 0.0 AND confidence_score <= 1.0),
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_signal_prediction_instrument_time
    ON ai_signal_prediction (instrument_id, predicted_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_signal_prediction_signal_id
    ON ai_signal_prediction (signal_id);

CREATE INDEX IF NOT EXISTS idx_ai_signal_prediction_model
    ON ai_signal_prediction (model_name, model_version, predicted_at DESC);

