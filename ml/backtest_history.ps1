param(
    [string]$InputDir = "data",
    [string]$Pattern = "Binance_*USDT_d.csv",
    [string]$OutputDir = "ml/reports/history_backtest",
    [double]$InitialCapital = 10000.0,
    [double]$FeeBps = 5.0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-Rsi {
    param(
        [double[]]$Close,
        [int]$Period = 14
    )

    $n = $Close.Length
    $rsi = New-Object double[] $n
    for ($i = 0; $i -lt $n; $i++) { $rsi[$i] = 50.0 }
    if ($n -le $Period) { return $rsi }

    $gain = 0.0
    $loss = 0.0
    for ($i = 1; $i -le $Period; $i++) {
        $delta = $Close[$i] - $Close[$i - 1]
        if ($delta -gt 0) { $gain += $delta } else { $loss += -$delta }
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
        $up = if ($delta -gt 0) { $delta } else { 0.0 }
        $down = if ($delta -lt 0) { -$delta } else { 0.0 }

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

function Get-Metrics {
    param(
        [string]$Symbol,
        [string]$Strategy,
        [double[]]$Returns,
        [double]$StartEquity
    )

    $n = $Returns.Length
    $equity = New-Object double[] $n
    $equity[0] = $StartEquity * (1.0 + $Returns[0])
    for ($i = 1; $i -lt $n; $i++) {
        $equity[$i] = $equity[$i - 1] * (1.0 + $Returns[$i])
    }

    $sum = 0.0
    $wins = 0
    for ($i = 0; $i -lt $n; $i++) {
        $sum += $Returns[$i]
        if ($Returns[$i] -gt 0.0) { $wins++ }
    }
    $mean = $sum / [Math]::Max(1, $n)

    $var = 0.0
    for ($i = 0; $i -lt $n; $i++) {
        $d = $Returns[$i] - $mean
        $var += ($d * $d)
    }
    $volDaily = [Math]::Sqrt($var / [Math]::Max(1, $n))

    $annVol = $volDaily * [Math]::Sqrt(365.0)
    $years = $n / 365.0
    $finalEquity = $equity[$n - 1]
    $cagr = if ($years -gt 0.0 -and $finalEquity -gt 0.0) { [Math]::Pow($finalEquity / $StartEquity, 1.0 / $years) - 1.0 } else { 0.0 }
    $sharpe = if ($annVol -gt 0.0) { $cagr / $annVol } else { 0.0 }

    $peak = $equity[0]
    $maxDd = 0.0
    for ($i = 0; $i -lt $n; $i++) {
        if ($equity[$i] -gt $peak) { $peak = $equity[$i] }
        $dd = ($equity[$i] / $peak) - 1.0
        if ($dd -lt $maxDd) { $maxDd = $dd }
    }

    [pscustomobject]@{
        symbol = $Symbol
        strategy = $Strategy
        total_return = ($finalEquity / $StartEquity) - 1.0
        cagr = $cagr
        annualized_volatility = $annVol
        sharpe = $sharpe
        max_drawdown = $maxDd
        positive_day_ratio = $wins / [Math]::Max(1, $n)
    }
}

function Get-Sma {
    param(
        [double[]]$Close,
        [int]$Window
    )

    $n = $Close.Length
    $sma = New-Object double[] $n
    $sum = 0.0
    for ($i = 0; $i -lt $n; $i++) {
        $sum += $Close[$i]
        if ($i -ge $Window) { $sum -= $Close[$i - $Window] }
        if ($i -ge ($Window - 1)) {
            $sma[$i] = $sum / $Window
        } else {
            $sma[$i] = [double]::NaN
        }
    }
    return $sma
}

function Get-Equity {
    param(
        [double[]]$Returns,
        [double]$StartEquity
    )

    $n = $Returns.Length
    $equity = New-Object double[] $n
    $equity[0] = $StartEquity * (1.0 + $Returns[0])
    for ($i = 1; $i -lt $n; $i++) {
        $equity[$i] = $equity[$i - 1] * (1.0 + $Returns[$i])
    }
    return $equity
}

$inputPath = Join-Path (Get-Location) $InputDir
$files = Get-ChildItem -Path $inputPath -Filter $Pattern | Sort-Object Name
if (-not $files) {
    throw "No files found in $inputPath matching $Pattern"
}

$feeRate = $FeeBps / 10000.0
$metrics = New-Object System.Collections.Generic.List[object]
$equityRows = New-Object System.Collections.Generic.List[object]
$outputPath = Join-Path (Get-Location) $OutputDir
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null

foreach ($file in $files) {
    $rows = (Get-Content -Path $file.FullName | Select-Object -Skip 1) | ConvertFrom-Csv
    $rows = $rows | Sort-Object Date

    $symbol = ($rows[0].Symbol)
    $n = $rows.Count

    $close = New-Object double[] $n
    for ($i = 0; $i -lt $n; $i++) {
        $close[$i] = [double]$rows[$i].Close
    }

    $ret = New-Object double[] $n
    $ret[0] = 0.0
    for ($i = 1; $i -lt $n; $i++) {
        $ret[$i] = ($close[$i] / $close[$i - 1]) - 1.0
    }

    $sma20 = Get-Sma -Close $close -Window 20
    $sma50 = Get-Sma -Close $close -Window 50
    $rsi = Get-Rsi -Close $close -Period 14

    $trendPos = New-Object double[] $n
    $mrPos = New-Object double[] $n
    $mrState = 0.0

    for ($i = 0; $i -lt $n; $i++) {
        if ($i -gt 0 -and -not [double]::IsNaN($sma20[$i - 1]) -and -not [double]::IsNaN($sma50[$i - 1])) {
            $trendPos[$i] = if ($sma20[$i - 1] -gt $sma50[$i - 1]) { 1.0 } else { 0.0 }
        } else {
            $trendPos[$i] = 0.0
        }

        if ($i -gt 0) {
            if ($rsi[$i - 1] -lt 30.0) { $mrState = 1.0 }
            if ($rsi[$i - 1] -gt 55.0) { $mrState = 0.0 }
        }
        $mrPos[$i] = $mrState
    }

    $bhRet = New-Object double[] $n
    $trRet = New-Object double[] $n
    $mrRet = New-Object double[] $n

    for ($i = 0; $i -lt $n; $i++) {
        $bhRet[$i] = $ret[$i]

        $trTurnover = if ($i -eq 0) { [Math]::Abs($trendPos[$i]) } else { [Math]::Abs($trendPos[$i] - $trendPos[$i - 1]) }
        $mrTurnover = if ($i -eq 0) { [Math]::Abs($mrPos[$i]) } else { [Math]::Abs($mrPos[$i] - $mrPos[$i - 1]) }

        $trRet[$i] = ($trendPos[$i] * $ret[$i]) - ($trTurnover * $feeRate)
        $mrRet[$i] = ($mrPos[$i] * $ret[$i]) - ($mrTurnover * $feeRate)
    }

    $metrics.Add((Get-Metrics -Symbol $symbol -Strategy "buy_and_hold" -Returns $bhRet -StartEquity $InitialCapital))
    $metrics.Add((Get-Metrics -Symbol $symbol -Strategy "sma20_50" -Returns $trRet -StartEquity $InitialCapital))
    $metrics.Add((Get-Metrics -Symbol $symbol -Strategy "rsi_mean_reversion" -Returns $mrRet -StartEquity $InitialCapital))

    $eqBh = Get-Equity -Returns $bhRet -StartEquity $InitialCapital
    $eqTr = Get-Equity -Returns $trRet -StartEquity $InitialCapital
    $eqMr = Get-Equity -Returns $mrRet -StartEquity $InitialCapital

    for ($i = 0; $i -lt $n; $i++) {
        $equityRows.Add([pscustomobject]@{
            Date = $rows[$i].Date
            symbol = $symbol
            buy_and_hold = $eqBh[$i]
            sma20_50 = $eqTr[$i]
            rsi_mean_reversion = $eqMr[$i]
        })
    }
}

$metricsPath = Join-Path $outputPath "backtest_metrics_by_symbol.csv"
$summaryPath = Join-Path $outputPath "backtest_summary.csv"
$jsonPath = Join-Path $outputPath "backtest_summary.json"
$equityPath = Join-Path $outputPath "equity_curves.csv"

$metrics | Export-Csv -Path $metricsPath -NoTypeInformation
$equityRows | Export-Csv -Path $equityPath -NoTypeInformation

$summary = $metrics |
    Group-Object strategy |
    ForEach-Object {
        $group = $_.Group
        [pscustomobject]@{
            strategy = $_.Name
            symbols = $group.Count
            avg_total_return = ($group | Measure-Object total_return -Average).Average
            avg_cagr = ($group | Measure-Object cagr -Average).Average
            avg_sharpe = ($group | Measure-Object sharpe -Average).Average
            avg_max_drawdown = ($group | Measure-Object max_drawdown -Average).Average
        }
    } |
    Sort-Object avg_sharpe -Descending

$summary | Export-Csv -Path $summaryPath -NoTypeInformation
$summary | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonPath -Encoding UTF8

"Processed files:"
$files | ForEach-Object { "- $($_.Name)" }
""
"Backtest summary:"
$summary | Format-Table -AutoSize | Out-String
""
"Generated:"
"- $metricsPath"
"- $summaryPath"
"- $jsonPath"
"- $equityPath"

