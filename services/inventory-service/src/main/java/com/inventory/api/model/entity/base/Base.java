package com.inventory.api.model.entity.base;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

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
    public Long createdBy;

    @Column(name = "updated_by")
    protected Long updatedBy;

    @Column(name = "created_at")
    protected LocalDateTime createdAt;

    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.setIsActive(true);
        this.setIsDeleted(false);
        this.setCreatedBy(1L);
        this.setUpdatedBy(1L);
        this.setCreatedAt(LocalDateTime.now());
        this.setUpdatedAt(LocalDateTime.now());
    }

    @PreUpdate
    public void preUpdate() {
        this.setUpdatedBy(1L);
        this.setUpdatedAt(LocalDateTime.now());
    }

    public void doDelete() {
        this.isDeleted = true;
    }

    public void doActivate() {
        this.isActive = true;
    }

    public void doDeactivate() {
        this.isActive = false;
    }
}
