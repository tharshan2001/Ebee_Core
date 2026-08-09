package com.ebee.supplier.service;

import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.enums.PurchasePaymentMethod;
import com.ebee.purchase.enums.PurchasePaymentStatus;
import com.ebee.purchase.repository.PurchaseOrderRepository;
import com.ebee.supplier.dto.*;
import com.ebee.supplier.entity.Supplier;
import com.ebee.supplier.mapper.SupplierMapper;
import com.ebee.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierMapper supplierMapper;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final LedgerService ledgerService;
    private final SecurityService securityService;

    public SupplierServiceImpl(SupplierRepository supplierRepository,
                               SupplierMapper supplierMapper,
                               PurchaseOrderRepository purchaseOrderRepository,
                               SupplierLedgerRepository supplierLedgerRepository,
                               LedgerService ledgerService,
                               SecurityService securityService) {
        this.supplierRepository = supplierRepository;
        this.supplierMapper = supplierMapper;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierLedgerRepository = supplierLedgerRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
    }

    @Override
    public SupplierResponse createSupplier(SupplierRequest request) {
        Supplier supplier = supplierMapper.toEntity(request);
        Supplier saved = supplierRepository.save(supplier);
        return supplierMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));
        return supplierMapper.toResponse(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAllSuppliers(String search, Pageable pageable) {
        Page<Supplier> suppliers;
        if (search != null && !search.isBlank()) {
            suppliers = supplierRepository.findByNameContainingIgnoreCase(search, pageable);
        } else {
            suppliers = supplierRepository.findAll(pageable);
        }
        return suppliers.map(supplierMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> searchSuppliers(String search) {
        return supplierRepository.findBySearchTerm(search).stream()
                .map(supplierMapper::toResponse)
                .toList();
    }

    @Override
    public SupplierResponse updateSupplier(UUID id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));
        supplierMapper.updateEntity(request, supplier);
        Supplier saved = supplierRepository.save(supplier);
        return supplierMapper.toResponse(saved);
    }

    @Override
    public void deactivateSupplier(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", id));
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> getSuppliersWithDues() {
        return supplierRepository.findAll(Sort.by(Sort.Direction.DESC, "outstandingBalance")).stream()
                .filter(s -> s.getOutstandingBalance() != null && s.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0)
                .map(supplierMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierOutstandingOrdersResponse getSupplierOutstandingOrders(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        List<PurchaseOrder> unpaidOrders = purchaseOrderRepository.findBySupplierIdAndPaymentStatusInOrderByReceivedAtAsc(
                supplierId, List.of(PurchasePaymentStatus.UNPAID, PurchasePaymentStatus.PARTIALLY_PAID));

        List<SupplierOutstandingOrdersResponse.PurchaseOrderOutstanding> orderOutstandings = unpaidOrders.stream()
                .map(po -> new SupplierOutstandingOrdersResponse.PurchaseOrderOutstanding(
                        po.getId(),
                        po.getPoNumber(),
                        po.getReceivedAt(),
                        po.getTotalAmount(),
                        po.getPaidAmount(),
                        po.getOutstandingBalance()
                ))
                .toList();

        BigDecimal totalOutstanding = orderOutstandings.stream()
                .map(o -> o.outstandingBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SupplierOutstandingOrdersResponse(
                supplierId,
                supplier.getName(),
                totalOutstanding,
                orderOutstandings
        );
    }

    @Override
    public SupplierPaymentResponse recordSupplierPayment(UUID supplierId, BigDecimal amount, PurchasePaymentMethod paymentMethod, String description) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        List<PurchaseOrder> unpaidOrders = purchaseOrderRepository.findBySupplierIdAndPaymentStatusInOrderByReceivedAtAsc(
                supplierId, List.of(PurchasePaymentStatus.UNPAID, PurchasePaymentStatus.PARTIALLY_PAID));

        if (unpaidOrders.isEmpty()) {
            BigDecimal outstanding = supplierLedgerRepository.calculateBalance(supplierId);
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal payment = amount.min(outstanding);
                ledgerService.recordSupplierPayment(
                        supplierId,
                        null,
                        payment,
                        description != null ? description : "Payment for outstanding via " + paymentMethod,
                        java.time.LocalDate.now(),
                        "Supplier Payment",
                        securityService.getCurrentUserId()
                );
                amount = amount.subtract(payment);
            }
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException("No outstanding orders and no adjustment balance to pay");
            }
        }

        BigDecimal remaining = amount;
        int settledCount = 0;

        for (PurchaseOrder order : unpaidOrders) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal payAmount = remaining.min(order.getOutstandingBalance());
            ledgerService.recordSupplierPayment(
                    supplierId,
                    order.getId(),
                    payAmount,
                    "Payment for PO " + order.getPoNumber() + " via " + paymentMethod,
                    java.time.LocalDate.now(),
                    "PO Payment: " + order.getPoNumber(),
                    securityService.getCurrentUserId()
            );
            remaining = remaining.subtract(payAmount);
            settledCount++;
        }

        syncSupplierOutstanding(supplier);
        supplier = supplierRepository.save(supplier);

        return new SupplierPaymentResponse(
                supplierId,
                supplier.getName(),
                amount.subtract(remaining),
                settledCount,
                supplier.getOutstandingBalance()
        );
    }

    @Override
    public SupplierAdjustmentResponse manualAdjustOutstanding(UUID supplierId, AdjustOutstandingRequest request) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", supplierId));

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Adjustment amount must be positive");
        }

        ledgerService.recordSupplierAdjustment(
                supplierId,
                request.amount(),
                request.date(),
                request.description(),
                "Outstanding Adjustment",
                securityService.getCurrentUserId()
        );

        syncSupplierOutstanding(supplier);
        supplier = supplierRepository.save(supplier);

        return new SupplierAdjustmentResponse(
                supplierId,
                supplier.getName(),
                request.amount(),
                request.date(),
                request.description(),
                supplier.getOutstandingBalance()
        );
    }

    public void syncSupplierOutstanding(Supplier supplier) {
        BigDecimal actual = supplierLedgerRepository.calculateBalance(supplier.getId());
        supplier.setOutstandingBalance(actual);
    }
}
