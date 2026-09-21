package com.order.api.repository.common;

import com.order.api.constant.ActionType;
import com.order.api.exception.BadRequestException;
import com.order.api.exception.NotFoundException;
import com.order.api.model.dto.request.PageReq;
import com.order.api.model.entity.base.Base;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.util.StringUtils;

import java.util.List;

/** Implements {@link CommonRepository} for every entity at once. */
public class CommonRepositoryImpl<T extends Base, ID> extends SimpleJpaRepository<T, ID>
        implements CommonRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInfo;

    public CommonRepositoryImpl(JpaEntityInformation<T, ?> entityInfo, EntityManager entityMgr) {
        super(entityInfo, entityMgr);
        this.entityInfo = entityInfo;
    }

    @Override
    public Page<T> doSearch(Specification<T> spec, PageReq req) {
        return super.findAll(spec, PageRequest.of(
                (req.getPage() == null) ? 0 : req.getPage(),
                (req.getSize() == null || req.getSize() <= 0) ? 10 : req.getSize(),
                Sort.by(
                        (req.getSort() == null) ? Sort.Direction.ASC : req.getSort(),
                        StringUtils.hasText(req.getSortBy()) ? req.getSortBy() : "id")));
    }

    @Override
    public List<T> doList(List<ID> ids) {
        return super.findAllById(ids);
    }

    @Override
    public T doGet(ID id) {
        return getDetail(id);
    }

    @Override
    public T doDelete(ID id) {
        T entity = getDetail(id);
        validate(entity, id, ActionType.DEL);

        entity.doDelete();
        return super.save(entity);
    }

    @Override
    public T doActivate(ID id) {
        T entity = getDetail(id);
        validate(entity, id, ActionType.ACT);

        entity.doActivate();
        return super.save(entity);
    }

    @Override
    public T doDeactivate(ID id) {
        T entity = getDetail(id);
        validate(entity, id, ActionType.DCT);

        entity.doDeactivate();
        return super.save(entity);
    }

    private String getTableName() {
        return entityInfo.getJavaType().getSimpleName();
    }

    private T getDetail(ID id) {
        return super.findById(id).orElseThrow(() -> new NotFoundException(String.format("Data %s with id %s not found",
                getTableName(), id)));
    }

    private void activeValidate(T entity, ID id) {
        if (Boolean.TRUE.equals(entity.getIsActive())) {
            throw new BadRequestException(String.format(
                    "Data %s with id %s already active, process cannot be continued",
                    getTableName(), id));
        }
    }

    private void inactiveValidate(T entity, ID id) {
        if (Boolean.FALSE.equals(entity.getIsActive())) {
            throw new BadRequestException(String.format(
                    "Data %s with id %s already inactive, process cannot be continued",
                    getTableName(), id));
        }
    }

    private void deleteValidate(T entity, ID id) {
        if (Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new BadRequestException(String.format(
                    "Data %s with id %s already deleted, process cannot be continued",
                    getTableName(), id));
        }
    }

    private void validate(T entity, ID id, String action) {
        deleteValidate(entity, id);

        switch (action) {
            case ActionType.ACT -> activeValidate(entity, id);
            case ActionType.DCT -> inactiveValidate(entity, id);
        }
    }
}
