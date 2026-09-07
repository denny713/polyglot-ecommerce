package com.inventory.api.converter;

import com.inventory.api.enums.DocStatus;
import jakarta.persistence.Converter;

/**
 * Persists {@link com.inventory.api.enums.DocStatus} as its label.
 * <p>
 * {@code autoApply = true} means every entity field of that type is converted
 * without being annotated, so the enum can gain a new constant without touching
 * the entities.
 */
@Converter(autoApply = true)
public class DocStatusConverter extends LabelConverter<DocStatus> {

    public DocStatusConverter() {
        super(DocStatus.class);
    }
}
