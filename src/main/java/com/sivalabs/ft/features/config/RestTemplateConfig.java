package com.sivalabs.ft.features.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate to propagate correlation ID
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.interceptors(correlationIdInterceptor()).build();
    }

    private ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            request.getHeaders()
                    .set(CorrelationIdContext.CORRELATION_ID_HEADER, CorrelationIdContext.getCorrelationIdOrDefault());
            return execution.execute(request, body);
        };
    }
}
