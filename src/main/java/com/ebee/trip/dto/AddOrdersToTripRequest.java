package com.ebee.trip.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class AddOrdersToTripRequest {

    private List<UUID> orderIdsToAdd = new ArrayList<>();

    private List<UUID> orderIdsToRemove = new ArrayList<>();
}
