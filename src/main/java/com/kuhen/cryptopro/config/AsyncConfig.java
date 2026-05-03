package com.kuhen.cryptopro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration.
 *
 * <p>{@code candlePersistenceExecutor} – single-threaded, queues up to 2 000
 * tasks – used exclusively for persisting closed candles from the Binance
 * WebSocket feed so that database I/O never blocks the WS callback thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "candlePersistenceExecutor")
    public Executor candlePersistenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("candle-persist-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

