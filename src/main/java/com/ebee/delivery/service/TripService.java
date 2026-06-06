package com.ebee.delivery.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.delivery.dto.TripRequest;
import com.ebee.delivery.dto.TripResponse;
import com.ebee.delivery.entity.DeliveryTrip;
import com.ebee.delivery.enums.TripStatus;
import com.ebee.delivery.repository.TripRepository;
import com.ebee.driver.enums.DriverAvailability;
import com.ebee.driver.repository.DriverRepository;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public TripResponse createTrip(TripRequest request) {
        User driver = null;
        if (request.getDriverId() != null) {
            driver = userRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getDriverId()));
            if (driver.getRole() != RoleType.DRIVER) {
                throw new IllegalArgumentException("User is not a driver");
            }

            com.ebee.driver.entity.Driver driverProfile = driverRepository.findByUserId(request.getDriverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", request.getDriverId()));

            if (driverProfile.getAvailability() != DriverAvailability.AVAILABLE) {
                throw new IllegalArgumentException(
                        "Driver is not available. Current status: " + driverProfile.getAvailability());
            }
        }

        DeliveryTrip trip = DeliveryTrip.builder()
                .tripDate(request.getTripDate())
                .district(request.getDistrict())
                .assignedDriver(driver)
                .notes(request.getNotes())
                .build();

        DeliveryTrip savedTrip = tripRepository.save(trip);
        return toTripResponse(savedTrip);
    }

    @Transactional
    public TripResponse updateTripStatus(UUID id, TripStatus status) {
        DeliveryTrip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryTrip", "id", id));

        if (trip.getStatus() == TripStatus.COMPLETED || trip.getStatus() == TripStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot update status of a " + trip.getStatus().name().toLowerCase() + " trip");
        }

        trip.setStatus(status);
        DeliveryTrip savedTrip = tripRepository.save(trip);
        return toTripResponse(savedTrip);
    }

    @Transactional
    public TripResponse assignDriver(UUID id, UUID driverId) {
        DeliveryTrip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryTrip", "id", id));

        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", driverId));

        if (driver.getRole() != RoleType.DRIVER) {
            throw new IllegalArgumentException("User is not a driver");
        }

        com.ebee.driver.entity.Driver driverProfile = driverRepository.findByUserId(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", driverId));

        if (driverProfile.getAvailability() != DriverAvailability.AVAILABLE) {
            throw new IllegalArgumentException(
                    "Driver is not available. Current status: " + driverProfile.getAvailability());
        }

        trip.setAssignedDriver(driver);
        DeliveryTrip savedTrip = tripRepository.save(trip);
        return toTripResponse(savedTrip);
    }

    @Transactional
    public TripResponse assignOrderToTrip(UUID tripId, UUID orderId) {
        DeliveryTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryTrip", "id", tripId));

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryStatus() == null) {
            throw new IllegalArgumentException("Order has no delivery initialized. Ship the order first.");
        }

        if (order.getTrip() != null) {
            throw new IllegalArgumentException("Order is already assigned to a trip");
        }

        if (!trip.getDistrict().equals(order.getDistrict())) {
            throw new IllegalArgumentException(
                    "Order district (" + order.getDistrict() + ") does not match trip district (" + trip.getDistrict() + ")");
        }

        order.setTrip(trip);
        orderRepository.save(order);

        return toTripResponse(trip);
    }

    @Transactional
    public TripResponse removeOrderFromTrip(UUID tripId, UUID orderId) {
        DeliveryTrip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryTrip", "id", tripId));

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getTrip() == null || !order.getTrip().getId().equals(tripId)) {
            throw new IllegalArgumentException("Order is not assigned to this trip");
        }

        order.setTrip(null);
        orderRepository.save(order);

        return toTripResponse(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> getTrips(TripStatus status, String district) {
        List<DeliveryTrip> trips;
        if (status != null && district != null) {
            trips = tripRepository.findAll().stream()
                    .filter(t -> status == t.getStatus() && district.equals(t.getDistrict()))
                    .toList();
        } else if (status != null) {
            trips = tripRepository.findByStatus(status);
        } else if (district != null) {
            trips = tripRepository.findByDistrict(district);
        } else {
            trips = tripRepository.findAll();
        }
        return trips.stream().map(this::toTripResponse).toList();
    }

    @Transactional(readOnly = true)
    public TripResponse getTripDetail(UUID id) {
        DeliveryTrip trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryTrip", "id", id));
        return toTripResponse(trip);
    }

    private TripResponse toTripResponse(DeliveryTrip trip) {
        TripResponse response = new TripResponse();
        response.setId(trip.getId());
        response.setTripDate(trip.getTripDate());
        response.setDistrict(trip.getDistrict());
        response.setStatus(trip.getStatus());
        response.setNotes(trip.getNotes());
        response.setCreatedAt(trip.getCreatedAt());
        response.setUpdatedAt(trip.getUpdatedAt());

        if (trip.getAssignedDriver() != null) {
            response.setDriverId(trip.getAssignedDriver().getId());
            response.setDriverName(trip.getAssignedDriver().getFirstName() + " " + trip.getAssignedDriver().getLastName());
        }

        List<Order> orders = orderRepository.findByTripIdWithDetails(trip.getId());
        response.setOrders(orderMapper.toResponseList(orders));

        return response;
    }
}
