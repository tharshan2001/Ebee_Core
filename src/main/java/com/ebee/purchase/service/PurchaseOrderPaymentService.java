package com.ebee.purchase.service;

import com.ebee.purchase.dto.RecordPaymentRequest;
import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.enums.PurchasePaymentMethod;
import com.ebee.purchase.enums.PurchasePaymentStatus;
import com.ebee.purchase.repository.PurchaseOrderRepository;
import com.ebee.supplier.entity.Supplier;
import com.ebee.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PurchaseOrderPaymentService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;

    public PurchaseOrderPaymentService(PurchaseOrderRepository purchaseOrderRepository,
                                       SupplierRepository supplierRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
    }

    public void initPayment(PurchaseOrder order, PurchasePaymentMethod paymentMethod, BigDecimal paidAmount) {
        BigDecimal totalAmount = order.getTotalAmount();

        if (paymentMethod == PurchasePaymentMethod.CREDIT) {
            if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                order.setPaidAmount(paidAmount);
                if (paidAmount.compareTo(totalAmount) >= 0) {
                    order.setPaymentStatus(PurchasePaymentStatus.PAID);
                    order.setOutstandingBalance(BigDecimal.ZERO);
                    order.setPaidAt(LocalDateTime.now());
                } else {
                    order.setPaymentStatus(PurchasePaymentStatus.PARTIALLY_PAID);
                    order.setOutstandingBalance(totalAmount.subtract(paidAmount));
                    order.setPaidAt(LocalDateTime.now());
                }
            } else {
                order.setPaymentStatus(PurchasePaymentStatus.UNPAID);
                order.setOutstandingBalance(totalAmount);
            }
        } else {
            order.setPaidAmount(totalAmount);
            order.setPaymentStatus(PurchasePaymentStatus.PAID);
            order.setOutstandingBalance(BigDecimal.ZERO);
            order.setPaidAt(LocalDateTime.now());
        }

        order.setPaymentMethod(paymentMethod);
    }

    public void initPaymentOnReceipt(PurchaseOrder order) {
        if (order.getPaymentStatus() != PurchasePaymentStatus.UNPAID) {
            return;
        }

        order.setPaymentMethod(PurchasePaymentMethod.CREDIT);
        order.setPaymentStatus(PurchasePaymentStatus.UNPAID);
        order.setOutstandingBalance(order.getTotalAmount());

        addSupplierOutstanding(order.getSupplier(), order.getOutstandingBalance());
    }

    public PurchaseOrder recordPayment(UUID orderId, RecordPaymentRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItemsLocked(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + orderId));

        if (order.getPaymentStatus() == PurchasePaymentStatus.PAID) {
            throw new IllegalArgumentException("Purchase order is already fully paid");
        }

        if (request.amount().compareTo(order.getOutstandingBalance()) > 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + request.amount() + " exceeds outstanding balance "
                            + order.getOutstandingBalance());
        }

        BigDecimal newPaidAmount = order.getPaidAmount().add(request.amount());
        BigDecimal newOutstanding = order.getOutstandingBalance().subtract(request.amount());

        order.setPaidAmount(newPaidAmount);
        order.setOutstandingBalance(newOutstanding);
        order.setPaymentMethod(request.paymentMethod());

        if (newOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            order.setPaymentStatus(PurchasePaymentStatus.PAID);
            order.setOutstandingBalance(BigDecimal.ZERO);
            order.setPaidAt(LocalDateTime.now());
        } else {
            order.setPaymentStatus(PurchasePaymentStatus.PARTIALLY_PAID);
        }

        subtractSupplierOutstanding(order.getSupplier(), request.amount());

        return purchaseOrderRepository.save(order);
    }

    public void releaseSupplierOutstanding(PurchaseOrder order) {
        if (order.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
            subtractSupplierOutstanding(order.getSupplier(), order.getOutstandingBalance());
            order.setOutstandingBalance(BigDecimal.ZERO);
        }
    }

    public Supplier addSupplierOutstanding(Supplier supplier, BigDecimal amount) {
        supplier.setOutstandingBalance(supplier.getOutstandingBalance().add(amount));
        return supplierRepository.save(supplier);
    }

    public Supplier subtractSupplierOutstanding(Supplier supplier, BigDecimal amount) {
        BigDecimal newBalance = supplier.getOutstandingBalance().subtract(amount);
        supplier.setOutstandingBalance(newBalance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newBalance);
        return supplierRepository.save(supplier);
    }
}
