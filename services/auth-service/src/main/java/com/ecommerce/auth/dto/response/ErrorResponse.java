package com.ecommerce.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Bentuk body error yang seragam untuk seluruh endpoint service ini.
 *
 * @param status    HTTP status code
 * @param error     kode error yang stabil dan bisa dibaca mesin, mis. {@code INVALID_CREDENTIALS}
 * @param message   penjelasan singkat untuk manusia
 * @param details   daftar error per-field; hanya terisi untuk kegagalan validasi
 * @param timestamp waktu server saat error dibuat
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        List<FieldError> details,
        Instant timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, Instant.now());
    }

    public static ErrorResponse of(int status, String error, String message, List<FieldError> details) {
        return new ErrorResponse(status, error, message, details, Instant.now());
    }

    /** Satu pelanggaran validasi pada satu field. */
    public record FieldError(String field, String message) {
    }
}
