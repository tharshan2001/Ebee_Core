package com.ebee.trip.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.response.PageResponse;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.delivery.service.DeliveryService;
import com.ebee.delivery.service.DeliveryStateTransition;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.enums.ExpenseStatus;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.trip.dto.AddOrdersToTripRequest;
import com.ebee.trip.dto.CreateTripRequest;
import com.ebee.trip.dto.TripOrderMarkerDto;
import com.ebee.trip.dto.TripResponse;
import com.ebee.trip.dto.UpdateTripRequest;
import com.ebee.trip.entity.Trip;
import com.ebee.trip.enums.TripStatus;
import com.ebee.trip.event.TripOrdersChangedEvent;
import com.ebee.trip.event.TripStatusChangedEvent;
import com.ebee.trip.mapper.TripMapper;
import com.ebee.trip.repository.TripRepository;
import com.ebee.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only trip lookups and map markers. TripService delegates here. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripQueryService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final SecurityService securityService;
    private final TripViews tripViews;

    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));
        tripViews.requireDriverOwnsTripForNonAdmin(trip);
        return tripViews.toTripResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getAllTrips(TripStatus status, UUID driverId, java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        List<Trip> trips = tripRepository.search(status, driverId, dateFrom, dateTo);
        return trips.stream().map(tripViews::toTripResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> getAllTripsPaginated(
            TripStatus status, UUID driverId, java.time.LocalDate dateFrom, java.time.LocalDate dateTo, String search,
            Pageable pageable) {
        Page<Trip> tripsPage = tripRepository.searchByText(status, driverId, dateFrom, dateTo,
                com.ebee.common.util.SearchPatterns.contains(search), pageable);
        return PageResponse.from(tripsPage.map(tripViews::toTripResponse));
    }

    /** How many trips are in each status (statuses with none are included as 0). */
    @Transactional(readOnly = true)
    public java.util.Map<TripStatus, Long> countByStatus() {
        java.util.Map<TripStatus, Long> counts = new java.util.EnumMap<>(TripStatus.class);
        for (TripStatus s : TripStatus.values()) {
            counts.put(s, 0L);
        }
        tripRepository.countByStatus().forEach(row -> counts.put((TripStatus) row[0], (Long) row[1]));
        return counts;
    }

    @Transactional(readOnly = true)
    public List<TripOrderMarkerDto> getActiveTripOrderMarkers(UUID driverId) {
        UUID effectiveDriverId = driverId;
        if (!securityService.isAdmin()) {
            Driver currentDriver = tripViews.resolveCurrentDriver();
            effectiveDriverId = currentDriver.getId();
        }
        return tripRepository.findByDriverIdAndStatus(effectiveDriverId, TripStatus.IN_PROGRESS)
                .map(trip -> {
                    List<Order> orders = orderRepository.findByTripId(trip.getId());
                    trip.setOrders(orders);
                    return tripViews.buildOrderMarkers(trip);
                })
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<TripOrderMarkerDto> getTripOrderMarkers(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));
        tripViews.requireDriverOwnsTripForNonAdmin(trip);
        List<Order> orders = orderRepository.findByTripId(tripId);
        trip.setOrders(orders);
        return tripViews.buildOrderMarkers(trip);
    }

}
