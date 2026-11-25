package com.ebee.order.service;

import com.ebee.order.entity.OrderItemLot;
import com.ebee.order.repository.OrderItemLotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Recall lookup: which orders (and customers) received a given lot. */
@Service
public class LotTraceService {

    public record LotSale(UUID orderId, String orderNumber, LocalDateTime orderDate, UUID customerId,
                          String customerName, String customerPhone, String lotNumber, LocalDate expiryDate,
                          int quantity, int returnedQuantity) {
    }

    private final OrderItemLotRepository orderItemLotRepository;

    public LotTraceService(OrderItemLotRepository orderItemLotRepository) {
        this.orderItemLotRepository = orderItemLotRepository;
    }

    @Transactional(readOnly = true)
    public List<LotSale> salesOfLot(UUID productId, String lotNumber) {
        return orderItemLotRepository.findSalesOfLot(productId, lotNumber).stream().map(this::toSale).toList();
    }

    private LotSale toSale(OrderItemLot l) {
        var order = l.getOrderItem().getOrder();
        var c = order.getCustomer();
        return new LotSale(order.getId(), order.getOrderNumber(), order.getCreatedAt(),
                c != null ? c.getId() : null, c != null ? (c.getFirstName() + " " + c.getLastName()).trim() : null,
                c != null ? c.getPhoneNumber() : null, l.getLotNumber(), l.getExpiryDate(),
                l.getQuantity(), l.getReturnedQuantity());
    }
}
