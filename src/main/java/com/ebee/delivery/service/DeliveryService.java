package com.ebee.delivery.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.delivery.dto.DeliveryStatusUpdateRequest;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.delivery.repository.DeliveryRepository;
import com.ebee.driver.entity.Driver;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final DriverRepository driverRepository;
    private final OrderMapper orderMapper;
    private final SecurityService securityService;

    @Transactional
    public void initializeDelivery(Order order) {
        if (order.getDeliveryStatus() == null) {
            order.setDeliveryStatus(DeliveryStatus.PENDING);
        }
    }

    @Transactional
    public OrderResponse assignDelivery(UUID orderId, UUID driverId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryStatus() != DeliveryStatus.PENDING) {
            throw new IllegalArgumentException("Delivery can only be assigned when status is PENDING");
        }

        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", driverId));

        if (driver.getRole() != RoleType.DRIVER) {
            throw new IllegalArgumentException("User is not a driver");
        }

        Driver driverProfile = driverRepository.findByUserId(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver", "userId", driverId));

        if (driverProfile.getAvailability() != DriverAvailability.AVAILABLE) {
            throw new IllegalArgumentException("Driver is not available. Current status: " + driverProfile.getAvailability());
        }

        driverProfile.setAvailability(DriverAvailability.ON_TRIP);
        driverRepository.save(driverProfile);

        order.setDeliveryAssignedTo(driver);
        order.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        Order savedOrder = orderRepository.save(order);

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse updateDeliveryStatus(UUID orderId, DeliveryStatusUpdateRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        DeliveryStatus currentStatus = order.getDeliveryStatus();
        DeliveryStatus newStatus = request.getStatus();

        if (currentStatus == null) {
            throw new IllegalArgumentException("Delivery has not been initialized for this order");
        }

        if (currentStatus == DeliveryStatus.DELIVERED || currentStatus == DeliveryStatus.FAILED) {
            throw new IllegalArgumentException("Cannot update status of a " + currentStatus.name().toLowerCase() + " delivery");
        }

        order.setDeliveryStatus(newStatus);

        if (newStatus == DeliveryStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            order.setStatus(OrderStatus.DELIVERED);

            if (order.getDeliveryAssignedTo() != null) {
                driverRepository.findByUserId(order.getDeliveryAssignedTo().getId())
                        .ifPresent(d -> {
                            d.setAvailability(DriverAvailability.AVAILABLE);
                            driverRepository.save(d);
                        });
            }
        }

        Order savedOrder = orderRepository.save(order);
        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getDeliveries(DeliveryStatus status, String district, UUID tripId) {
        List<Order> orders;
        if (tripId != null) {
            orders = deliveryRepository.findByTripId(tripId);
        } else if (district != null && status != null) {
            orders = orderRepository.findAll().stream()
                    .filter(o -> district.equals(o.getDistrict()) && status == o.getDeliveryStatus())
                    .toList();
        } else if (status != null) {
            orders = deliveryRepository.findByDeliveryStatus(status);
        } else if (district != null) {
            orders = deliveryRepository.findByDistrict(district);
        } else {
            orders = orderRepository.findAll().stream()
                    .filter(o -> o.getDeliveryStatus() != null)
                    .toList();
        }
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyDeliveries() {
        UUID userId = securityService.getCurrentUserId();
        List<Order> orders = deliveryRepository.findByDeliveryAssignedToId(userId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    public OrderResponse getDeliveryDetail(UUID orderId) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getDeliveryStatus() == null) {
            throw new ResourceNotFoundException("Delivery", "orderId", orderId);
        }

        return orderMapper.toResponse(order);
    }
}
