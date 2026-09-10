package com.inventory.api.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The {@code Response} envelope plus record counts, returned by the search
 * endpoints.
 * <p>
 * {@code data} holds the page, not the whole result set. {@code totalRecord} and
 * {@code filterRecord} are both filled from the filtered total today, so they only
 * differ once an unfiltered count is added.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PagingResponse {

    private int code;
    private String status;
    private Object data;
    private long totalRecord;
    private long filterRecord;
}
