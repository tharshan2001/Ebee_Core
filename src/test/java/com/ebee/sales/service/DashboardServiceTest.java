package com.ebee.sales.service;

import com.ebee.commission.repository.CommissionRepository;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.service.TripService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CommissionRepository commissionRepository;
    @Mock private TripService tripService;

    @InjectMocks private DashboardService dashboardService;

    private void stubKpis() {
        when(orderRepository.sumRevenueByDateRange(any(), any())).thenReturn(new BigDecimal("1000"));
        when(orderRepository.countOrdersByDateRange(any(), any())).thenReturn(5L);
        when(commissionRepository.sumCommissionLockedByDateRange(any(), any())).thenReturn(new BigDecimal("50"));
        when(orderRepository.countOrdersByDateRangeAllStatuses(any(), any())).thenReturn(10L);
        when(orderRepository.countDeliveredByDateRange(any(), any())).thenReturn(3L);
    }

    private TripResponse activeTrip() {
        TripResponse trip = new TripResponse();
        trip.setId(UUID.randomUUID());
        trip.setName("Morning Route");
        trip.setStatus(TripStatus.IN_PROGRESS);
        return trip;
    }

    @Test
    void getDashboard_populatesActiveTrips() {
        stubKpis();
        TripResponse active = activeTrip();
        when(tripService.getAllTrips(TripStatus.IN_PROGRESS, null, null, null))
                .thenReturn(List.of(active));

        var result = dashboardService.getDashboard(null, null);

        assertThat(result.getActiveTrips()).containsExactly(active);
        assertThat(result.getActiveTripsCount()).isEqualTo(1);
        verify(tripService).getAllTrips(TripStatus.IN_PROGRESS, null, null, null);
    }

    @Test
    void getDashboard_activeTripsEmptyWhenNoneRunning() {
        stubKpis();
        when(tripService.getAllTrips(TripStatus.IN_PROGRESS, null, null, null))
                .thenReturn(List.of());

        var result = dashboardService.getDashboard(null, null);

        assertThat(result.getActiveTrips()).isEmpty();
        assertThat(result.getActiveTripsCount()).isZero();
    }
}
