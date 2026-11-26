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

/** How trips are shown (response with payment / delivery counts, map markers) and who may see one.
 * Shared by TripService (changes) and TripQueryService (lookups). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripViews {

    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final DriverRepository driverRepository;
    private final TripMapper tripMapper;
    private final ExpenseMapper expenseMapper;
    private final SecurityService securityService;

    private static final Set<OrderStatus> EXCLUDED_FROM_SETTLEMENT = Set.of(
            OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.RETURN_EXCHANGE);

    public Driver resolveCurrentDriver() {
        UUID currentUserId = securityService.getCurrentUserId();
        return driverRepository.findByUserId(currentUserId)
                .orElseThrow(() -> new AccessDeniedException("Only drivers can access their own trip data"));
    }

    public void requireDriverOwnsTripForNonAdmin(Trip trip) {
        if (securityService.isAdmin()) {
            return;
        }
        Driver currentDriver = resolveCurrentDriver();
        if (trip.getDriver() == null || !trip.getDriver().getId().equals(currentDriver.getId())) {
            throw new AccessDeniedException("This trip is not assigned to you");
        }
    }

    public List<TripOrderMarkerDto> buildOrderMarkers(Trip trip) {
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

    public TripResponse toTripResponse(Trip trip) {
        TripResponse response = tripMapper.toResponse(trip);

        List<Order> orders = orderRepository.findByTripId(trip.getId());

        response.setOrderCount(orders.size());

        int paidCount = 0;
        int partialCount = 0;
        int unpaidCount = 0;
        int deliveredCount = 0;
        int failedCount = 0;
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;

        for (Order order : orders) {
            if (order.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
                deliveredCount++;
            } else if (order.getDeliveryStatus() == DeliveryStatus.FAILED_PERMANENT) {
                failedCount++;
            }
            boolean saleable = !EXCLUDED_FROM_SETTLEMENT.contains(order.getStatus())
                    && order.getDeliveryStatus() != DeliveryStatus.FAILED_PERMANENT;
            if (!saleable) {
                continue;
            }
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
        response.setDeliveredOrderCount(deliveredCount);
        response.setFailedOrderCount(failedCount);

        response.setExpectedAmount(expected);
        response.setCollectedAmount(collected);
        response.setOutstandingAmount(expected.subtract(collected));

        List<Expense> expenseList = expenseRepository.findByTripIdWithRelations(trip.getId());

        BigDecimal totalExpenses = BigDecimal.ZERO;
        for (Expense expense : expenseList) {
            if (expense.getStatus() == ExpenseStatus.APPROVED && expense.getAmount() != null) {
                totalExpenses = totalExpenses.add(expense.getAmount());
            }
        }

        response.setTotalExpenses(totalExpenses);
        response.setNetAmount(collected.subtract(totalExpenses));

        response.setOrderIds(orders.stream().map(Order::getId).collect(Collectors.toList()));
        response.setExpenses(expenseList.stream().map(expenseMapper::toResponse).collect(Collectors.toList()));

        return response;
    }

}
