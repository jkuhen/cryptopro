# Open Signal Card - Entry, SL, TP Display Fix

## Problem
The Open Signal card on the dashboard was not displaying entry price, stop loss (SL), and take profit (TP) values. All these fields were showing as empty or null.

## Root Cause
The issue was in the signal oversight data retrieval logic:

1. **SignalTelemetryService.java** (lines 157-159): Explicitly setting entry price, stop loss, and take profit to `null` with comments stating "not tracked in telemetry"
2. **SignalOversightService.java** (lines 186-188): Also explicitly setting these values to `null` with comments stating "not stored in signals table"

The problem: While signals themselves don't store execution prices, the **linked trades DO** contain this data. The code was not joining the trades table to retrieve the execution details.

## Solution
Modified `SignalOversightService.java` to:

1. **Enhanced SQL Query** - Added LEFT JOIN with the trades table to fetch execution data:
   ```sql
   LEFT JOIN trades t ON t.signal_id = s.id 
   AND t.status IN ('OPEN', 'CLOSED')
   ```

2. **Updated Column Selection** - Extended the SELECT clause to include:
   - `t.entry_price` (column index 7)
   - `t.stop_loss` (column index 8)
   - `t.take_profit` (column index 9)

3. **New Classification Method** - Created `classifyWithTradeData()` to populate trade execution data:
   - Takes entry price, stop loss, and take profit as parameters
   - Populates these values in `OpenSignalView` and `MissedTradeView` records

4. **Backward Compatibility** - Kept the original `classify()` method for:
   - Telemetry-only entries (in-memory signals without DB trade records)
   - Fallback scenarios where trade data is unavailable

## Changes Made

### File: `SignalOversightService.java`

**1. Modified queryDb() method:**
- Changed SQL from selecting 7 columns to 10 columns (added trade data)
- Used LEFT JOIN to handle signals without associated trades
- Filters traded status as OPEN or CLOSED (excludes REJECTED/CANCELLED trades)

**2. Updated buildOversightReport() method:**
- Extended row data extraction to handle 10 columns instead of 7
- Added null-safe checks for trade data columns
- Calls new `classifyWithTradeData()` method for DB rows with trade information

**3. Added classifyWithTradeData() method:**
- New private method accepting trade execution parameters
- Populates entry price, SL, and TP in the response objects
- Mirrors the original `classify()` method but includes trade data

## Impact

### What Gets Fixed
- ✅ Open Signals card now shows entry prices for executed trades
- ✅ Stop loss values are populated from the trades table
- ✅ Take profit values are populated from the trades table
- ✅ Both dashboard summary and detailed transaction reports display correctly

### What Remains Unchanged
- ✅ Telemetry-only signals (in-memory entries) still have null values (acceptable since signals precede execution)
- ✅ Backward compatibility maintained with existing code structure
- ✅ No database schema changes required (uses existing signal_id foreign key)

## Testing Recommendations

1. **Test with Executed Signals:**
   - Verify signals with linked trades show entry, SL, TP values
   - Check both open and closed trades are displayed

2. **Test with Unexecuted Signals:**
   - Verify recent signals without trades still display correctly (with null values)
   - Ensure UI handles null values gracefully

3. **Test Dashboard Pages:**
   - Dashboard > Open Signals card (symbol-filtered view)
   - Reports > Transaction Reports > Open Signals section
   - Transaction Reports > Missed Trades section (also uses entry price field)

4. **Performance Check:**
   - Monitor query performance with LEFT JOIN
   - Verify indexes on signal_id, status columns are being used

## Database Indexes
Ensure these indexes exist for optimal performance:
```sql
CREATE INDEX IF NOT EXISTS idx_trades_signal_id ON trades(signal_id);
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
```

## Notes
- The LEFT JOIN approach ensures signals without trades won't be filtered out (they'll just have NULL trade values)
- Trade status filtering to 'OPEN', 'CLOSED' prevents showing data from invalid/cancelled trade records
- In-memory telemetry entries (from SignalTelemetryService) continue using the original `classify()` method, maintaining separation of concerns

