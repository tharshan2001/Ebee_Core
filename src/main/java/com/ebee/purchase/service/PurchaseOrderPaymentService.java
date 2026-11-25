package com.ebee.purchase.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.common.security.SecurityService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PurchaseOrderPaymentService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final LedgerService ledgerService;
    private final SecurityService securityService;
    private final com.ebee.finance.service.MoneyAccountService moneyAccountService;

    public PurchaseOrderPaymentService(PurchaseOrderRepository purchaseOrderRepository,
                                       SupplierRepository supplierRepository,
                                       LedgerService ledgerService,
                                       SecurityService securityService,
                                       com.ebee.finance.service.MoneyAccountService moneyAccountService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
        this.moneyAccountService = moneyAccountService;
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
        // Money paid when the order is placed is a supplier payment like any other: on the ledger
        // now (the ledger goes negative = prepaid) and netted when the stock is invoiced on receipt.
        if (order.getPaidAmount() != null && order.getPaidAmount().signum() > 0) {
            subtractSupplierOutstandingWithLedger(order.getSupplier(), order.getPaidAmount(), order.getId(),
                    "Paid at order: PO " + order.getPoNumber() + " via " + paymentMethod,
                    "PO Payment: " + order.getPoNumber());
        }
        moneyAccountService.recordMovement(com.ebee.finance.entity.AccountTransaction.Direction.OUT, order.getPaidAmount(), paymentMethod.name(), null,
                com.ebee.finance.entity.AccountTransaction.Type.SUPPLIER_PAYMENT, "PURCHASE_ORDER", order.getId(), "Paid at order: PO " + order.getPoNumber());
    }

    /** A PO placed without a payment method becomes an unpaid credit order when stock first arrives. */
    public void initPaymentOnReceipt(PurchaseOrder order) {
        if (order.getPaymentMethod() != null) {
            return;
        }
        BigDecimal paid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        order.setPaymentMethod(PurchasePaymentMethod.CREDIT);
        order.setPaidAmount(paid);
        order.setOutstandingBalance(order.getTotalAmount().subtract(paid).max(BigDecimal.ZERO));
        order.setPaymentStatus(order.getOutstandingBalance().signum() == 0 ? PurchasePaymentStatus.PAID
                : paid.signum() > 0 ? PurchasePaymentStatus.PARTIALLY_PAID : PurchasePaymentStatus.UNPAID);
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

        subtractSupplierOutstandingWithLedger(
                order.getSupplier(), request.amount(), order.getId(),
                "Payment for PO " + order.getPoNumber() + " via " + request.paymentMethod(),
                "PO Payment: " + order.getPoNumber());
        moneyAccountService.recordMovement(com.ebee.finance.entity.AccountTransaction.Direction.OUT, request.amount(), request.paymentMethod().name(),
                request.accountId(), com.ebee.finance.entity.AccountTransaction.Type.SUPPLIER_PAYMENT, "PURCHASE_ORDER", order.getId(),
                "Payment for PO " + order.getPoNumber());

        return purchaseOrderRepository.save(order);
    }




    public Supplier addSupplierOutstandingWithLedger(Supplier supplier, BigDecimal amount, UUID purchaseOrderId,
                                                     String poNumber, String description) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordSupplierInvoice(
                    supplier.getId(),
                    purchaseOrderId,
                    amount,
                    description,
                    LocalDate.now(),
                    "PO Invoice: " + poNumber,
                    securityService.getCurrentUserId()
            );
        }
        return syncSupplierBalance(supplier);
    }

    public Supplier subtractSupplierOutstandingWithLedger(Supplier supplier, BigDecimal amount, UUID purchaseOrderId,
                                                         String description, String reference) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordSupplierPayment(
                    supplier.getId(),
                    purchaseOrderId,
                    amount,
                    description,
                    LocalDate.now(),
                    reference,
                    securityService.getCurrentUserId()
            );
        }
        return syncSupplierBalance(supplier);
    }

    public Supplier subtractSupplierOutstandingForReturn(Supplier supplier, BigDecimal amount, UUID purchaseOrderId,
                                                        String poNumber, String description) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.recordSupplierExpense(
                    supplier.getId(),
                    purchaseOrderId,
                    amount,
                    description,
                    LocalDate.now(),
                    "PO Return: " + poNumber,
                    securityService.getCurrentUserId()
            );
        }
        return syncSupplierBalance(supplier);
    }

    /**
     * The supplier's cached outstanding balance is what the ledger says we owe (never negative:
     * a prepaid supplier shows 0 here and a negative ledger balance). It used to be added to and
     * clamped separately, so it drifted from the ledger whenever a supplier was prepaid.
     */
    private Supplier syncSupplierBalance(Supplier supplier) {
        supplier.setOutstandingBalance(ledgerService.getSupplierBalance(supplier.getId()).max(BigDecimal.ZERO));
        return supplierRepository.save(supplier);
    }
}
