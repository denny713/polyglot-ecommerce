package com.inventory.api.enums;

/**
 * Contract that lets {@link com.inventory.api.converter.LabelConverter} work
 * against any enum without knowing its type.
 */
public interface Labeled {

    String getLabel();
}
