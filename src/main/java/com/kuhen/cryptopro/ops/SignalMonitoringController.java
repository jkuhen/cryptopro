package com.kuhen.cryptopro.ops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/signals")
public class SignalMonitoringController {

    private final SignalTelemetryService signalTelemetryService;

    public SignalMonitoringController(SignalTelemetryService signalTelemetryService) {
        this.signalTelemetryService = signalTelemetryService;
    }

    @GetMapping("/summary")
    public SignalSummaryResponse summary(@RequestParam(defaultValue = "50") int limit) {
        return signalTelemetryService.buildSummary(Math.max(1, Math.min(limit, 500)));
    }

    @GetMapping("/regime/latest")
    public LatestRegimeResponse latestRegime(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        return signalTelemetryService.latestRegimeForSymbol(symbol);
    }
}

