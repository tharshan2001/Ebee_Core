package com.ebee.supplier.dto;

import com.ebee.common.response.PageResponse;

import java.math.BigDecimal;

/** One page of suppliers we owe, largest first; the figures cover all of them. */
public record SupplierDuesPage(PageResponse<SupplierResponse> page, long count, BigDecimal totalOutstanding,
                               String largestName, BigDecimal largestAmount) {
}
