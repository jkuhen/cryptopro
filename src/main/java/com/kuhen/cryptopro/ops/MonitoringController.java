package com.kuhen.cryptopro.ops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {

    private final OpsTelemetryService opsTelemetryService;

    public MonitoringController(OpsTelemetryService opsTelemetryService) {
        this.opsTelemetryService = opsTelemetryService;
    }

    @GetMapping("/transactions")
    public List<TransactionLogEntry> transactions(@RequestParam(defaultValue = "50") int limit) {
        return opsTelemetryService.recentTransactions(Math.max(1, Math.min(limit, 200)));
    }

    @GetMapping("/errors")
    public List<ErrorLogEntry> errors(@RequestParam(defaultValue = "50") int limit) {
        return opsTelemetryService.recentErrors(Math.max(1, Math.min(limit, 200)));
    }
}

