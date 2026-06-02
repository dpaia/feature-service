package com.sivalabs.ft.features.advice;

import com.sivalabs.ft.features.config.CorrelationIdContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Controller advice to ensure correlation ID is included in all responses
 */
@ControllerAdvice
public class CorrelationIdAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // Ensure correlation ID is included in response headers
        String correlationId = CorrelationIdContext.getCorrelationIdOrDefault();
        response.getHeaders().set(CorrelationIdContext.CORRELATION_ID_HEADER, correlationId);

        return body;
    }
}
