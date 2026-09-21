package com.order.api.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/** Tags each request with a correlation id, echoed back as {@code X-Correlation-Id}, so it can be followed across log lines. */
@Configuration
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
