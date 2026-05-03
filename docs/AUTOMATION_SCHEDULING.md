# Automation Scheduling

The system is configured to run unattended for market sync, signal generation/execution, backtesting, history persistence, and AI retraining.

## Scheduled pipelines

- `cryptopro.binance.sync-cron` -> market data sync (`BinanceMarketDataScheduler`)
- `cryptopro.orchestrator.fixed-delay-ms` -> signal + risk + execution (`TradingOrchestratorService`)
- `cryptopro.ai.retraining.fixed-delay-ms` -> labeled-signal dataset prep and optional model training (`ContinuousRetrainingService`)
- `cryptopro.automation.backtest.cron` -> scheduled historical backtest summary (`AutomationSchedulerService`)
- `cryptopro.automation.persistence.cron` -> scheduled history + feature artifact persistence (`AutomationSchedulerService`)

## Default outputs

- Automated backtests: `ml/reports/auto_backtest/<utc-version>/`
  - `backtest_summary.csv`
  - `backtest_summary.json`
  - `LATEST.txt` in `ml/reports/auto_backtest/`
- Persisted history artifacts: `ml/artifacts/history/<utc-version>/`
  - `history_candles_daily.csv`
  - `ai_features_daily.csv`
  - `dataset_card.md`
  - `manifest.json`
  - `analysis/` copied files when available
  - `LATEST.txt` in `ml/artifacts/history/`

## Important properties

See `src/main/resources/application.yml` under:

- `cryptopro.ai.retraining.*`
- `cryptopro.automation.*`
- `cryptopro.orchestrator.*`
- `cryptopro.binance.*`

If you need to pause a workflow, set its `enabled` flag to `false` without changing code.

