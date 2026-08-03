package com.ebee.trip.service;

import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.response.PageResponse;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.event.DeliveryStatusChangedEvent;
import com.ebee.driver.entity.Driver;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.expense.dto.ExpenseResponse;
import com.ebee.expense.entity.Expense;
import com.ebee.expense.enums.ExpenseCategory;
import com.ebee.expense.mapper.ExpenseMapper;
import com.ebee.expense.repository.ExpenseRepository;
import com.ebee.order.entity.Order;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final DriverRepository driverRepository;
    private final TripMapper tripMapper;
    private final ExpenseMapper expenseMapper;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecurityService securityService;

    @Transactional
    public TripResponse createTrip(CreateTripRequest request) {
        if (tripRepository.existsByName(request.getName())) {
            throw new IllegalStateException("Trip with name '" + request.getName() + "' already exists");
        }

        Trip.TripBuilder builder = Trip.builder()
                .name(request.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .notes(request.getNotes())
                .status(TripStatus.PLANNED);

        if (request.getDriverId() != null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            builder.driver(driver);
        }

        Trip trip = tripRepository.save(builder.build());

        auditLogService.record(AuditAction.CREATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' created");

        log.info("Trip {} created with name {}", trip.getId(), trip.getName());
        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse updateTrip(UUID tripId, UpdateTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        boolean locked = trip.getStatus() != TripStatus.PLANNED;

        if (locked && request.getStartDate() != null) {
            throw new IllegalStateException("Cannot change start date of a trip that is not in PLANNING status");
        }
        if (locked && request.getEndDate() != null) {
            throw new IllegalStateException("Cannot change end date of a trip that is not in PLANNING status");
        }
        if (locked && request.getDriverId() != null) {
            throw new IllegalStateException("Cannot change driver of a trip that is not in PLANNING status");
        }

        if (request.getName() != null && !request.getName().equals(trip.getName())) {
            if (tripRepository.existsByNameAndIdNot(request.getName(), tripId)) {
                throw new IllegalStateException("Trip with name '" + request.getName() + "' already exists");
            }
            trip.setName(request.getName());
        }

        if (request.getStartDate() != null) {
            trip.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            trip.setEndDate(request.getEndDate());
        }
        if (request.getNotes() != null) {
            trip.setNotes(request.getNotes());
        }

        if (request.getDriverId() != null && trip.getDriver() == null) {
            Driver driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            trip.setDriver(driver);
            syncOrdersDriver(trip);
        } else if (request.getDriverId() != null && !request.getDriverId().equals(trip.getDriver().getId())) {
            Driver newDriver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "id", request.getDriverId()));
            Driver oldDriver = trip.getDriver();
            trip.setDriver(newDriver);
            syncOrdersDriver(trip);
            auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                    "Driver changed from " + oldDriver.getUser().getFirstName() + " to " + newDriver.getUser().getFirstName());
        }

        trip = tripRepository.save(trip);

        if (request.getMileageKm() != null) {
            trip.setMileageKm(request.getMileageKm());
            trip = tripRepository.save(trip);
        }

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' updated");

        return toTripResponse(trip);
    }

    @Transactional(readOnly = true)
    public TripResponse getTripById(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));
        return toTripResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getAllTrips(TripStatus status, UUID driverId, java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        List<Trip> trips;

        if (status != null && driverId != null) {
            trips = tripRepository.findByDriverIdAndStatusIn(driverId, List.of(status));
        } else if (status != null) {
            trips = tripRepository.findByStatusNotIn(List.of());
            trips = trips.stream().filter(t -> t.getStatus() == status).collect(Collectors.toList());
        } else if (driverId != null) {
            trips = tripRepository.findByDriverId(driverId);
        } else {
            trips = tripRepository.findAll();
        }

        if (dateFrom != null) {
            trips = trips.stream().filter(t -> !t.getStartDate().isBefore(dateFrom)).collect(Collectors.toList());
        }
        if (dateTo != null) {
            trips = trips.stream().filter(t -> !t.getEndDate().isAfter(dateTo)).collect(Collectors.toList());
        }

        return trips.stream().map(this::toTripResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<TripResponse> getAllTripsPaginated(
            TripStatus status, UUID driverId, java.time.LocalDate dateFrom, java.time.LocalDate dateTo, Pageable pageable) {
        Page<Trip> tripsPage;

        if (status != null && driverId != null) {
            tripsPage = tripRepository.findByDriverIdAndStatus(driverId, status, pageable);
        } else if (status != null) {
            tripsPage = tripRepository.findByStatus(status, pageable);
        } else if (driverId != null) {
            tripsPage = tripRepository.findByDriverId(driverId, pageable);
        } else {
            tripsPage = tripRepository.findAll(pageable);
        }

        if (dateFrom != null || dateTo != null) {
            List<Trip> filtered = tripsPage.getContent().stream()
                    .filter(t -> (dateFrom == null || !t.getStartDate().isBefore(dateFrom)))
                    .filter(t -> (dateTo == null || !t.getEndDate().isAfter(dateTo)))
                    .collect(Collectors.toList());
            long total = tripsPage.getTotalElements();
            return new PageResponse<>(filtered.stream().map(this::toTripResponse).collect(Collectors.toList()),
                    tripsPage.getNumber(), tripsPage.getSize(), total,
                    tripsPage.getTotalPages(), tripsPage.isFirst(), tripsPage.isLast());
        }

        return PageResponse.from(tripsPage.map(this::toTripResponse));
    }

    @Transactional(readOnly = true)
    public List<TripOrderMarkerDto> getActiveTripOrderMarkers(UUID driverId) {
        return tripRepository.findByDriverIdAndStatus(driverId, TripStatus.IN_PROGRESS)
                .map(trip -> {
                    List<Order> orders = orderRepository.findByTripId(trip.getId());
                    trip.setOrders(orders);
                    return buildOrderMarkers(trip);
                })
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<TripOrderMarkerDto> getTripOrderMarkers(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));
        List<Order> orders = orderRepository.findByTripId(tripId);
        trip.setOrders(orders);
        return buildOrderMarkers(trip);
    }

    @Transactional
    public TripResponse cancelTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Trip is already cancelled");
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.CANCELLED);
        trip = tripRepository.save(trip);

        for (Order order : trip.getOrders()) {
            DeliveryStatus previousDeliveryStatus = order.getDeliveryStatus();
            order.setTrip(null);
            order.setDeliveryStatus(DeliveryStatus.PENDING);
            orderRepository.save(order);

            eventPublisher.publishEvent(new DeliveryStatusChangedEvent(
                    this, order, previousDeliveryStatus, "Trip cancelled — order returned to pending", LocalDateTime.now()));
        }

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' cancelled");

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, List.of()));

        return toTripResponse(trip);
    }

    @Transactional
    public void deleteTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNING trips can be deleted");
        }
        if (!trip.getOrders().isEmpty()) {
            throw new IllegalStateException("Cannot delete a trip that has orders assigned");
        }

        tripRepository.delete(trip);

        auditLogService.record(AuditAction.DELETE, "Trip", tripId,
                "Trip '" + trip.getName() + "' deleted");
    }

    @Transactional
    public TripResponse startTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNING trips can be started");
        }
        if (trip.getDriver() == null) {
            throw new IllegalStateException("Cannot start a trip without a driver assigned");
        }

        Optional<Trip> existingInProgress = tripRepository.findByDriverIdAndStatus(
                trip.getDriver().getId(), TripStatus.IN_PROGRESS);
        if (existingInProgress.isPresent()) {
            throw new IllegalStateException("Driver already has an active trip: " + existingInProgress.get().getName());
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' started");

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, buildOrderMarkers(trip)));

        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse addOrdersToTrip(UUID tripId, AddOrdersToTripRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify orders on a " + trip.getStatus() + " trip");
        }
        if (trip.getDriver() == null) {
            throw new IllegalStateException("Cannot add orders to a trip without a driver");
        }

        if (request.getOrderIdsToAdd() != null && !request.getOrderIdsToAdd().isEmpty()) {
            for (UUID orderId : request.getOrderIdsToAdd()) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

                if (order.getTrip() != null && order.getTrip().getId().equals(tripId)) {
                    continue;
                }

                if (order.getDeliveryAssignedTo() != null
                        && !order.getDeliveryAssignedTo().getId().equals(trip.getDriver().getUser().getId())) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is assigned to a different driver");
                }

                if (order.getTrip() != null) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is already on trip '" + order.getTrip().getName() + "'");
                }

                order.setTrip(trip);
                order.setDeliveryAssignedTo(trip.getDriver().getUser());
                orderRepository.save(order);
            }
        }

        if (request.getOrderIdsToRemove() != null && !request.getOrderIdsToRemove().isEmpty()) {
            for (UUID orderId : request.getOrderIdsToRemove()) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

                if (order.getTrip() == null || !order.getTrip().getId().equals(tripId)) {
                    throw new IllegalStateException("Order " + order.getOrderNumber() + " is not on this trip");
                }

                order.setTrip(null);
                orderRepository.save(order);
            }
        }

        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Orders updated on trip '" + trip.getName() + "'");

        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, buildOrderMarkers(trip)));

        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse updateMileage(UUID tripId, BigDecimal mileageKm) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        trip.setMileageKm(mileageKm);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Mileage updated to " + mileageKm + " km on trip '" + trip.getName() + "'");

        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse completeTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", "id", tripId));

        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only IN_PROGRESS trips can be completed");
        }

        TripStatus previousStatus = trip.getStatus();
        trip.setStatus(TripStatus.COMPLETED);
        trip = tripRepository.save(trip);

        auditLogService.record(AuditAction.UPDATE, "Trip", trip.getId(),
                "Trip '" + trip.getName() + "' completed");

        eventPublisher.publishEvent(new TripStatusChangedEvent(this, trip, previousStatus));
        eventPublisher.publishEvent(new TripOrdersChangedEvent(this, trip, List.of()));

        return toTripResponse(trip);
    }

    private void syncOrdersDriver(Trip trip) {
        for (Order order : trip.getOrders()) {
            if (trip.getDriver() != null) {
                order.setDeliveryAssignedTo(trip.getDriver().getUser());
            }
            orderRepository.save(order);
        }
    }

    private List<TripOrderMarkerDto> buildOrderMarkers(Trip trip) {
        List<Order> orders = orderRepository.findByTripId(trip.getId());
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        return orders.stream()
                .map(this::toTripOrderMarkerDto)
                .toList();
    }

    private TripOrderMarkerDto toTripOrderMarkerDto(Order order) {
        String customerName = null;
        String customerPhone = null;
        String street = null;
        String addressLine2 = null;
        String city = null;
        String postalCode = null;
        String district = null;
        String country = null;

        if (order.getCustomer() != null) {
            customerName = order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName();
            customerPhone = order.getCustomer().getPhoneNumber();
            if (order.getCustomer().getAddress() != null) {
                var addr = order.getCustomer().getAddress();
                street = addr.getStreet();
                addressLine2 = addr.getAddressLine2();
                city = addr.getCity();
                postalCode = addr.getPostalCode();
                district = addr.getDistrict() != null ? addr.getDistrict().getDisplayName() : null;
                country = addr.getCountry();
            }
        }

        String formattedAddress = buildFormattedAddress(street, addressLine2, city, postalCode, district, country);

        return new TripOrderMarkerDto(
                order.getId(),
                order.getOrderNumber(),
                customerName,
                customerPhone,
                street,
                addressLine2,
                city,
                postalCode,
                district,
                country,
                order.getDeliveryStatus(),
                order.getStatus(),
                order.getTotalAmount(),
                formattedAddress
        );
    }

    private String buildFormattedAddress(String street, String addressLine2, String city,
                                         String postalCode, String district, String country) {
        StringBuilder sb = new StringBuilder();
        if (street != null) sb.append(street);
        if (addressLine2 != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(addressLine2);
        }
        if (city != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(city);
        }
        if (postalCode != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(postalCode);
        }
        if (district != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(district);
        }
        if (country != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(country);
        }
        return sb.toString();
    }

    private TripResponse toTripResponse(Trip trip) {
        TripResponse response = tripMapper.toResponse(trip);

        List<Order> orders = orderRepository.findByTripId(trip.getId());
        List<Expense> expenses = expenseRepository.findByTripId(trip.getId());

        response.setOrderCount(orders.size());

        int paidCount = 0;
        int partialCount = 0;
        int unpaidCount = 0;
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;

        for (Order order : orders) {
            expected = expected.add(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
            collected = collected.add(order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO);

            if (order.getPaidAmount() != null && order.getTotalAmount() != null) {
                if (order.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
                    unpaidCount++;
                } else if (order.getPaidAmount().compareTo(order.getTotalAmount()) >= 0) {
                    paidCount++;
                } else {
                    partialCount++;
                }
            } else {
                unpaidCount++;
            }
        }

        response.setPaidOrderCount(paidCount);
        response.setPartialOrderCount(partialCount);
        response.setUnpaidOrderCount(unpaidCount);

        response.setExpectedAmount(expected);
        response.setCollectedAmount(collected);
        response.setOutstandingAmount(expected.subtract(collected));

        BigDecimal totalExpenses = BigDecimal.ZERO;
        BigDecimal petrolCost = BigDecimal.ZERO;
        BigDecimal parkingCost = BigDecimal.ZERO;
        BigDecimal tollCost = BigDecimal.ZERO;
        BigDecimal travelCost = BigDecimal.ZERO;
        BigDecimal maintenanceCost = BigDecimal.ZERO;
        BigDecimal otherCost = BigDecimal.ZERO;

        for (Expense expense : expenses) {
            if (expense.getAmount() != null) {
                totalExpenses = totalExpenses.add(expense.getAmount());
            }

            if (expense.getCategory() != null) {
                switch (expense.getCategory()) {
                    case PETROL -> petrolCost = petrolCost.add(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO);
                    case PARKING -> parkingCost = parkingCost.add(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO);
                    case TOLL -> tollCost = tollCost.add(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO);
                    case TRAVEL -> travelCost = travelCost.add(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO);
                    case MAINTENANCE -> maintenanceCost = maintenanceCost.add(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO);
                    case OTHER, MILEAGE -> otherCost = otherCost.add(expense.getAmount() != null ? expense.getAmount() : BigDecimal.ZERO);
                }
            }
        }

        response.setTotalExpenses(totalExpenses);
        response.setPetrolCost(petrolCost);
        response.setParkingCost(parkingCost);
        response.setTollCost(tollCost);
        response.setTravelCost(travelCost);
        response.setMaintenanceCost(maintenanceCost);
        response.setOtherCost(otherCost);

        response.setNetAmount(collected.subtract(totalExpenses));

        response.setOrderIds(orders.stream().map(Order::getId).collect(Collectors.toList()));
        response.setExpenses(expenses.stream().map(expenseMapper::toResponse).collect(Collectors.toList()));

        return response;
    }
}
