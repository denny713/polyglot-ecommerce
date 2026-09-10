package com.inventory.api.repository.common;

import com.inventory.api.model.dto.request.PageReq;
import com.inventory.api.model.entity.base.Base;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * The operations every repository in this service gets on top of
 * {@code JpaRepository}.
 * <p>
 * They exist because the plain CRUD methods would each need the same surrounding
 * work: a not-found that reports which entity and id, paging defaults, soft-delete
 * instead of removal, and a guard against activating something already active.
 * {@link CommonRepositoryImpl} supplies the bodies.
 * <p>
 * {@code @NoRepositoryBean} keeps Spring Data from trying to build an
 * implementation of this interface itself.
 */
@NoRepositoryBean
public interface CommonRepository<T extends Base, ID> extends JpaRepository<T, ID> {

    Page<T> doSearch(Specification<T> spec, PageReq req);

    List<T> doList(List<ID> ids);

    T doGet(ID id);

    T doDelete(ID id);

    T doActivate(ID id);

    T doDeactivate(ID id);
}
