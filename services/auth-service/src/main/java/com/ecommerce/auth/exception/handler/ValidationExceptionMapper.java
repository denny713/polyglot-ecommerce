package com.ecommerce.auth.exception.handler;

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
 * Menyeragamkan bentuk response untuk kegagalan {@code @Valid} pada DTO request,
 * mis. username atau password kosong.
 *
 * <p>Yang dipetakan adalah {@code ResteasyReactiveViolationException} — bukan
 * {@code ConstraintViolationException} induknya — karena Quarkus sudah punya
 * mapper bawaan untuk tipe itu, dan JAX-RS selalu memilih mapper yang paling
 * spesifik terhadap tipe yang benar-benar dilempar. {@link Priority} dengan
 * {@link Priorities#USER} memastikan mapper ini yang dipakai.
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
     * Property path bawaan berbentuk {@code login.request.username} (nama method,
     * nama parameter, lalu field). Yang berguna bagi klien hanya ruas terakhir.
     */
    private String fieldNameOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
