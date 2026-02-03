package com.sivalabs.ft.features.config.event;

import java.time.Instant;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AsyncEventProperties.class)
public class ResilientEventConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ResilientEventConfig.class);

    @Bean(name = "asyncEventExecutor")
    public TaskExecutor eventTaskExecutor(AsyncEventProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix(properties.threadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster applicationEventMulticaster(
            @Qualifier("asyncEventExecutor") TaskExecutor taskExecutor) {

        ResilientApplicationEventMulticaster multicaster = new ResilientApplicationEventMulticaster();

        multicaster.setTaskExecutor(taskExecutor);

        return multicaster;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error(
                    "Event listener execution failed - Listener: {}, Event: {}, Mode: async, Timestamp: {}, Payload: {}",
                    method.getDeclaringClass().getName(),
                    params.length > 0 ? params[0].getClass().getName() : "Unknown",
                    Instant.now(),
                    params.length > 0 ? params[0] : "No Payload",
                    ex);
        };
    }
}
