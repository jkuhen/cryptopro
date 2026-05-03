param(
    [string]$InputDir = "data",
    [string]$Pattern = "Binance_*USDT_d.csv",
    [string]$BacktestReportDir = "ml/reports/history_backtest",
    [string]$OutputRoot = "ml/artifacts/history",
    [string]$Version = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-Sma {
    param(
        [double[]]$Series,
        [int]$Window
    )

    $n = $Series.Length
    $result = New-Object double[] $n
    $sum = 0.0

    for ($i = 0; $i -lt $n; $i++) {
        $sum += $Series[$i]
        if ($i -ge $Window) {
            $sum -= $Series[$i - $Window]
        }

        if ($i -ge ($Window - 1)) {
            $result[$i] = $sum / $Window
        } else {
            $result[$i] = [double]::NaN
        }
    }

    return $result
}

function Get-RollingStd {
    param(
        [double[]]$Series,
        [int]$Window
    )

    $n = $Series.Length
    $result = New-Object double[] $n

    for ($i = 0; $i -lt $n; $i++) {
        if ($i -lt ($Window - 1)) {
            $result[$i] = [double]::NaN
            continue
        }

        $start = $i - $Window + 1
        $sum = 0.0
        for ($j = $start; $j -le $i; $j++) {
            $sum += $Series[$j]
        }

        $mean = $sum / $Window
        $var = 0.0
        for ($j = $start; $j -le $i; $j++) {
            $d = $Series[$j] - $mean
            $var += ($d * $d)
        }

        $result[$i] = [Math]::Sqrt($var / $Window)
    }

    return $result
}

function Get-Rsi {
    param(
        [double[]]$Close,
        [int]$Period = 14
    )

    $n = $Close.Length
    $rsi = New-Object double[] $n
    for ($i = 0; $i -lt $n; $i++) { $rsi[$i] = 50.0 }

    if ($n -le $Period) {
        return $rsi
    }

    $gain = 0.0
    $loss = 0.0
    for ($i = 1; $i -le $Period; $i++) {
        $delta = $Close[$i] - $Close[$i - 1]
        if ($delta -gt 0.0) {
            $gain += $delta
        } else {
            $loss += -$delta
        }
    }

    $avgGain = $gain / $Period
    $avgLoss = $loss / $Period

    if ($avgLoss -eq 0.0) {
        $rsi[$Period] = 100.0
    } else {
        $rs = $avgGain / $avgLoss
        $rsi[$Period] = 100.0 - (100.0 / (1.0 + $rs))
    }

    for ($i = $Period + 1; $i -lt $n; $i++) {
        $delta = $Close[$i] - $Close[$i - 1]
        $up = if ($delta -gt 0.0) { $delta } else { 0.0 }
        $down = if ($delta -lt 0.0) { -$delta } else { 0.0 }

        $avgGain = (($avgGain * ($Period - 1)) + $up) / $Period
        $avgLoss = (($avgLoss * ($Period - 1)) + $down) / $Period

        if ($avgLoss -eq 0.0) {
            $rsi[$i] = 100.0
        } else {
            $rs = $avgGain / $avgLoss
            $rsi[$i] = 100.0 - (100.0 / (1.0 + $rs))
        }
    }

    return $rsi
}

function To-DoubleOrZero {
    param([object]$Value)

    if ($null -eq $Value) { return 0.0 }
    $s = [string]$Value
    if ([string]::IsNullOrWhiteSpace($s)) { return 0.0 }
    return [double]$s
}

if ([string]::IsNullOrWhiteSpace($Version)) {
    $Version = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmss")
}

$root = Get-Location
$inputPath = Join-Path $root $InputDir
$backtestPath = Join-Path $root $BacktestReportDir
$outputRootPath = Join-Path $root $OutputRoot
$versionPath = Join-Path $outputRootPath $Version
$analysisPath = Join-Path $versionPath "analysis"

$files = Get-ChildItem -Path $inputPath -Filter $Pattern | Sort-Object Name
if (-not $files) {
    throw "No history files found in '$inputPath' with pattern '$Pattern'."
}

New-Item -ItemType Directory -Path $versionPath -Force | Out-Null
New-Item -ItemType Directory -Path $analysisPath -Force | Out-Null

$candleRows = New-Object System.Collections.Generic.List[object]
$featureRows = New-Object System.Collections.Generic.List[object]
$symbols = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
    $rows = (Get-Content -Path $file.FullName | Select-Object -Skip 1) | ConvertFrom-Csv
    $rows = $rows | Sort-Object Date

    if (-not $rows -or $rows.Count -eq 0) {
        continue
    }

    $symbol = [string]$rows[0].Symbol
    if (-not $symbols.Contains($symbol)) {
        $symbols.Add($symbol)
    }

    $baseVolumeColumn = $null
    $rowProps = $rows[0].PSObject.Properties.Name
    foreach ($prop in $rowProps) {
        if ($prop -like "Volume *" -and $prop -ne "Volume USDT") {
            $baseVolumeColumn = $prop
            break
        }
    }
    if ($null -eq $baseVolumeColumn) {
        $baseVolumeColumn = "Volume USDT"
    }

    $n = $rows.Count
    $close = New-Object double[] $n
    $volumeQuote = New-Object double[] $n

    for ($i = 0; $i -lt $n; $i++) {
        $close[$i] = To-DoubleOrZero $rows[$i].Close
        $volumeQuote[$i] = To-DoubleOrZero $rows[$i]."Volume USDT"
    }

    $ret1 = New-Object double[] $n
    $ret3 = New-Object double[] $n
    $ret7 = New-Object double[] $n
    $futureRet1 = New-Object double[] $n

    for ($i = 0; $i -lt $n; $i++) {
        if ($i -gt 0 -and $close[$i - 1] -ne 0.0) {
            $ret1[$i] = ($close[$i] / $close[$i - 1]) - 1.0
        } else {
            $ret1[$i] = 0.0
        }

        if ($i -gt 2 -and $close[$i - 3] -ne 0.0) {
            $ret3[$i] = ($close[$i] / $close[$i - 3]) - 1.0
        } else {
            $ret3[$i] = 0.0
        }

        if ($i -gt 6 -and $close[$i - 7] -ne 0.0) {
            $ret7[$i] = ($close[$i] / $close[$i - 7]) - 1.0
        } else {
            $ret7[$i] = 0.0
        }

        if ($i -lt ($n - 1) -and $close[$i] -ne 0.0) {
            $futureRet1[$i] = ($close[$i + 1] / $close[$i]) - 1.0
        } else {
            $futureRet1[$i] = 0.0
        }
    }

    $sma20 = Get-Sma -Series $close -Window 20
    $sma50 = Get-Sma -Series $close -Window 50
    $rsi14 = Get-Rsi -Close $close -Period 14
    $volStd14 = Get-RollingStd -Series $ret1 -Window 14
    $volSma20 = Get-Sma -Series $volumeQuote -Window 20
    $volStd20 = Get-RollingStd -Series $volumeQuote -Window 20

    for ($i = 0; $i -lt $n; $i++) {
        $date = [string]$rows[$i].Date
        $unixMs = [string]$rows[$i].Unix
        $open = To-DoubleOrZero $rows[$i].Open
        $high = To-DoubleOrZero $rows[$i].High
        $low = To-DoubleOrZero $rows[$i].Low
        $closePx = To-DoubleOrZero $rows[$i].Close
        $volumeBase = To-DoubleOrZero $rows[$i].$baseVolumeColumn
        $volumeQ = To-DoubleOrZero $rows[$i]."Volume USDT"
        $tradeCount = [long](To-DoubleOrZero $rows[$i].tradecount)

        $candleRows.Add([pscustomobject]@{
            symbol = $symbol
            date = $date
            unix_ms = $unixMs
            open = $open
            high = $high
            low = $low
            close = $closePx
            volume_base = $volumeBase
            volume_quote = $volumeQ
            trade_count = $tradeCount
            source_file = $file.Name
        })

        $s20 = $sma20[$i]
        $s50 = $sma50[$i]
        $volStd = $volStd14[$i]
        $vSma20 = $volSma20[$i]
        $vStd20 = $volStd20[$i]

        $closeToSma20 = if ([double]::IsNaN($s20) -or $s20 -eq 0.0) { 0.0 } else { ($closePx / $s20) - 1.0 }
        $closeToSma50 = if ([double]::IsNaN($s50) -or $s50 -eq 0.0) { 0.0 } else { ($closePx / $s50) - 1.0 }
        $volZ20 = if ([double]::IsNaN($vSma20) -or [double]::IsNaN($vStd20) -or $vStd20 -eq 0.0) { 0.0 } else { ($volumeQ - $vSma20) / $vStd20 }
        $labelUp = if ($i -lt ($n - 1) -and $futureRet1[$i] -gt 0.0) { 1 } else { 0 }
        $isTrainable = ($i -lt ($n - 1))

        $featureRows.Add([pscustomobject]@{
            symbol = $symbol
            date = $date
            close = $closePx
            ret_1d = $ret1[$i]
            ret_3d = $ret3[$i]
            ret_7d = $ret7[$i]
            volatility_14d = if ([double]::IsNaN($volStd)) { 0.0 } else { $volStd }
            rsi_14 = $rsi14[$i]
            close_to_sma20 = $closeToSma20
            close_to_sma50 = $closeToSma50
            volume_z20 = $volZ20
            future_ret_1d = $futureRet1[$i]
            label_up_1d = $labelUp
            is_trainable = $isTrainable
        })
    }
}

$candlesCsv = Join-Path $versionPath "history_candles_daily.csv"
$featuresCsv = Join-Path $versionPath "ai_features_daily.csv"
$datasetCard = Join-Path $versionPath "dataset_card.md"
$manifestPath = Join-Path $versionPath "manifest.json"
$latestFile = Join-Path $outputRootPath "LATEST.txt"

$candleRows | Export-Csv -Path $candlesCsv -NoTypeInformation
$featureRows | Export-Csv -Path $featuresCsv -NoTypeInformation

$copiedAnalysis = New-Object System.Collections.Generic.List[string]
$analysisFiles = @(
    "backtest_metrics_by_symbol.csv",
    "backtest_summary.csv",
    "backtest_summary.json",
    "equity_curves.csv"
)

foreach ($name in $analysisFiles) {
    $src = Join-Path $backtestPath $name
    if (Test-Path $src) {
        $dst = Join-Path $analysisPath $name
        Copy-Item -Path $src -Destination $dst -Force
        $copiedAnalysis.Add($name)
    }
}

$card = @(
    "# History Artifact Dataset Card",
    "",
    "- version: $Version",
    "- generated_utc: $((Get-Date).ToUniversalTime().ToString('o'))",
    "- symbols: $([string]::Join(', ', $symbols))",
    "- candle_rows: $($candleRows.Count)",
    "- feature_rows: $($featureRows.Count)",
    "- trainable_rows: $((@($featureRows | Where-Object { $_.is_trainable -eq $true })).Count)",
    "",
    "## Files",
    "",
    "- history_candles_daily.csv : normalized OHLCV daily candles",
    "- ai_features_daily.csv : engineered features + 1-day forward label",
    "- analysis/* : copied backtest outputs for auditability"
)
Set-Content -Path $datasetCard -Value $card -Encoding UTF8

$manifest = [pscustomobject]@{
    version = $Version
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    source = [pscustomobject]@{
        inputDir = $InputDir
        pattern = $Pattern
        files = @($files | ForEach-Object { $_.Name })
        symbols = @($symbols)
    }
    outputs = [pscustomobject]@{
        candlesCsv = [System.IO.Path]::GetFileName($candlesCsv)
        featuresCsv = [System.IO.Path]::GetFileName($featuresCsv)
        datasetCard = [System.IO.Path]::GetFileName($datasetCard)
        analysisDir = "analysis"
        copiedAnalysisFiles = @($copiedAnalysis)
    }
    stats = [pscustomobject]@{
        candleRows = $candleRows.Count
        featureRows = $featureRows.Count
        trainableRows = (@($featureRows | Where-Object { $_.is_trainable -eq $true })).Count
    }
}

$manifest | ConvertTo-Json -Depth 8 | Set-Content -Path $manifestPath -Encoding UTF8
Set-Content -Path $latestFile -Value $Version -Encoding UTF8

Write-Output "Persisted history artifacts successfully."
Write-Output "- version: $Version"
Write-Output "- artifact_dir: $versionPath"
Write-Output "- candles: $candlesCsv"
Write-Output "- features: $featuresCsv"
Write-Output "- manifest: $manifestPath"
Write-Output "- dataset_card: $datasetCard"
if ($copiedAnalysis.Count -gt 0) {
    Write-Output "- copied_analysis: $($copiedAnalysis -join ', ')"
} else {
    Write-Output "- copied_analysis: none (run backtest first if needed)"
}
