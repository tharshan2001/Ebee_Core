package com.ebee.common.util;

import org.springframework.data.domain.Sort;

public final class SortUtils {

    private SortUtils() {}

    public static Sort parse(String sort, SortableFields.AllowedFor allowedFor) {
        String normalisedSort = SortableFields.validate(sort, allowedFor);
        Sort.Direction direction = normalisedSort.endsWith(",asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = normalisedSort.replace(",asc", "").replace(",desc", "");
        return Sort.by(direction, sortField);
    }
}
