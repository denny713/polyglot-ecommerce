package com.inventory.api.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Tags each request with a correlation id so one request can be followed across
 * log lines, and echoes it back as {@code X-Correlation-Id} for the caller.
 */
@Component
public class InterceptorConfig implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String corId = UUID.randomUUID().toString();
        MDC.put("correlationId", corId);
        response.setHeader("X-Correlation-Id", corId);
        return true;
    }

    @Override
    public void afterCompletion(final @NonNull HttpServletRequest request,
                                final @NonNull HttpServletResponse response,
                                final @NonNull Object handler,
                                final Exception ex) {
        MDC.remove("correlationId");
    }
}
