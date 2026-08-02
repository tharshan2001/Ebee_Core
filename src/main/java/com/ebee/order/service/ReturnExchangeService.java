package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.order.dto.InitiateReturnExchangeRequest;
import com.ebee.order.dto.ReturnExchangeResponse;
import com.ebee.order.dto.ReviewReturnExchangeRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.entity.ReturnExchangeRequest;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.ReturnExchangeResolution;
import com.ebee.order.enums.ReturnExchangeStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.ReturnExchangeRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnExchangeService {

    private final ReturnExchangeRepository returnExchangeRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final LedgerService ledgerService;

    @Transactional
    public ReturnExchangeResponse initiateReturnExchange(UUID orderId, InitiateReturnExchangeRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Can only initiate return exchange for DELIVERED orders");
        }

        if (returnExchangeRepository.existsByOrderId(orderId)) {
            throw new IllegalArgumentException("Return exchange already requested for this order");
        }

        order.setStatus(OrderStatus.RETURN_EXCHANGE);
        orderRepository.save(order);

        ReturnExchangeRequest returnRequest = ReturnExchangeRequest.builder()
                .order(order)
                .requestedAmount(request.requestedAmount())
                .reason(request.reason())
                .status(ReturnExchangeStatus.PENDING_REVIEW)
                .build();

        ReturnExchangeRequest saved = returnExchangeRepository.save(returnRequest);
        log.info("Return exchange initiated for order {}: requestedAmount={}", order.getOrderNumber(), request.requestedAmount());

        return toResponse(saved);
    }

    @Transactional
    public ReturnExchangeResponse reviewReturnExchange(UUID returnExchangeId, ReviewReturnExchangeRequest request, UUID reviewedByUserId) {
        ReturnExchangeRequest returnRequest = returnExchangeRepository.findById(returnExchangeId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnExchangeRequest", "id", returnExchangeId));

        if (returnRequest.getStatus() != ReturnExchangeStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("Return exchange request is not pending review");
        }

        User reviewedBy = userRepository.findById(reviewedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewedByUserId));

        Order order = returnRequest.getOrder();
        Customer customer = order.getCustomer();

        returnRequest.setApprovedAmount(request.approvedAmount());
        returnRequest.setResolutionType(request.resolutionType());
        returnRequest.setNotes(request.notes());
        returnRequest.setReviewedBy(reviewedBy);
        returnRequest.setReviewedAt(LocalDateTime.now());

        if (request.resolutionType() == ReturnExchangeResolution.ADVANCE) {
            returnRequest.setStatus(ReturnExchangeStatus.APPROVED);
            order.setStatus(OrderStatus.RETURN_EXCHANGE);

            List<UUID> productIds = order.getItems().stream()
                    .map(item -> item.getProduct().getId())
                    .toList();
            List<Integer> quantities = order.getItems().stream()
                    .map(OrderItem::getQuantity)
                    .toList();
            List<String> notes = order.getItems().stream()
                    .map(item -> "Return exchange for Order " + order.getOrderNumber())
                    .toList();
            inventoryService.recordOrderStocks(productIds, quantities, StockMovementType.RETURN, notes);

            BigDecimal newAdvanceBalance = customer.getAdvanceBalance().add(request.approvedAmount());
            customer.setAdvanceBalance(newAdvanceBalance);
            customerRepository.save(customer);

            ledgerService.recordReturnExchangeCredit(
                    customer.getId(),
                    order.getId(),
                    request.approvedAmount(),
                    "Return exchange credit for Order " + order.getOrderNumber(),
                    reviewedByUserId
            );

            log.info("Return exchange APPROVED with ADVANCE for order {}: approvedAmount={}", order.getOrderNumber(), request.approvedAmount());
        } else {
            returnRequest.setStatus(ReturnExchangeStatus.REJECTED);
            order.setStatus(OrderStatus.DELIVERED);
            log.info("Return exchange REJECTED for order {}: resolution={}", order.getOrderNumber(), request.resolutionType());
        }

        ReturnExchangeRequest saved = returnExchangeRepository.save(returnRequest);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReturnExchangeResponse> getPendingReviews() {
        return returnExchangeRepository.findByStatusOrderByCreatedAtDesc(ReturnExchangeStatus.PENDING_REVIEW)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReturnExchangeResponse getByOrderId(UUID orderId) {
        ReturnExchangeRequest request = returnExchangeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("ReturnExchangeRequest", "orderId", orderId));
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return returnExchangeRepository.countByStatus(ReturnExchangeStatus.PENDING_REVIEW);
    }

    private ReturnExchangeResponse toResponse(ReturnExchangeRequest request) {
        Order order = request.getOrder();
        Customer customer = order.getCustomer();

        String reviewedByName = null;
        if (request.getReviewedBy() != null) {
            User reviewedByUser = request.getReviewedBy();
            reviewedByName = reviewedByUser.getFirstName() + " " + reviewedByUser.getLastName();
        }

        String customerName = customer != null ? customer.getFirstName() + " " + customer.getLastName() : "N/A";

        return new ReturnExchangeResponse(
                request.getId(),
                order.getId(),
                order.getOrderNumber(),
                customer != null ? customer.getId() : null,
                customerName,
                request.getRequestedAmount(),
                request.getApprovedAmount(),
                request.getStatus(),
                request.getResolutionType(),
                request.getReason(),
                request.getNotes(),
                reviewedByName,
                request.getReviewedAt(),
                request.getCreatedAt()
        );
    }
}
