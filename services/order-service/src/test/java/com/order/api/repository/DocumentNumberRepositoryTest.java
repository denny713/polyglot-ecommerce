package com.order.api.repository;

import com.order.api.enums.DocType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Tests that a document type reaches the database function as the prefix it expects. */
class DocumentNumberRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 23);

    @ParameterizedTest
    @EnumSource(DocType.class)
    void shouldPassTheTypeAsItsLabel(DocType type) {
        DocumentNumberRepository repository = mock(DocumentNumberRepository.class, CALLS_REAL_METHODS);
        doReturn(type.getLabel() + "20260923001").when(repository).generate(type.getLabel(), DATE);

        assertEquals(type.getLabel() + "20260923001", repository.generateDocumentNumber(type, DATE));

        verify(repository).generate(type.getLabel(), DATE);
    }
}
