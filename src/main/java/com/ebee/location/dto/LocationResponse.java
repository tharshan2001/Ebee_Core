package com.ebee.location.dto;

import com.ebee.location.entity.Location;

import java.util.UUID;

public record LocationResponse(UUID id, String code, String name, Location.Type type, String district,
                               String address, boolean active, boolean isDefault) {
    public static LocationResponse of(Location l) {
        return new LocationResponse(l.getId(), l.getCode(), l.getName(), l.getType(), l.getDistrict(),
                l.getAddress(), l.isActive(), l.isDefaultLocation());
    }
}
