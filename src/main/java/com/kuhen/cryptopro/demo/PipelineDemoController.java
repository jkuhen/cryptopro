package com.kuhen.cryptopro.demo;

import com.kuhen.cryptopro.ops.OpsTelemetryService;
import com.kuhen.cryptopro.ops.PaperPortfolioService;
import com.kuhen.cryptopro.ops.TransactionLogEntry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/pipeline")
public class PipelineDemoController {

    private final PipelineDemoService pipelineDemoService;
    private final OpsTelemetryService opsTelemetryService;
    private final PaperPortfolioService paperPortfolioService;

    public PipelineDemoController(
            PipelineDemoService pipelineDemoService,
            OpsTelemetryService opsTelemetryService,
            PaperPortfolioService paperPortfolioService
    ) {
        this.pipelineDemoService = pipelineDemoService;
        this.opsTelemetryService = opsTelemetryService;
        this.paperPortfolioService = paperPortfolioService;
    }

    @GetMapping("/demo")
    public PipelineDemoResponse runDemo(@RequestParam(defaultValue = "BTCUSDT") String symbol) {
        PipelineDemoResponse response = pipelineDemoService.run(symbol.toUpperCase());
        opsTelemetryService.recordTransaction(new TransactionLogEntry(
                Instant.now(),
                "PIPELINE_DEMO",
                response.symbol(),
                response.direction().name(),
                response.executionStatus().name(),
                response.executionRequestedQuantity(),
                response.executionFilledQuantity(),
                response.executionSlippageBps(),
                response.executionNotes(),
                "PAPER_DEMO",
                "paper",
                response.executionAverageFillPrice() > 0.0 ? response.executionAverageFillPrice() : null
        ));
        paperPortfolioService.applyExecution(
                response.symbol(),
                response.direction(),
                new com.kuhen.cryptopro.execution.ExecutionResult(
                        response.executionStatus(),
                        response.executionRequestedQuantity(),
                        response.executionFilledQuantity(),
                        response.executionLimitPrice(),
                        response.executionAverageFillPrice(),
                        response.executionSlippageBps(),
                        response.executionPartialEntries(),
                        response.executionSlices(),
                        response.executionNotes()
                ),
                response.executionAverageFillPrice()
        );
        return response;
    }
}

