package com.inventory.api.model.entity.base;

import com.inventory.api.util.AccountUtil;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Identity, auditing and soft-delete for every entity in this service.
 * <p>
 * Rows are never removed: {@code doDelete} only sets {@code is_deleted}, and the
 * {@code @SQLRestriction} on this class is what keeps those rows out of every
 * query, so a soft-deleted row reads as absent rather than as inactive.
 * <p>
 * {@code createdBy} and {@code updatedBy} are stamped from
 * {@link com.inventory.api.util.AccountUtil}, which is populated per request by
 * {@code TokenFilter}. A write from outside a request thread therefore leaves them
 * null.
 * <p>
 * {@code cascadeChildren} is the extension point: an entity that overrides it has
 * delete, activate and deactivate propagate to its children in one call. The
 * default is empty, so a leaf entity needs no override.
 */
@Getter
@Setter
@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("is_deleted = false")
public abstract class Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    protected Long id;

    @Column(name = "is_active")
    protected Boolean isActive;

    @Column(name = "is_deleted")
    protected Boolean isDeleted;

    @Column(name = "created_by")
    public UUID createdBy;

    @Column(name = "updated_by")
    protected UUID updatedBy;

    @Column(name = "created_at")
    protected LocalDateTime createdAt;

    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.setIsActive(true);
        this.setIsDeleted(false);
        this.setCreatedBy(AccountUtil.getUserLogin());
        this.setUpdatedBy(AccountUtil.getUserLogin());
        this.setCreatedAt(LocalDateTime.now());
        this.setUpdatedAt(LocalDateTime.now());
    }

    @PreUpdate
    public void preUpdate() {
        this.setUpdatedBy(AccountUtil.getUserLogin());
        this.setUpdatedAt(LocalDateTime.now());
    }

    public void doDelete() {
        this.isDeleted = true;
        cascadeChildren().forEach(Base::doDelete);
    }

    public void doActivate() {
        this.isActive = true;
        cascadeChildren().forEach(Base::doActivate);
    }

    public void doDeactivate() {
        this.isActive = false;
        cascadeChildren().forEach(Base::doDeactivate);
    }

    protected List<? extends Base> cascadeChildren() {
        return List.of();
    }
}
