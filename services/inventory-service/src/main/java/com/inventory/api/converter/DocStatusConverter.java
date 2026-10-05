package com.inventory.api.converter;

import com.inventory.api.enums.DocStatus;
import jakarta.persistence.Converter;

/** Persists {@link com.inventory.api.enums.DocStatus} as its label. */
@Converter(autoApply = true)
public class DocStatusConverter extends LabelConverter<DocStatus> {

    public DocStatusConverter() {
        super(DocStatus.class);
    }
}
