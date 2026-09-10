package com.inventory.api.repository;

import com.inventory.api.model.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/**
 * Reserves the next document number by calling the {@code generate_doc_no}
 * database function.
 * <p>
 * Numbering is done in the database on purpose: the sequence per type and date has
 * to stay gap-free and unique under concurrent submits, which a read-then-increment
 * in Java cannot guarantee. The function is created by its own migration, so this
 * interface breaks if that migration has not run.
 * <p>
 * It extends the bare {@code Repository} rather than {@code CommonRepository}
 * because it owns no entity — {@code PurchaseOrder} is named only to satisfy the
 * type parameter.
 */
public interface DocumentNumberRepository extends Repository<PurchaseOrder, Long> {

    @Query(value = "SELECT generate_doc_no(CAST(:type AS TEXT), CAST(:date AS DATE))", nativeQuery = true)
    String generateDocumentNumber(@Param("type") String type, @Param("date") LocalDate date);
}
