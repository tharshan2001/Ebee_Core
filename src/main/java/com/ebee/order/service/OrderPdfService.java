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
    private final com.ebee.tax.service.TaxService taxService;

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = formatAddress(a.getStreet(), a.getAddressLine2(), a.getCity(),
                    a.getPostalCode(), a.getDistrict(), a.getCountry());
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
                    lineName(item),
                    lineCode(item),
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

        Map<String, Object> vars = toMap(data);
        var settings = taxService.getSettings();
        if (settings.isVatRegistered() || order.getTaxTotal().signum() > 0) {
            // Tax invoice: seller and buyer VAT numbers, net amount and VAT shown separately.
            vars.put("title", "TAX INVOICE");
            vars.put("vatRegistrationNumber", settings.getVatRegistrationNumber());
            vars.put("customerVatNumber", order.getCustomer() != null ? order.getCustomer().getVatNumber() : null);
            vars.put("taxTotal", order.getTaxTotal());
            vars.put("netTotal", order.getTotalAmount().subtract(order.getTaxTotal()));
        }
        return printTemplateService.renderToPdf("invoice", vars);
    }

    @Transactional(readOnly = true)
    public byte[] generateReceiptPdf(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        String address = null;
        if (order.getCustomer() != null && order.getCustomer().getAddress() != null) {
            var a = order.getCustomer().getAddress();
            address = formatAddress(a.getStreet(), a.getAddressLine2(), a.getCity(),
                    a.getPostalCode(), a.getDistrict(), a.getCountry());
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
                    lineName(item),
                    lineCode(item),
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
            address = formatAddress(a.getStreet(), a.getAddressLine2(), a.getCity(),
                    a.getPostalCode(), a.getDistrict(), a.getCountry());
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

    /** Combo lines have no product; name them after the combo. */
    private static String lineName(com.ebee.order.entity.OrderItem item) {
        if (item.getCombo() != null) {
            return item.getCombo().getName();
        }
        String name = item.getProduct() != null ? item.getProduct().getName() : "Item";
        return item.getSalesUnitName() != null ? name + " (" + item.getSalesUnitName() + ")" : name;
    }

    private static String lineCode(com.ebee.order.entity.OrderItem item) {
        if (item.getCombo() != null) {
            return "COMBO";
        }
        return item.getProduct() != null ? item.getProduct().getItemCode() : "";
    }

    private String formatAddress(String line1, String line2, String city, String postalCode,
                                  com.ebee.common.enums.District district, String country) {
        // Only the district is required, so any other part may be missing.
        String town = java.util.stream.Stream.of(city, postalCode)
                .filter(v -> v != null && !v.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        return java.util.stream.Stream.of(line1, line2, town, district != null ? district.getDisplayName() : null, country)
                .filter(v -> v != null && !v.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private Map<String, Object> toMap(Object record) {
        Map<String, Object> map = new LinkedHashMap<>();
        try {
            for (var method : record.getClass().getRecordComponents()) {
                map.put(method.getName(), method.getAccessor().invoke(record));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to convert record to map", e);
        }
        return map;
    }
}
