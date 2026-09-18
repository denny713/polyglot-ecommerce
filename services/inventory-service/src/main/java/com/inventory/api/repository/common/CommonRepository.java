package com.inventory.api.repository.common;

import com.inventory.api.model.dto.request.PageReq;
import com.inventory.api.model.entity.base.Base;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/** The operations every repository in this service gets on top of {@code JpaRepository}. */
@NoRepositoryBean
public interface CommonRepository<T extends Base, ID> extends JpaRepository<T, ID> {

    Page<T> doSearch(Specification<T> spec, PageReq req);

    List<T> doList(List<ID> ids);

    T doGet(ID id);

    T doDelete(ID id);

    T doActivate(ID id);

    T doDeactivate(ID id);
}
