# AI Signal Scoring Module

## Overview

This module scores trade signals with XGBoost using five features:

- `trendScore`
- `rsi`
- `volumeSpike`
- `openInterestChange`
- `fundingRate`

It outputs:

- `probabilityOfSuccess` in `[0, 1]`
- `confidence` in `[0, 1]`

Predictions are persisted to the `ai_signal_prediction` table.

## Main classes

- `com.kuhen.cryptopro.ai.XgboostSignalTrainingService`
- `com.kuhen.cryptopro.ai.TradeSignalPredictionService`
- `com.kuhen.cryptopro.ai.XgboostArtifactSignalScoringModel`
- `com.kuhen.cryptopro.ai.XgboostSignalScoringModel`

## Database

Flyway migration:

- `src/main/resources/db/migration/V3__create_ai_signal_prediction.sql`

Table stores:

- instrument and optional signal linkage
- the five input features
- probability and confidence
- model name/version
- notes and timestamps

## Training

`XgboostSignalTrainingService` can train a model from labeled rows and write:

- `model.bin`
- `metadata.json`

under the configured artifact root/version.

## Prediction

`TradeSignalPredictionService`:

1. scores incoming features through `SignalScoringModel`
2. resolves instrument id
3. persists the prediction row
4. returns `SignalScoringResult`

## Verified commands

```powershell
cd C:\Kuhen\Work\Projects\cryptopro
mvn -q -DskipTests compile
```

```powershell
cd C:\Kuhen\Work\Projects\cryptopro
mvn test "-Dtest=XgboostSignalScoringModelTest,TradeSignalPredictionServiceTest,XgboostSignalTrainingServiceTest,RegimeDetectionModelTest"
```

```powershell
cd C:\Kuhen\Work\Projects\cryptopro
mvn test "-Dtest=XgboostSignalScoringModelTest,TradeSignalPredictionServiceTest,XgboostSignalTrainingServiceTest,RegimeDetectionModelTest,MultiTimeframeStrategyEngineTest,SmcDetectorTest,StrategyEngineTest,StrategyAdaptationServiceTest"
```

