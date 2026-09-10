package com.inventory.api.converter;

import com.inventory.api.enums.DocType;
import jakarta.persistence.Converter;

/**
 * Persists {@link com.inventory.api.enums.DocType} as its label.
 * <p>
 * {@code autoApply = true} means every entity field of that type is converted
 * without being annotated, so the enum can gain a new constant without touching
 * the entities.
 */
@Converter(autoApply = true)
public class DocTypeConverter extends LabelConverter<DocType> {

    public DocTypeConverter() {
        super(DocType.class);
    }
}
