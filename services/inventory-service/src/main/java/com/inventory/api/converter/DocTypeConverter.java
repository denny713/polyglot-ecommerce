package com.inventory.api.converter;

import com.inventory.api.enums.DocType;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocTypeConverter extends LabelConverter<DocType> {

    public DocTypeConverter() {
        super(DocType.class);
    }
}
