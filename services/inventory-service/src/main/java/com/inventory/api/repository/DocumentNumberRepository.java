package com.inventory.api.repository;

import com.inventory.api.model.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface DocumentNumberRepository extends Repository<PurchaseOrder, Long> {

    @Query(value = "SELECT generate_doc_no(CAST(:type AS TEXT), CAST(:date AS DATE))", nativeQuery = true)
    String generateDocumentNumber(@Param("type") String type, @Param("date") LocalDate date);
}
