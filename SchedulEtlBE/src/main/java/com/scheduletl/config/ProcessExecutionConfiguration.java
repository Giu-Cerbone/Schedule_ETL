package com.scheduletl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Configuration
public class ProcessExecutionConfiguration {

    @Bean(destroyMethod = "close")
    public ExecutorService processExecutionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public Semaphore processExecutionSemaphore(
            @Value("${scheduletl.process.max-concurrent:4}") int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("Process concurrency must be at least 1");
        }
        return new Semaphore(maxConcurrent, true);
    }
}
