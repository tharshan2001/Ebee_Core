package com.ebee.customer.dto;

import com.ebee.common.response.PageResponse;

import java.math.BigDecimal;

/** One page of customers with money outstanding either way; the figures cover every match, not just the page. */
public record CustomerBalancePage(PageResponse<CustomerResponse> page, long count, BigDecimal totalOutstanding,
                                  BigDecimal totalAdvance) {
}
