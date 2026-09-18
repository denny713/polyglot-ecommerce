package com.inventory.api.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The single response shape of this service, used by both successful calls and {@code ResponseHandler}. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Response {

    private int code;
    private String status;
    private Object data;
}
