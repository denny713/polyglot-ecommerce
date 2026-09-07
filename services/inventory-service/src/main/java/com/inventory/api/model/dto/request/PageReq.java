package com.inventory.api.model.dto.request;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;

/**
 * Paging and sorting inputs, inherited by every search request.
 * <p>
 * Each field may be left out; the defaults live in
 * {@code CommonRepositoryImpl#doSearch} — page 0, size 10, ascending by
 * {@code id}. {@code sortBy} is a property name and is not validated here, so an
 * unknown name fails when the query is built.
 */
@Getter
@Setter
public class PageReq {

    protected Integer page;
    protected Integer size;
    protected String sortBy;
    protected Sort.Direction sort;
}
