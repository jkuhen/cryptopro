# Feature Engineering Module

## Overview

The Feature Engineering Module is a Spring Boot service that calculates and persists technical analysis indicators for cryptocurrency market data. It efficiently processes OHLCV (Open, High, Low, Close, Volume) candle data to compute key trading signals.

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────┐
│  FeatureEngineeringService (Orchestrator)               │
│  - calculateAndPersistFeatures()                        │
│  - calculateAndPersistFeaturesForAll()                  │
│  - calculateFeatures()                                  │
└──┬──────────────────────────────────────────────────────┘
   │
   ├─→ FeatureEngineeringUtil (Calculations)
   │   - calculateEma()
   │   - calculateRsi()
   │   - calculateAtr()
   │   - calculateVolumeMA()
   │
   ├─→ FeaturesRepository (Persistence)
   │   - upsert()
   │   - findLatest()
   │   - findRange()
   │
   ├─→ OhlcvCandleRepository (Data Source)
   │   - findLatest()
   │
   └─→ FeaturesEntity (Persistence Model)
       - ema20, ema50, ema200 (prices)
       - rsi (0-100)
       - atr (volatility)
       - volumeMa (volume)
```

## Technical Indicators

### 1. Exponential Moving Averages (EMA)

**Periods:** 20, 50, 200

**Formula:**
```
SMA = sum of N prices / N
EMA_today = (Close - EMA_yesterday) * α + EMA_yesterday
α = 2 / (N + 1)
```

**Purpose:** Smooth price trend analysis with emphasis on recent prices.

**Example:**
```java
Double ema20 = FeatureEngineeringUtil.calculateEma(closePrices, 20);
Double ema50 = FeatureEngineeringUtil.calculateEma(closePrices, 50);
Double ema200 = FeatureEngineeringUtil.calculateEma(closePrices, 200);
```

### 2. Relative Strength Index (RSI)

**Period:** 14 (default)

**Formula:**
```
RS = average gain / average loss
RSI = 100 - (100 / (1 + RS))
Range: 0-100
```

**Interpretation:**
- RSI > 70: Overbought (potential sell signal)
- RSI < 30: Oversold (potential buy signal)

**Example:**
```java
Double rsi = FeatureEngineeringUtil.calculateRsi(closePrices, 14);
```

### 3. Average True Range (ATR)

**Period:** 14 (default)

**Formula:**
```
True Range = max(
  high - low,
  abs(high - prev_close),
  abs(low - prev_close)
)
ATR = Wilder's smoothed average of true range
```

**Purpose:** Measures volatility/ price movement range.

**Example:**
```java
Double atr = FeatureEngineeringUtil.calculateAtrFromPrices(
    highs, lows, closes, 14
);
```

### 4. Volume Moving Average

**Period:** 20 (default)

**Formula:**
```
Volume MA = (V_n + V_n-1 + ... + V_n-period+1) / period
```

**Purpose:** Identifies volume trends and market strength.

**Example:**
```java
Double volumeMa = FeatureEngineeringUtil.calculateVolumeMA(volumes, 20);
```

## Usage

### Basic Feature Calculation

```java
@Service
public class TradingSignalService {
    private final FeatureEngineeringService featureService;
    
    public void processLatestCandle(String symbol, String timeframe) {
        Long instrumentId = getInstrumentId(symbol);
        
        // Calculate and persist features for latest candle
        boolean success = featureService.calculateAndPersistFeatures(
            instrumentId, 
            symbol, 
            timeframe,
            250  // minimum candles required
        );
        
        if (success) {
            LOGGER.info("Features calculated for {}", symbol);
        }
    }
}
```

### Batch Processing

```java
// Calculate features for all symbols and timeframes
List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT");
List<String> timeframes = Arrays.asList("M5", "M15", "H1");

int count = featureService.calculateAndPersistFeaturesForAll(
    symbols, 
    timeframes, 
    250
);
LOGGER.info("Processed {} feature sets", count);
```

### Retrieving Features

```java
// Get latest features for a symbol
List<FeaturesEntity> features = featureService.getLatestFeatures(
    instrumentId, 
    100  // last 100 feature rows
);

// Get features in a time range
List<FeaturesEntity> rangedFeatures = featureService.getFeaturesInRange(
    instrumentId,
    fromTime,
    toTime
);
```

## Efficiency & Incremental Updates

### Design for Efficiency

1. **Lazy Initialization:** Features are only calculated when new candles arrive
2. **Incremental Processing:** Only the latest candle is fully calculated
3. **Upsert Semantics:** Late-arriving corrections are handled gracefully
4. **Minimal Query:** Queries only fetch required data (usually last 250 candles)

### Calculation Requirements

```
Indicator       Min Candles    Period
──────────────────────────────────────
EMA20              20            20
EMA50              50            50
EMA200            200           200
RSI(14)            15            14+1
ATR(14)            15            14+1
Volume MA          20            20

Maximum required:  200 candles
```

### Database Partitioning

The `features` table is partitioned monthly by `recorded_at` for efficient storage and query performance:

```sql
-- Monthly partitions
features_p_202604
features_p_202605
features_p_202606
...
features_default  -- Fallback for unmatched dates
```

## API Reference

### FeatureEngineeringService

```java
public boolean calculateAndPersistFeatures(
    Long symbolId,
    String symbol,
    String timeframe,
    int minCandlesRequired
)
```
**Returns:** true if successful, false otherwise

---

```java
public int calculateAndPersistFeaturesForAll(
    List<String> symbols,
    List<String> timeframes,
    int minCandlesRequired
)
```
**Returns:** Count of successfully persisted feature rows

---

```java
public CalculatedFeatures calculateFeatures(List<OhlcvCandleEntity> candles)
```
**Returns:** CalculatedFeatures object or null if insufficient data

---

```java
public void persistFeatures(
    Long symbolId,
    Instant recordedAt,
    CalculatedFeatures features
)
```
**Purpose:** Directly persist features with upsert semantics

---

```java
public List<FeaturesEntity> getLatestFeatures(Long symbolId, int limit)
public List<FeaturesEntity> getFeaturesInRange(Long symbolId, Instant fromTime, Instant toTime)
```
**Purpose:** Retrieve persisted features

### FeatureEngineeringUtil

All methods are static and can be used independently:

```java
// EMA Calculations
Double calculateEma(List<Double> prices, int period)
Double calculateEmaIncremental(List<Double> prices, int period, Double previousEma)

// RSI Calculations
Double calculateRsi(List<Double> prices, int period)
RsiResult calculateRsiIncremental(List<Double> newPrices, int period, ...)

// ATR Calculations
Double calculateAtr(List<OhlcvCandleEntity> candles, int period)
Double calculateAtrFromPrices(List<Double> highs, List<Double> lows, 
                               List<Double> closes, int period)

// Volume MA
Double calculateVolumeMA(List<Double> volumes, int period)
Double calculateVolumeMaFromCandles(List<OhlcvCandleEntity> candles, int period)

// Validation
boolean isValidFeatureValue(Double value, double minBound, Double maxBound)
```

## Data Model

### FeaturesEntity

```java
@Entity
@Table(name = "features")
public class FeaturesEntity {
    Long id;                    // Primary key
    Long instrumentId;          // Foreign key to instrument
    Long signalId;             // Optional link to signal
    Instant recordedAt;        // Timestamp (partitioning key)
    Double ema20;              // 20-period EMA
    Double ema50;              // 50-period EMA
    Double ema200;             // 200-period EMA
    Double rsi;                // RSI (0-100)
    Double atr;                // ATR (volatility)
    Double volumeMa;           // Volume moving average
    Instant createdAt;         // Row creation time
}
```

### Database Schema

```sql
CREATE TABLE features (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    instrument_id BIGINT NOT NULL,
    signal_id BIGINT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    ema20 DOUBLE PRECISION NOT NULL,
    ema50 DOUBLE PRECISION NOT NULL,
    ema200 DOUBLE PRECISION NOT NULL,
    rsi DOUBLE PRECISION NOT NULL CHECK (rsi >= 0.0 AND rsi <= 100.0),
    atr DOUBLE PRECISION NOT NULL CHECK (atr >= 0.0),
    volume_ma DOUBLE PRECISION NOT NULL CHECK (volume_ma >= 0.0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_features PRIMARY KEY (id, recorded_at),
    CONSTRAINT uq_features_instrument_time UNIQUE (instrument_id, recorded_at)
) PARTITION BY RANGE (recorded_at);
```

## Testing

Comprehensive unit tests are provided:

### FeatureEngineeringUtilTest
- 16 tests covering all technical indicators
- Tests for edge cases (null inputs, insufficient data)
- Validation logic tests

### FeatureEngineeringServiceTest
- 10 tests for service orchestration
- Feature calculation from candles
- Validation of feature values

Run tests:
```bash
mvn test -Dtest=FeatureEngineeringUtilTest
mvn test -Dtest=FeatureEngineeringServiceTest
```

## Best Practices

### 1. Error Handling
Always check if features are valid before using:
```java
CalculatedFeatures features = service.calculateFeatures(candles);
if (features != null && features.isValid()) {
    // Use features
}
```

### 2. Batch Operations
For processing multiple symbols/timeframes, use batch method:
```java
// Good: Single transaction
featureService.calculateAndPersistFeaturesForAll(symbols, timeframes, 250);

// Avoid: Repeated individual calls
for (String symbol : symbols) {
    featureService.calculateAndPersistFeatures(...);
}
```

### 3. Minimum Candle Requirement
Ensure sufficient candles for all features:
```java
// Minimum 200 for EMA200 + RSI + ATR + Volume MA
featureService.calculateAndPersistFeatures(id, symbol, tf, 250);
```

### 4. Incremental Updates
Call once per new candle for efficiency:
```java
// Good: Called once per closed candle
@Scheduled(fixedRate = 60000)
void processLatestCandles() {
    // Process all symbols/timeframes
}
```

## Integration Example

```java
@Component
public class FeatureEngineeringScheduler {
    
    private final FeatureEngineeringService featureService;
    
    @Scheduled(fixedRate = 60000)  // Every minute
    public void calculateDailyFeatures() {
        List<String> symbols = Arrays.asList("BTCUSDT", "ETHUSDT");
        List<String> timeframes = Arrays.asList("M5", "M15", "H1");
        
        try {
            int count = featureService.calculateAndPersistFeaturesForAll(
                symbols, 
                timeframes, 
                250
            );
            LOGGER.info("Calculated features for {} timestamp(s)", count);
        } catch (Exception ex) {
            LOGGER.error("Feature engineering failed", ex);
        }
    }
}
```

## Performance Characteristics

| Operation | Time | Space | Notes |
|-----------|------|-------|-------|
| Calculate EMA | O(N) | O(1) | Single pass |
| Calculate RSI | O(N) | O(1) | Uses running averages |
| Calculate ATR | O(N) | O(1) | Wilder's smoothing |
| Persist Features | O(1) | O(1) | Upsert via native SQL |
| Query Latest | O(log N) | O(K) | K = limit parameter |

## Troubleshooting

### Issue: All features are null
**Cause:** Insufficient candle data  
**Solution:** Ensure minimum 200 candles available before calling

### Issue: Features differ from expected values
**Cause:** Different calculation method (e.g., SMA vs EMA)  
**Solution:** Review TA-Lib standard formulas used in implementation

### Issue: Performance degradation
**Cause:** Fetching too many candles per calculation  
**Solution:** Keep minimum required candles, rely on incremental updates

### Issue: Late-arriving data not persisted
**Cause:** Same timestamp already exists  
**Solution:** Upsert is working correctly - use `findLatestBefore()` to verify

## Future Enhancements

1. **Bollinger Bands** - Upper/Lower bands with period-20 SMA
2. **MACD** - Momentum indicator
3. **Stochastic Oscillator** - %K and %D lines
4. **Volume Profile** - Volume distribution by price level
5. **Incremental RSI** - More efficient RSI updates using previous averages

## References

- TA-Lib Standard: https://github.com/mrjbq7/ta-lib
- Trading Systems Design: https://www.investopedia.com/
- PostgreSQL Partitioning: https://www.postgresql.org/docs/current/ddl-partitioning.html

