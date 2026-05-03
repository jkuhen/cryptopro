# CryptoPro AI-Assisted Trading Prototype

Spring Boot implementation of the first five architecture layers for a professional crypto trading stack.

## Implemented Layers

- **Layer 1 - Data Layer**
  - Canonical market models for OHLCV, order book, funding, open interest, and liquidations
  - `MarketDataProvider` contract with a real `LunoMarketDataProvider` adapter
  - API key and secret parameterized in `src/main/resources/application.yml` via `LUNO_API_KEY` and `LUNO_API_SECRET`
- **Layer 2 - Preprocessing Layer**
  - Candle spike cleaning (`SpikeCleaner`)
  - Volume normalization (`VolumeNormalizer`) with z-score and relative volume
  - Session and volatility regime detection (`SessionRegimeDetector`)
- **Layer 3 - Strategy Engine**
  - Weighted multi-timeframe bias (`0.50*H1 + 0.30*M15 + 0.20*M5`)
  - Smart Money Concepts checks (liquidity sweep, BOS, FVG)
  - Derivatives fusion (OI/price relationship + funding extremes + liquidation imbalance)
- **Layer 4 - AI Layer**
  - XGBoost-style signal scoring model outputting `probabilityOfSuccess` and `confidence`
  - Regime detection model for `TRENDING`, `RANGING`, and `HIGH_MANIPULATION`
  - Automatic strategy adaptation by regime and AI confidence
  - Versioned XGBoost artifact flow (offline training + model file loading for inference)
- **Layer 5 - Execution Engine**
  - Limit order execution logic using order book levels
  - Slippage tolerance hard checks before accepting execution
  - Partial entries with configurable slice count
- **Layer 6 - Risk Management Engine (Critical)**
  - Max risk per trade controls (default 1.5%, configurable)
  - Daily loss cap guardrail (default 5%)
  - Max concurrent trade cap (default 5)
  - ATR-based stop-loss generation for volatility-aware risk sizing

## Externalized Configuration

All thresholds and weights are tunable in `src/main/resources/application.yml`:

- `cryptopro.preprocessing.*` for spike/regime thresholds
- `cryptopro.strategy.weights.*` and `cryptopro.strategy.thresholds.*` for model and decision tuning
- `cryptopro.ai.weights.*`, `cryptopro.ai.thresholds.*`, and `cryptopro.ai.adaptation.*` for AI layer tuning
- `cryptopro.ai.model.*` for scorer provider and artifact versioning
- `cryptopro.risk.*` for hard-gates and Layer 6 risk sizing (`max-spread-bps`, `max-latency-ms`, `stale-feed-seconds`, risk/trade, daily cap, concurrency, ATR stop)
- `cryptopro.execution.*` for limit offset, slippage tolerance, and partial-entry behavior
- `cryptopro.luno.*` for exchange adapter settings

## XGBoost Artifact Flow

### Offline training

Python trainer script: `ml/train_signal_xgboost.py`

```powershell
Set-Location "C:\Kuhen\Work\Projects\cryptopro"
python -m pip install -r ml\requirements.txt
python ml\train_signal_xgboost.py --version v1 --artifact-root models\signal_scorer
```

The trainer writes:

- `models/signal_scorer/v1/model.bin`
- `models/signal_scorer/v1/metadata.json`

You can also provide your own CSV dataset with required columns:

- `trendAlignmentScore`
- `volumeSpike`
- `liquiditySweepDetected`
- `oiChangePercent`
- `fundingRate`
- `volatilityRegimeEncoded`
- `label` (`0` or `1`)

### Production model loading

Inference is served by `XgboostArtifactSignalScoringModel` and controlled in `src/main/resources/application.yml`:

- `cryptopro.ai.model.provider: xgboost-artifact`
- `cryptopro.ai.model.artifact-root: models/signal_scorer`
- `cryptopro.ai.model.version: v1`
- `cryptopro.ai.model.binary-file: model.bin`
- `cryptopro.ai.model.metadata-file: metadata.json`

`fail-on-missing-artifact` can be set to `true` to hard-fail startup when artifact files are missing.

## Model Registry Endpoint

Runtime model info is exposed at:

`GET /api/v1/model/info`

Response includes:

- `modelVersion`
- `artifactPath`
- `metadataMetrics`
- `loaded` flag and provider notes

This gives ops visibility into which artifact/version is active.

## Metrics Page

Open:

`http://localhost:8081/metrics.html`

Buttons available:

- `Load Model Info` (calls `/api/v1/model/info`)
- `Copy Model Info JSON`
- `Load Pipeline Demo`

## Dashboard Page

Open:

`http://localhost:8081/dashboard.html`

Includes:

- KPI cards (transactions, success rate, slippage, errors)
- signal summary with win-rate-by-condition analytics
- recent transaction details table
- paper portfolio section (balances, positions, fees, realized/unrealized PnL, equity curve)
- monitoring section (transaction logs + error logs)
- administration section (runtime app/provider/model details)

APIs used by dashboard:

- `GET /api/v1/dashboard/overview`
- `GET /api/v1/monitoring/transactions`
- `GET /api/v1/monitoring/errors`
- `GET /api/v1/signals/summary`
- `GET /api/v1/retraining/status`

## Paper Portfolio Engine

The dashboard now includes a paper portfolio engine for simulation accounting:

- cash balance tracking
- position inventory and average price
- fee accumulation
- realized and unrealized PnL
- equity curve snapshots

Portfolio updates are applied when execution fills occur from:

- `GET /api/v1/pipeline/demo`
- `POST /api/v1/execution/simulate`

Portfolio reset control:

- `POST /api/v1/dashboard/portfolio/reset`
- Dashboard button: `Reset Paper Portfolio`

## Signal Telemetry and Win Rate by Condition

Every generated signal is logged with key conditions and outcome classification (`WIN`, `LOSS`, `SKIPPED`).

Condition win-rate analytics are exposed via:

- `GET /api/v1/signals/summary`

Tracked conditions include:

- `liquiditySweep`
- `volumeSpike`
- `oiConfirmation`

## Continuous Retraining

Continuous retraining is configurable and can run on a fixed schedule.

Config:

- `cryptopro.ai.retraining.enabled`
- `cryptopro.ai.retraining.fixed-delay-ms`
- `cryptopro.ai.retraining.min-samples`
- `cryptopro.ai.retraining.max-samples`

APIs:

- `GET /api/v1/retraining/status`
- `POST /api/v1/retraining/run`

The retraining service prepares a fresh labeled dataset from logged signals and stores it under `models/signal_scorer/retraining/`.

## Simulation Page

Open:

`http://localhost:8081/simulation.html`

The execution simulation controls were moved from `metrics.html` into this dedicated section/page.

Button available:

- `Run Execution Simulation` (posts to `/api/v1/execution/simulate`)

## Execution Simulation Endpoint

Interactive simulation endpoint:

`POST /api/v1/execution/simulate`

Supports runtime overrides without changing YAML:

- symbol and direction
- tradable flag
- quantity
- `maxPartialEntries`
- `slippageToleranceBps`
- `limitOffsetBps`
- optional custom `bids` / `asks` levels

This is ideal for testing slippage and slicing behavior from the `simulation.html` section.

## Example Trading Flow

The end-to-end flow is:

1. Pull M1 to H1 market data.
2. Detect weighted multi-timeframe trend.
3. Check microstructure + derivatives context:
   - liquidity sweep
   - volume spike
   - OI confirmation
4. Score signal via AI model.
5. If model score passes threshold (for example `> 0.75`), run risk checks.
6. Execute trade through limit-order engine.
7. Manage dynamically:
   - trail stop
   - exit on opposite signal

## Common Mistakes to Avoid

- strict timeframe alignment that suppresses valid setups
- relying on lagging indicators only
- ignoring derivatives data (OI/funding/liquidations)
- overfitting AI models to historical noise
- trading too many pairs too early

## Suggested Starter Stack

- `luno` for exchange data and execution context
- `ta` for indicator and volatility feature engineering
- `xgboost` for scoring and regime models

## Best Pairs to Start With

Start with high-liquidity pairs for cleaner behavior:

- `BTC/USDT`
- `ETH/USDT`
- `SOL/USDT`

## Risk Hard-Gates (Pre-Execution)

Before a signal is tradable, the system blocks execution when:

- spread exceeds `cryptopro.risk.max-spread-bps`
- feed latency exceeds `cryptopro.risk.max-latency-ms`
- feed age exceeds `cryptopro.risk.stale-feed-seconds`

The demo response now includes `riskPassed`, `riskReasons`, `feedLatencyMs`, `feedAgeSeconds`, and `spreadBps`.

## AI Layer Inputs and Outputs

AI signal scoring features:

- trend alignment score
- volume spike
- liquidity sweep detected
- open interest change percent
- funding rate
- volatility regime

AI outputs:

- `aiProbabilityOfSuccess` (`0..1`)
- `aiConfidence` (`0..1`)
- `detectedRegime` and `regimeConfidence`
- `aiModel` and `aiModelVersion` for versioned inference traceability

## API Demo

Call this endpoint to execute the end-to-end pipeline:

`GET /api/v1/pipeline/demo?symbol=BTCUSDT`

Example response includes `biasScore`, `smcScore`, `derivativesScore`, `finalScore`, `direction`, `sizeMultiplier`, `aiProbabilityOfSuccess`, `aiConfidence`, `detectedRegime`, `aiModelVersion`, execution fields (`executionStatus`, `executionLimitPrice`, `executionSlippageBps`, `executionSlices`), and `rationale`.

## Run Locally

```powershell
mvn spring-boot:run
```

Then open:

`http://localhost:8081/api/v1/pipeline/demo?symbol=BTCUSDT`

## Build and Test

```powershell
mvn test
mvn package
java -jar target\cryptopro-1.0-SNAPSHOT.jar
```

## Optional Startup Runner

To print one demo signal at startup, set in `src/main/resources/application.yml`:

`cryptopro.demo.runner.enabled=true`



