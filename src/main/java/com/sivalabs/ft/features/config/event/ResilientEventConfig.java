package com.sivalabs.ft.features.config.event;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class ResilientEventConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ResilientEventConfig.class);

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
                    "Event listener execution failed - "
                            + "Listener: {}, Event: {}, Mode: {}, Timestamp: {}, Payload: {}",
                    method.getDeclaringClass().getName(),
                    params.length > 0 ? params[0].getClass().getName() : "Unknown",
                    "async",
                    Instant.now(),
                    params.length > 0 ? params[0] : "No Payload",
                    ex);
        };
    }
}
