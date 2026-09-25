package com.order.api.repository;

import com.order.api.model.entity.SalesOrder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

/** Reserves the next document number by calling the {@code generate_doc_no} database function. */
public interface DocumentNumberRepository extends Repository<SalesOrder, Long> {

    @Query(value = "SELECT generate_doc_no(CAST(:type AS TEXT), CAST(:date AS DATE))", nativeQuery = true)
    String generateDocumentNumber(@Param("type") String type, @Param("date") LocalDate date);
}
