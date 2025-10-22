package com.sivalabs.ft.features.config;

import com.sivalabs.ft.features.ApplicationProperties;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration class for asynchronous event processing.
 * This class defines a ThreadPoolTaskExecutor bean and configures
 * the ApplicationEventMulticaster to use this executor for async event delivery.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private final ApplicationProperties properties;

    public AsyncConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a ThreadPoolTaskExecutor bean with configurable properties.
     * This executor is used for processing @Async annotated methods.
     *
     * @return a configured ThreadPoolTaskExecutor
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.async().corePoolSize());
        executor.setMaxPoolSize(properties.async().maxPoolSize());
        executor.setQueueCapacity(properties.async().queueCapacity());
        executor.setThreadNamePrefix(properties.async().threadNamePrefix());
        executor.initialize();
        return executor;
    }

    /**
     * Creates an ApplicationEventMulticaster bean that uses the taskExecutor
     * for asynchronous event delivery.
     *
     * @return a configured SimpleApplicationEventMulticaster
     */
    @Bean
    public ApplicationEventMulticaster applicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(taskExecutor());
        return eventMulticaster;
    }
}
