package com.ebee.order.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.PrintTemplateService;
import com.ebee.order.dto.InvoiceData;
import com.ebee.order.dto.ReceiptData;
import com.ebee.order.dto.ShippingLabelData;
import com.ebee.order.entity.Order;
import com.ebee.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPdfService {

    private final OrderRepository orderRepository;
    private final PrintTemplateService printTemplateService;

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = a.getStreet() + ", " + a.getCity() + ", " + a.getDistrict().getDisplayName() + " - " + a.getPostalCode();
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        var lineItems = new java.util.ArrayList<InvoiceData.LineItem>();
        for (var item : order.getItems()) {
            BigDecimal itemSubtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal discount = BigDecimal.ZERO;
            if (item.getOriginalUnitPrice() != null && item.getOriginalUnitPrice().compareTo(item.getUnitPrice()) > 0) {
                discount = item.getOriginalUnitPrice().subtract(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
            }
            subtotal = subtotal.add(itemSubtotal);
            discountTotal = discountTotal.add(discount);
            lineItems.add(new InvoiceData.LineItem(
                    item.getProduct().getName(),
                    item.getProduct().getItemCode(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    itemSubtotal,
                    discount.compareTo(BigDecimal.ZERO) > 0 ? discount.toString() : null
            ));
        }

        var data = new InvoiceData(
                "INVOICE",
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getCustomer() != null ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName() : null,
                order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : null,
                address,
                order.getAgent() != null ? order.getAgent().getUser().getFirstName() + " " + order.getAgent().getUser().getLastName() : null,
                order.getAgent() != null ? order.getAgent().getAgentCode() : null,
                lineItems,
                subtotal,
                discountTotal,
                order.getTotalAmount(),
                order.getNotes()
        );

        return printTemplateService.renderToPdf("invoice", toMap(data));
    }

    @Transactional(readOnly = true)
    public byte[] generateReceiptPdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = a.getStreet() + ", " + a.getCity() + ", " + a.getDistrict().getDisplayName() + " - " + a.getPostalCode();
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        var lineItems = new java.util.ArrayList<InvoiceData.LineItem>();
        for (var item : order.getItems()) {
            BigDecimal itemSubtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal discount = BigDecimal.ZERO;
            if (item.getOriginalUnitPrice() != null && item.getOriginalUnitPrice().compareTo(item.getUnitPrice()) > 0) {
                discount = item.getOriginalUnitPrice().subtract(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity()));
            }
            subtotal = subtotal.add(itemSubtotal);
            discountTotal = discountTotal.add(discount);
            lineItems.add(new InvoiceData.LineItem(
                    item.getProduct().getName(),
                    item.getProduct().getItemCode(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    itemSubtotal,
                    discount.compareTo(BigDecimal.ZERO) > 0 ? discount.toString() : null
            ));
        }

        var data = new ReceiptData(
                "RECEIPT",
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getPaidAt() != null ? order.getPaidAt() : java.time.LocalDateTime.now(),
                order.getCustomer() != null ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName() : null,
                order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : null,
                address,
                order.getAgent() != null ? order.getAgent().getUser().getFirstName() + " " + order.getAgent().getUser().getLastName() : null,
                order.getAgent() != null ? order.getAgent().getAgentCode() : null,
                order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "Cash",
                order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "PAID",
                order.getOutstandingBalance(),
                lineItems,
                subtotal,
                discountTotal,
                order.getTotalAmount(),
                order.getNotes()
        );

        return printTemplateService.renderToPdf("receipt", toMap(data));
    }

    @Transactional(readOnly = true)
    public byte[] generateShippingLabelPdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = a.getStreet() + ", " + a.getCity() + ", " + a.getDistrict().getDisplayName() + " - " + a.getPostalCode();
        }

        var data = new ShippingLabelData(
                order.getOrderNumber(),
                order.getCustomer() != null ? order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName() : null,
                order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : null,
                address,
                order.getItems().size(),
                order.getDeliveryNotes()
        );

        return printTemplateService.renderToPdf("shipping-label", toMap(data));
    }

    private Map<String, Object> toMap(Object record) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            for (var method : record.getClass().getRecordComponents()) {
                map.put(method.getName(), method.getAccessor().invoke(record));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert record to map", e);
        }
        return map;
    }
}
