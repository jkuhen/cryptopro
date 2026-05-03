import argparse
import json
from pathlib import Path
from typing import Dict, Tuple

import numpy as np
import pandas as pd

TRADING_DAYS_PER_YEAR = 365.0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run baseline backtests and analytics on Binance daily history CSV files."
    )
    parser.add_argument(
        "--input-dir",
        default="data",
        help="Directory containing Binance_*_d.csv files",
    )
    parser.add_argument(
        "--pattern",
        default="Binance_*USDT_d.csv",
        help="File glob pattern under input directory",
    )
    parser.add_argument(
        "--output-dir",
        default="ml/reports/history_backtest",
        help="Directory for generated reports",
    )
    parser.add_argument(
        "--initial-capital",
        type=float,
        default=10_000.0,
        help="Initial capital used for equity curves",
    )
    parser.add_argument(
        "--fee-bps",
        type=float,
        default=5.0,
        help="Per side transaction fee in basis points",
    )
    return parser.parse_args()


def compute_rsi(close: pd.Series, period: int = 14) -> pd.Series:
    delta = close.diff()
    gain = delta.clip(lower=0.0)
    loss = -delta.clip(upper=0.0)

    avg_gain = gain.ewm(alpha=1.0 / period, adjust=False, min_periods=period).mean()
    avg_loss = loss.ewm(alpha=1.0 / period, adjust=False, min_periods=period).mean()

    rs = avg_gain / avg_loss.replace(0.0, np.nan)
    rsi = 100.0 - (100.0 / (1.0 + rs))
    return rsi.fillna(50.0)


def load_price_history(path: Path) -> pd.DataFrame:
    frame = pd.read_csv(path, skiprows=1)
    frame["Date"] = pd.to_datetime(frame["Date"], utc=True)
    frame = frame.sort_values("Date").reset_index(drop=True)
    frame["close"] = frame["Close"].astype(float)
    frame["ret"] = frame["close"].pct_change().fillna(0.0)
    frame["symbol"] = frame["Symbol"].astype(str)
    return frame


def _annualized_return(equity: pd.Series) -> float:
    n = len(equity)
    if n < 2 or equity.iloc[0] <= 0.0:
        return 0.0
    total_return = equity.iloc[-1] / equity.iloc[0]
    if total_return <= 0.0:
        return -1.0
    years = n / TRADING_DAYS_PER_YEAR
    if years <= 0.0:
        return 0.0
    return float(total_return ** (1.0 / years) - 1.0)


def _max_drawdown(equity: pd.Series) -> float:
    peak = equity.cummax()
    dd = equity / peak - 1.0
    return float(dd.min())


def _metrics(name: str, strategy_returns: pd.Series, initial_capital: float) -> Tuple[pd.Series, Dict[str, float]]:
    equity = initial_capital * (1.0 + strategy_returns).cumprod()

    mean_daily = float(strategy_returns.mean())
    vol_daily = float(strategy_returns.std(ddof=0))
    ann_return = _annualized_return(equity)
    ann_vol = vol_daily * np.sqrt(TRADING_DAYS_PER_YEAR)
    sharpe = 0.0 if ann_vol == 0.0 else ann_return / ann_vol

    positive_days = float((strategy_returns > 0.0).mean())

    result = {
        "strategy": name,
        "total_return": float(equity.iloc[-1] / equity.iloc[0] - 1.0),
        "cagr": ann_return,
        "annualized_volatility": float(ann_vol),
        "sharpe": float(sharpe),
        "max_drawdown": _max_drawdown(equity),
        "positive_day_ratio": positive_days,
    }
    return equity.rename(name), result


def backtest_symbol(frame: pd.DataFrame, initial_capital: float, fee_bps: float) -> Tuple[pd.DataFrame, pd.DataFrame]:
    close = frame["close"]
    ret = frame["ret"]

    fee_rate = fee_bps / 10_000.0

    sma_fast = close.rolling(20).mean()
    sma_slow = close.rolling(50).mean()
    trend_signal = (sma_fast > sma_slow).astype(float)

    rsi = compute_rsi(close, period=14)
    mr_signal = pd.Series(0.0, index=frame.index)
    mr_signal[rsi < 30.0] = 1.0
    mr_signal[rsi > 55.0] = 0.0
    mr_signal = mr_signal.replace(0.0, np.nan).ffill().fillna(0.0)

    # Apply signal with one-bar lag to avoid look-ahead bias.
    trend_pos = trend_signal.shift(1).fillna(0.0)
    mr_pos = mr_signal.shift(1).fillna(0.0)

    trend_turnover = trend_pos.diff().abs().fillna(0.0)
    mr_turnover = mr_pos.diff().abs().fillna(0.0)

    buy_hold_ret = ret
    trend_ret = trend_pos * ret - trend_turnover * fee_rate
    mr_ret = mr_pos * ret - mr_turnover * fee_rate

    eq_bh, m_bh = _metrics("buy_and_hold", buy_hold_ret, initial_capital)
    eq_tr, m_tr = _metrics("sma20_50", trend_ret, initial_capital)
    eq_mr, m_mr = _metrics("rsi_mean_reversion", mr_ret, initial_capital)

    equity_frame = pd.DataFrame(
        {
            "Date": frame["Date"],
            "symbol": frame["symbol"],
            "buy_and_hold": eq_bh,
            "sma20_50": eq_tr,
            "rsi_mean_reversion": eq_mr,
        }
    )

    metrics_frame = pd.DataFrame([m_bh, m_tr, m_mr])
    metrics_frame.insert(0, "symbol", frame["symbol"].iloc[0])

    return equity_frame, metrics_frame


def run_batch(input_dir: Path, pattern: str, output_dir: Path, initial_capital: float, fee_bps: float) -> None:
    files = sorted(input_dir.glob(pattern))
    if not files:
        raise FileNotFoundError(f"No files found in {input_dir} with pattern '{pattern}'")

    output_dir.mkdir(parents=True, exist_ok=True)

    all_equity = []
    all_metrics = []

    for file_path in files:
        history = load_price_history(file_path)
        equity, metrics = backtest_symbol(history, initial_capital=initial_capital, fee_bps=fee_bps)
        all_equity.append(equity)
        all_metrics.append(metrics)

    equity_frame = pd.concat(all_equity, ignore_index=True)
    metrics_frame = pd.concat(all_metrics, ignore_index=True)

    summary = (
        metrics_frame.groupby("strategy", as_index=False)
        .agg(
            symbols=("symbol", "count"),
            avg_total_return=("total_return", "mean"),
            avg_cagr=("cagr", "mean"),
            avg_sharpe=("sharpe", "mean"),
            avg_max_drawdown=("max_drawdown", "mean"),
        )
        .sort_values("avg_sharpe", ascending=False)
        .reset_index(drop=True)
    )

    metrics_csv = output_dir / "backtest_metrics_by_symbol.csv"
    summary_csv = output_dir / "backtest_summary.csv"
    equity_csv = output_dir / "equity_curves.csv"
    summary_json = output_dir / "backtest_summary.json"

    metrics_frame.to_csv(metrics_csv, index=False)
    summary.to_csv(summary_csv, index=False)
    equity_frame.to_csv(equity_csv, index=False)
    summary_json.write_text(summary.to_json(orient="records", indent=2), encoding="utf-8")

    print("Processed files:")
    for file_path in files:
        print(f"- {file_path}")
    print("\nTop strategies by avg sharpe:")
    print(summary.to_string(index=False, float_format=lambda x: f"{x:0.4f}"))
    print("\nGenerated:")
    print(f"- {metrics_csv}")
    print(f"- {summary_csv}")
    print(f"- {equity_csv}")
    print(f"- {summary_json}")


def main() -> None:
    args = parse_args()
    run_batch(
        input_dir=Path(args.input_dir),
        pattern=args.pattern,
        output_dir=Path(args.output_dir),
        initial_capital=args.initial_capital,
        fee_bps=args.fee_bps,
    )


if __name__ == "__main__":
    main()

