package com.inventory.api.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
