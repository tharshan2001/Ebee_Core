package com.ebee.trip.event;

import com.ebee.trip.dto.TripResponse;

import java.util.List;

/**
 * STOMP payload broadcast to {@code /topic/active-trips}. Carries the full
 * list of IN_PROGRESS trips as a replace-style snapshot so subscribers can
 * simply render the latest state without diffing.
 */
public record ActiveTripsMessage(List<TripResponse> activeTrips, long activeTripsCount) {
}
