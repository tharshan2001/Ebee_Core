package com.ebee.trip.mapper;

import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.entity.Trip;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TripMapper {

    @Mapping(target = "driverId", expression = "java(trip.getDriver() != null ? trip.getDriver().getId() : null)")
    @Mapping(target = "driverName", expression = "java(trip.getDriver() != null ? trip.getDriver().getUser().getFirstName() + ' ' + trip.getDriver().getUser().getLastName() : null)")
    TripResponse toResponse(Trip trip);
}
