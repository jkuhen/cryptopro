package com.kuhen.cryptopro.ai;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retraining")
public class RetrainingController {

    private final ContinuousRetrainingService continuousRetrainingService;

    public RetrainingController(ContinuousRetrainingService continuousRetrainingService) {
        this.continuousRetrainingService = continuousRetrainingService;
    }

    @GetMapping("/status")
    public RetrainingStatus status() {
        return continuousRetrainingService.status();
    }

    @PostMapping("/run")
    public RetrainingStatus run() {
        return continuousRetrainingService.runNow();
    }
}

