package com.sivalabs.ft.features.config.event;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Async;

public class ResilientApplicationEventMulticaster extends SimpleApplicationEventMulticaster {

    private static final Logger log = LoggerFactory.getLogger(ResilientApplicationEventMulticaster.class);

    @Override
    protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
        try {
            super.invokeListener(listener, event);
        } catch (Exception ex) {
            log.error(
                    "Event listener execution failed - Listener: {}, Event: {}, Mode: {}, Timestamp: {}, Payload: {}",
                    listener.getClass().getName(),
                    event.getClass().getName(),
                    isAsync(listener) ? "async" : "sync",
                    Instant.now(),
                    event,
                    ex);
        }
    }

    private boolean isAsync(ApplicationListener<?> listener) {
        Class<?> targetClass = AopUtils.getTargetClass(listener);
        return AnnotationUtils.findAnnotation(targetClass, Async.class) != null;
    }
}
