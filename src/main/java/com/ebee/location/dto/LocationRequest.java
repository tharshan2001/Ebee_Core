package com.ebee.location.dto;

import com.ebee.location.entity.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocationRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull Location.Type type,
        String district,
        String address,
        Boolean active,
        Boolean makeDefault) {
}
