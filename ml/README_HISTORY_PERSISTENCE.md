# History Persistence for Testing and AI

This flow snapshots historical data and analytics into a versioned artifact bundle so future tests and AI training can use stable, repeatable inputs.

## What gets persisted

For each run, a new folder is created under `ml/artifacts/history/<version>/` with:

- `history_candles_daily.csv` : normalized OHLCV daily candles for all matched symbols
- `ai_features_daily.csv` : engineered features and next-day label (`label_up_1d`)
- `dataset_card.md` : quick metadata and row counts
- `manifest.json` : full provenance and output stats
- `analysis/` : copied backtest outputs if available

A pointer file `ml/artifacts/history/LATEST.txt` is updated to the latest version.

## Run

From repository root:

```powershell
.\ml\persist_history_artifacts.ps1
```

Optional arguments:

```powershell
.\ml\persist_history_artifacts.ps1 -InputDir data -Pattern "Binance_*USDT_d.csv" -BacktestReportDir "ml/reports/history_backtest" -OutputRoot "ml/artifacts/history" -Version "v20260426"
```

## Recommended workflow

1. Generate analysis reports:

```powershell
.\ml\backtest_history.ps1
```

2. Persist canonical snapshots:

```powershell
.\ml\persist_history_artifacts.ps1
```

3. Use `ml/artifacts/history/LATEST.txt` to load the newest dataset in future tests/training jobs.

