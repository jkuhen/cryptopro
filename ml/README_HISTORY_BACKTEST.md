# History Backtest Runner

This runner processes Binance daily history CSV files and generates baseline analytics for:

- Buy and hold
- SMA(20/50) trend-following (long-only)
- RSI(14) mean reversion (long-only)

## Input format

The script supports the CryptoDataDownload-style files in `data/` with the first metadata line and then a CSV header, e.g.:

- `https://www.CryptoDataDownload.com`
- `Unix,Date,Symbol,Open,High,Low,Close,...`

## Run

From repository root (PowerShell runner, no Python dependency):

```powershell
.\ml\backtest_history.ps1
```

Optional arguments:

```powershell
.\ml\backtest_history.ps1 -InputDir data -Pattern "Binance_*USDT_d.csv" -OutputDir "ml/reports/history_backtest" -InitialCapital 10000 -FeeBps 5
```

Python runner (if Python is installed):

```powershell
python -u .\ml\backtest_history.py
```

## Outputs

The script writes:

- `ml/reports/history_backtest/backtest_metrics_by_symbol.csv`
- `ml/reports/history_backtest/backtest_summary.csv`
- `ml/reports/history_backtest/equity_curves.csv`
- `ml/reports/history_backtest/backtest_summary.json`
