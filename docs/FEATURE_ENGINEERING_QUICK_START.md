# Feature Engineering Module - Quick Start Guide

## 📦 What Was Delivered

A complete, production-ready feature engineering module for calculating technical analysis indicators from cryptocurrency market data.

## 🚀 Quick Start

### 1. Enable the Scheduler

Uncomment the `@Component` annotation in example usage:

```java
// File: src/main/java/com/kuhen/cryptopro/data/FeatureEngineeringExampleUsage.java
@Component  // ← Uncomment this
class FeatureEngineeringExampleScheduler { ... }
```

This will automatically calculate features every minute for all monitored symbols.

### 2. Basic Usage

```java
@Service
public class YourService {
    
    private final FeatureEngineeringService featureService;
    
    public void calculateFeatures() {
        // Single symbol/timeframe
        boolean success = featureService.calculateAndPersistFeatures(
            symbolId,           // Long - instrument ID
            "BTCUSDT",         // String - symbol name
            "H1",              // String - timeframe
            250                // int - minimum candles required
        );
    }
    
    public void calculateAll() {
        // Batch process
        int count = featureService.calculateAndPersistFeaturesForAll(
            Arrays.asList("BTCUSDT", "ETHUSDT"),
            Arrays.asList("M15", "H1"),
            250
        );
    }
}
```

### 3. Retrieve Features

```java
// Get latest 100 feature rows
List<FeaturesEntity> features = featureService.getLatestFeatures(symbolId, 100);

// Get features for a time range
List<FeaturesEntity> ranged = featureService.getFeaturesInRange(
    symbolId, 
    fromTime, 
    toTime
);
```

## 📊 Calculated Indicators

| Indicator | Period | Formula | Range |
|-----------|--------|---------|-------|
| **EMA20** | 20 | Exponential Moving Average | Price level |
| **EMA50** | 50 | Exponential Moving Average | Price level |
| **EMA200** | 200 | Exponential Moving Average | Price level |
| **RSI** | 14 | Relative Strength Index | 0-100 |
| **ATR** | 14 | Average True Range | 0-∞ |
| **Volume MA** | 20 | Volume Moving Average | 0-∞ |

## 📁 File Structure

```
cryptopro/
├── src/main/java/com/kuhen/cryptopro/data/
│   ├── FeatureEngineeringService.java          ← Main service (use this)
│   ├── FeatureEngineeringUtil.java             ← Calculations (advanced)
│   ├── FeatureEngineeringExampleUsage.java     ← Examples (reference)
│   ├── entity/
│   │   └── FeaturesEntity.java                 ← Database model
│   └── repository/
│       └── FeaturesRepository.java             ← Database access
│
├── src/test/java/com/kuhen/cryptopro/data/
│   ├── FeatureEngineeringUtilTest.java         ← 16 unit tests
│   └── FeatureEngineeringServiceTest.java      ← 10 unit tests
│
└── Documentation/
    ├── FEATURE_ENGINEERING_README.md           ← Detailed reference
    ├── FEATURE_ENGINEERING_DELIVERY_SUMMARY.md ← Implementation summary
    └── FEATURE_ENGINEERING_QUICK_START.md      ← This file
```

## 🔧 Technical Details

### Efficiency Features

✅ **No Full Recomputation**
- Only processes latest closed candle
- Reuses historical data from database
- Safe to call every minute

✅ **Incremental Updates**
- EMA calculations support incremental mode
- RSI can use previous averages
- Minimal CPU overhead

✅ **Database Optimized**
- Upsert semantics (INSERT... ON CONFLICT)
- Monthly table partitioning
- Efficient indexes on (instrument_id, recorded_at)

### Minimum Data Requirements

Must have at least **200 candles** for full feature set:
- EMA200 needs 200 candles
- RSI, ATR need ~15 candles
- Safety margin built in

## 🧪 Testing

All tests are included and passing:

```bash
# Run feature engineering tests only
mvn test -Dtest=FeatureEngineeringUtilTest,FeatureEngineeringServiceTest

# Result: BUILD SUCCESS ✅
# Tests: 26 passed, 0 failed
```

## 📚 Documentation

Three documentation files are provided:

1. **FEATURE_ENGINEERING_README.md** (Comprehensive)
   - Architecture overview
   - Formula explanations
   - API reference
   - Integration patterns

2. **FEATURE_ENGINEERING_DELIVERY_SUMMARY.md** (Implementation)
   - What was built
   - Technical specs
   - File inventory
   - Quality metrics

3. **FEATURE_ENGINEERING_QUICK_START.md** (This file)
   - Quick reference
   - Copy-paste examples
   - Common tasks

## 🔌 Integration Examples

### Example 1: Scheduled Processing (Recommended)

```java
@Component
public class FeatureCalculationScheduler {
    
    private final FeatureEngineeringService featureService;
    
    @Scheduled(fixedRate = 60_000)  // Every minute
    public void calculateFeatures() {
        featureService.calculateAndPersistFeaturesForAll(
            Arrays.asList("BTCUSDT", "ETHUSDT", "SOLUSDT"),
            Arrays.asList("M5", "M15", "H1"),
            250
        );
    }
}
```

### Example 2: REST Endpoint

```java
@RestController
@RequestMapping("/api/features")
public class FeatureController {
    
    private final FeatureEngineeringService featureService;
    
    @GetMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(
            @RequestParam String symbol,
            @RequestParam String timeframe) {
        
        Long instrumentId = getInstrumentId(symbol);
        boolean success = featureService.calculateAndPersistFeatures(
            instrumentId, symbol, timeframe, 250
        );
        
        return ResponseEntity.ok(Map.of(
            "symbol", symbol,
            "timeframe", timeframe,
            "success", success
        ));
    }
}
```

### Example 3: Trading Signal Generation

```java
@Service
public class SignalGenerator {
    
    private final FeatureEngineeringService featureService;
    
    public List<Signal> generateSignals(Long instrumentId) {
        List<FeaturesEntity> features = featureService.getLatestFeatures(
            instrumentId, 10
        );
        
        List<Signal> signals = new ArrayList<>();
        
        for (FeaturesEntity feature : features) {
            // Example: Buy when RSI < 30 and EMA20 > EMA50
            if (feature.getRsi() < 30 
                && feature.getEma20() > feature.getEma50()) {
                signals.add(new BuySignal(instrumentId, feature.getRecordedAt()));
            }
            
            // Example: Sell when RSI > 70
            if (feature.getRsi() > 70) {
                signals.add(new SellSignal(instrumentId, feature.getRecordedAt()));
            }
        }
        
        return signals;
    }
}
```

## ⚠️ Common Issues & Solutions

### Issue: All features are null
**Cause:** Not enough candle data (< 200)  
**Solution:** Wait for more candles to be collected or use smaller timeframe

### Issue: Calculation takes too long
**Cause:** Fetching too many candles per call  
**Solution:** Keep minimum at 250, let incremental updates handle rest

### Issue: Features don't match expected values
**Cause:** Using different calculation method  
**Solution:** Review TA-Lib standard formulas (they are correctly implemented)

## 🎯 Next Steps

1. **Deploy Module**: Module is production-ready, add to your pipeline
2. **Schedule Execution**: Enable the scheduler or call from your existing pipeline
3. **Integrate Signals**: Use features in your signal generation logic
4. **Monitor**: Track feature values and validate calculations

## 📞 API Summary

### FeatureEngineeringService (Main Interface)

```java
// Calculate and persist features for latest candle
boolean calculateAndPersistFeatures(
    Long symbolId, String symbol, String timeframe, int minCandlesRequired
)

// Batch calculate for multiple symbols/timeframes
int calculateAndPersistFeaturesForAll(
    List<String> symbols, List<String> timeframes, int minCandlesRequired
)

// Get calculated features
List<FeaturesEntity> getLatestFeatures(Long symbolId, int limit)
List<FeaturesEntity> getFeaturesInRange(Long symbolId, Instant fromTime, Instant toTime)

// Low-level interface
CalculatedFeatures calculateFeatures(List<OhlcvCandleEntity> candles)
void persistFeatures(Long symbolId, Instant recordedAt, CalculatedFeatures features)
```

### FeatureEngineeringUtil (Calculations)

All methods are static and can be used independently:

```java
// EMA
Double calculateEma(List<Double> prices, int period)
Double calculateEmaIncremental(List<Double> prices, int period, Double previousEma)

// RSI
Double calculateRsi(List<Double> prices, int period)
RsiResult calculateRsiIncremental(List<Double> newPrices, int period, ...)

// ATR
Double calculateAtrFromPrices(List<Double> highs, List<Double> lows, 
                               List<Double> closes, int period)

// Volume
Double calculateVolumeMA(List<Double> volumes, int period)
Double calculateVolumeMaFromCandles(List<OhlcvCandleEntity> candles, int period)

// Validation
boolean isValidFeatureValue(Double value, double minBound, Double maxBound)
```

## 🎉 Summary

You now have a **complete, tested, production-ready feature engineering module** that:

✅ Calculates technical indicators (EMA, RSI, ATR, Volume MA)  
✅ Handles incremental updates efficiently  
✅ Persists to database with upsert semantics  
✅ Includes 26 passing unit tests  
✅ Works with existing Cryptopro database schema  
✅ Follows Spring Boot best practices  

**Ready to integrate into your trading pipeline!**

