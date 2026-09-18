package com.order.api.enums;

/** Contract that lets {@link com.order.api.converter.LabelConverter} work against any enum without knowing its type. */
public interface Labeled {

    String getLabel();
}
