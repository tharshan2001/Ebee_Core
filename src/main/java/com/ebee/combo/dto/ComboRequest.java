package com.ebee.combo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ComboRequest {

    @NotBlank(message = "Combo name is required")
    private String name;

    private String description;

    @NotEmpty(message = "Combo must contain at least one item")
    @Valid
    private List<ComboItemRequest> items;

    @Valid
    private ComboRuleRequest rule;
}
