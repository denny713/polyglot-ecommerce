package com.ecommerce.auth.handler;

import com.ecommerce.auth.dto.response.ErrorResponse;
import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Comparator;
import java.util.List;

/**
 * Normalizes the response shape for {@code @Valid} failures on request DTOs, e.g.
 * a blank username or password.
 *
 * <p>It maps {@code ResteasyReactiveViolationException} — not its parent
 * {@code ConstraintViolationException} — because Quarkus already ships a built-in
 * mapper for that type, and JAX-RS always picks the mapper most specific to the
 * type actually thrown. The {@link Priority} of {@link Priorities#USER} makes sure
 * this mapper is the one used.
 */
@Provider
@Priority(Priorities.USER)
public class ValidationExceptionMapper implements ExceptionMapper<ResteasyReactiveViolationException> {

    @Override
    public Response toResponse(ResteasyReactiveViolationException exception) {
        List<ErrorResponse.FieldError> details = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        fieldNameOf(violation), violation.getMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(
                        Response.Status.BAD_REQUEST.getStatusCode(),
                        "VALIDATION_ERROR",
                        "Request validation failed",
                        details))
                .build();
    }

    /**
     * The default property path looks like {@code login.request.username} (method
     * name, parameter name, then field). Only the last segment is useful to the
     * client.
     */
    private String fieldNameOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
