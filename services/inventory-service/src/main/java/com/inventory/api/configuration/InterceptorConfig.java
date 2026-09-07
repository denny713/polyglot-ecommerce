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
 * <p>
 * The id is put in the SLF4J {@link org.slf4j.MDC} under {@code correlationId},
 * which is the key the console pattern in {@code application.yml} prints as
 * {@code %X&#123;correlationId&#125;}, and removed again in {@code afterCompletion}
 * so the pooled request thread does not leak it into the next request.
 * <p>
 * Note that a {@code HandlerInterceptor} is only invoked once it is registered
 * through a {@code WebMvcConfigurer#addInterceptors}; being a {@code @Component}
 * alone is not enough.
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
