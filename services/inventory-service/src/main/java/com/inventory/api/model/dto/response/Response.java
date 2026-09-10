package com.inventory.api.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single response shape of this service, used by both successful calls and
 * {@code ResponseHandler}.
 * <p>
 * {@code code} repeats the HTTP status inside the body so a client that only reads
 * the payload still sees it, and {@code data} is deliberately untyped: a payload
 * on success, an error description on failure.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Response {

    private int code;
    private String status;
    private Object data;
}
