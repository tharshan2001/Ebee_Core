package com.ebee.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerByDistrictReport {
    private String district;
    private Long customerCount;
}
