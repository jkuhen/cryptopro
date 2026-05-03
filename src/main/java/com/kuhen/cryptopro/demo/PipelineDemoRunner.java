package com.kuhen.cryptopro.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cryptopro.demo.runner", name = "enabled", havingValue = "true")
public class PipelineDemoRunner implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineDemoRunner.class);

    private final PipelineDemoService pipelineDemoService;

    public PipelineDemoRunner(PipelineDemoService pipelineDemoService) {
        this.pipelineDemoService = pipelineDemoService;
    }

    @Override
    public void run(String... args) {
        PipelineDemoResponse response = pipelineDemoService.run("BTCUSDT");
        LOGGER.info("Demo signal => symbol={}, direction={}, score={}, tradable={}, rationale={}",
                response.symbol(),
                response.direction(),
                response.finalScore(),
                response.tradable(),
                response.rationale());
    }
}

