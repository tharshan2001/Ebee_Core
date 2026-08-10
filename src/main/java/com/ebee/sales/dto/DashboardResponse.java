package com.ebee.sales.dto;

import com.ebee.trip.dto.TripResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardResponse {
    private DashboardPeriod period;
    private DashboardSummary summary;
    private DashboardToday today;
    private DashboardPreviousPeriod previousPeriod;
    @Builder.Default
    private List<TripResponse> activeTrips = List.of();
    @Builder.Default
    private long activeTripsCount = 0;
}
