package com.ebee.purchase.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.port.StoragePort;
import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.purchase.dto.AttachmentResponse;
import com.ebee.purchase.dto.PurchaseOrderItemRequest;
import com.ebee.purchase.dto.PurchaseOrderItemResponse;
import com.ebee.purchase.dto.PurchaseOrderRequest;
import com.ebee.purchase.dto.PurchaseOrderItemResponse;
import com.ebee.purchase.dto.PurchaseOrderResponse;
import com.ebee.purchase.dto.PurchaseOrderStatusRequest;
import com.ebee.purchase.dto.PurchaseOrderUpdateRequest;
import com.ebee.purchase.dto.ReceiveOrderRequest;
import com.ebee.purchase.dto.RecordPaymentRequest;
import com.ebee.purchase.dto.ReturnItemsRequest;
import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.entity.PurchaseOrderAttachment;
import com.ebee.purchase.entity.PurchaseOrderItem;
import com.ebee.purchase.enums.PurchaseOrderStatus;
import com.ebee.purchase.enums.PurchasePaymentStatus;
import com.ebee.purchase.mapper.PurchaseOrderMapper;
import com.ebee.purchase.repository.PurchaseOrderAttachmentRepository;
import com.ebee.purchase.repository.PurchaseOrderRepository;
import com.ebee.supplier.entity.Supplier;
import com.ebee.purchase.exception.DuplicatePoNumberException;
import com.ebee.supplier.repository.SupplierRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

@Service
@Transactional
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderServiceImpl.class);
    private static final String PO_PREFIX = "PO";

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderAttachmentRepository attachmentRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final StoragePort storagePort;
    private final PurchaseOrderPaymentService purchaseOrderPaymentService;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseOrderAttachmentRepository attachmentRepository,
                                    SupplierRepository supplierRepository,
                                    ProductRepository productRepository,
                                    UserRepository userRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    InventoryService inventoryService,
                                    InventoryRepository inventoryRepository,
                                    StoragePort storagePort,
                                    PurchaseOrderPaymentService purchaseOrderPaymentService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.attachmentRepository = attachmentRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.storagePort = storagePort;
        this.purchaseOrderPaymentService = purchaseOrderPaymentService;
    }

    @Retryable(retryFor = DuplicatePoNumberException.class, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2))
    @Override
    public PurchaseOrderResponse createOrder(PurchaseOrderRequest request, UUID createdByUserId) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", request.supplierId()));

        List<UUID> productIds = request.items().stream()
                .map(PurchaseOrderItemRequest::productId)
                .toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (UUID pid : productIds) {
            if (!productMap.containsKey(pid)) {
                throw new ResourceNotFoundException("Product", "id", pid);
            }
        }

        User currentUser = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", createdByUserId));

        String poNumber = generatePoNumber();

        PurchaseOrder order = new PurchaseOrder();
        order.setPoNumber(poNumber);
        order.setSupplier(supplier);
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.setNotes(request.notes());
        order.setCreatedBy(currentUser);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseOrderItem> items = new ArrayList<>();

        for (PurchaseOrderItemRequest itemRequest : request.items()) {
            Product product = productMap.get(itemRequest.productId());
            BigDecimal subtotal = itemRequest.unitPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            item.setSellingPrice(itemRequest.sellingPrice());
            item.setSubtotal(subtotal);

            items.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setItems(items);
        order.setTotalAmount(totalAmount);

        if (request.paymentMethod() != null) {
            purchaseOrderPaymentService.initPayment(order, request.paymentMethod(), request.paidAmount());
        }

        PurchaseOrder saved;
        try {
            saved = purchaseOrderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicatePoNumber(ex)) {
                throw new DuplicatePoNumberException("Duplicate PO number: " + poNumber, ex);
            }
            throw ex;
        }

        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    public PurchaseOrderResponse updateOrder(UUID id, PurchaseOrderUpdateRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItemsLocked(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        if (order.getStatus() != PurchaseOrderStatus.DRAFT && order.getStatus() != PurchaseOrderStatus.PENDING) {
            throw new IllegalStateException("Cannot edit purchase order in status: " + order.getStatus());
        }

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", "id", request.supplierId()));

        List<UUID> productIds = request.items().stream()
                .map(PurchaseOrderItemRequest::productId)
                .toList();
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (UUID pid : productIds) {
            if (!productMap.containsKey(pid)) {
                throw new ResourceNotFoundException("Product", "id", pid);
            }
        }

        order.getItems().clear();

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<PurchaseOrderItem> newItems = new ArrayList<>();

        for (PurchaseOrderItemRequest itemRequest : request.items()) {
            Product product = productMap.get(itemRequest.productId());
            BigDecimal subtotal = itemRequest.unitPrice().multiply(BigDecimal.valueOf(itemRequest.quantity()));

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(product);
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(itemRequest.unitPrice());
            item.setSellingPrice(itemRequest.sellingPrice());
            item.setSubtotal(subtotal);

            newItems.add(item);
            totalAmount = totalAmount.add(subtotal);
        }

        order.setSupplier(supplier);
        order.setNotes(request.notes());
        order.getItems().addAll(newItems);
        order.setTotalAmount(totalAmount);

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getOrder(UUID id) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));
        return purchaseOrderMapper.toFullResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listOrders(String search, Pageable pageable) {
        Page<PurchaseOrder> orders;
        if (search != null && !search.isBlank()) {
            orders = purchaseOrderRepository.findByPoNumberStartingWith(search, pageable);
        } else {
            orders = purchaseOrderRepository.findAllWithDetails(pageable);
        }
        return orders.map(purchaseOrderMapper::toFullResponse);
    }

    @Override
    public PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatusRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItemsLocked(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        PurchaseOrderStatus current = order.getStatus();
        PurchaseOrderStatus target = request.status();

        validateTransition(current, target);

        if (target == PurchaseOrderStatus.CANCELLED && current == PurchaseOrderStatus.RECEIVED) {
            purchaseOrderPaymentService.releaseSupplierOutstanding(order);
        }

        order.setStatus(target);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    public PurchaseOrderResponse receiveOrder(UUID id, ReceiveOrderRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItemsLocked(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        PurchaseOrderStatus status = order.getStatus();
        if (status != PurchaseOrderStatus.APPROVED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalArgumentException("Cannot receive order in status: " + status);
        }

        Map<UUID, PurchaseOrderItem> itemMap = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));

        List<UUID> productIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<BigDecimal> unitCosts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        BigDecimal receivedTotal = BigDecimal.ZERO;
        boolean allFullyReceived = true;

        for (ReceiveOrderRequest.ItemReceipt receipt : request.items()) {
            PurchaseOrderItem item = itemMap.get(receipt.itemId());
            if (item == null) {
                throw new ResourceNotFoundException("PurchaseOrderItem", "id", receipt.itemId());
            }

            int alreadyReceived = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0;
            int alreadyReturned = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;
            int availableToReceive = item.getQuantity() - alreadyReceived - alreadyReturned;

            if (receipt.receivedQuantity() > availableToReceive) {
                throw new IllegalArgumentException(
                        "Cannot receive " + receipt.receivedQuantity() + " of item " + item.getProduct().getName()
                                + " — only " + availableToReceive + " remaining to receive");
            }

            int newReceived = alreadyReceived + receipt.receivedQuantity();
            item.setReceivedQuantity(newReceived);

            productIds.add(item.getProduct().getId());
            quantities.add(receipt.receivedQuantity());
            BigDecimal uc = item.getUnitPrice() != null ? item.getUnitPrice() : ZERO;
            unitCosts.add(uc);
            notes.add("PO " + order.getPoNumber() + " - " + item.getProduct().getName());

            receivedTotal = receivedTotal.add(uc.multiply(BigDecimal.valueOf(receipt.receivedQuantity())));

            if (newReceived < item.getQuantity()) {
                allFullyReceived = false;
            }
        }

        recordStockReceipt(productIds, quantities, unitCosts, notes, order.getPoNumber());

        if (order.getReceivedAt() == null) {
            order.setReceivedAt(LocalDateTime.now());
        }

        if (order.getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0) {
            order.setOutstandingBalance(receivedTotal);
            purchaseOrderPaymentService.addSupplierOutstandingWithLedger(
                    order.getSupplier(), receivedTotal, order.getId(),
                    order.getPoNumber(), "PO " + order.getPoNumber() + " - Stock received");
        } else {
            order.setOutstandingBalance(order.getOutstandingBalance().add(receivedTotal));
            purchaseOrderPaymentService.addSupplierOutstandingWithLedger(
                    order.getSupplier(), receivedTotal, order.getId(),
                    order.getPoNumber(), "PO " + order.getPoNumber() + " - Additional stock received");
        }

        order.setStatus(allFullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    public PurchaseOrderResponse returnItems(UUID id, ReturnItemsRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItemsLocked(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        PurchaseOrderStatus status = order.getStatus();
        if (status != PurchaseOrderStatus.RECEIVED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalArgumentException("Cannot return items from order in status: " + status);
        }

        Map<UUID, PurchaseOrderItem> itemMap = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));

        List<UUID> productIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        BigDecimal returnTotal = BigDecimal.ZERO;
        boolean allFullyReturned = true;

        for (ReturnItemsRequest.ItemReturn ret : request.items()) {
            PurchaseOrderItem item = itemMap.get(ret.itemId());
            if (item == null) {
                throw new ResourceNotFoundException("PurchaseOrderItem", "id", ret.itemId());
            }

            int received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0;
            int alreadyReturned = item.getReturnedQuantity() != null ? item.getReturnedQuantity() : 0;
            int availableToReturn = received - alreadyReturned;

            if (ret.returnQuantity() > availableToReturn) {
                throw new IllegalArgumentException(
                        "Cannot return " + ret.returnQuantity() + " of item " + item.getProduct().getName()
                                + " — only " + availableToReturn + " available to return");
            }

            item.setReturnedQuantity(alreadyReturned + ret.returnQuantity());

            productIds.add(item.getProduct().getId());
            quantities.add(ret.returnQuantity());
            String reason = request.reason() != null ? request.reason() : "Return to supplier";
            notes.add("PO " + order.getPoNumber() + " return - " + item.getProduct().getName() + " - " + reason);

            BigDecimal uc = item.getUnitPrice() != null ? item.getUnitPrice() : ZERO;
            returnTotal = returnTotal.add(uc.multiply(BigDecimal.valueOf(ret.returnQuantity())));

            int newReturned = item.getReturnedQuantity();
            if (newReturned < received) {
                allFullyReturned = false;
            }
        }

        inventoryService.recordOrderStocks(productIds, quantities, StockMovementType.OUT, notes);

        order.setOutstandingBalance(order.getOutstandingBalance().subtract(returnTotal));
        purchaseOrderPaymentService.subtractSupplierOutstandingForReturn(
                order.getSupplier(), returnTotal, order.getId(),
                order.getPoNumber(), "Return for PO " + order.getPoNumber());

        if (order.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            order.setOutstandingBalance(BigDecimal.ZERO);
            if (order.getPaymentStatus() == PurchasePaymentStatus.PAID) {
                order.setPaidAmount(order.getPaidAmount().subtract(returnTotal));
                if (order.getPaidAmount().compareTo(BigDecimal.ZERO) < 0) {
                    order.setPaidAmount(BigDecimal.ZERO);
                }
            }
        }

        if (allFullyReturned) {
            order.setStatus(PurchaseOrderStatus.RETURNED);
            order.setReturnedAt(LocalDateTime.now());
        }

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    public PurchaseOrderResponse addAttachment(UUID orderId, MultipartFile file) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithAttachments(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", orderId));

        String folder = "purchases/" + orderId;
        String fileUrl = storagePort.uploadFile(file, folder, "Attachment");

        PurchaseOrderAttachment attachment = new PurchaseOrderAttachment();
        attachment.setPurchaseOrder(order);
        attachment.setFileUrl(fileUrl);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileType(file.getContentType());
        attachment.setFileSize(file.getSize());

        order.getAttachments().add(attachment);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    public void deleteAttachment(UUID orderId, UUID attachmentId) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", orderId));

        PurchaseOrderAttachment attachment = attachmentRepository.findByIdAndPurchaseOrderId(attachmentId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment", "id", attachmentId));

        storagePort.deleteFile(attachment.getFileUrl());
        order.getAttachments().remove(attachment);
        attachmentRepository.delete(attachment);
    }

    @Override
    public PurchaseOrderResponse recordPayment(UUID id, RecordPaymentRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItemsLocked(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        purchaseOrderPaymentService.recordPayment(order.getId(), request);
        return purchaseOrderMapper.toFullResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> findBySupplier(UUID supplierId) {
        return purchaseOrderRepository.findBySupplierId(supplierId, Pageable.unpaged())
                .stream()
                .map(purchaseOrderMapper::toFullResponse)
                .toList();
    }

    private synchronized String generatePoNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = PO_PREFIX + "-" + datePart + "-";
        long count = purchaseOrderRepository.countByPoNumberStartingWith(prefix);
        return String.format("%s%03d", prefix, count + 1);
    }

    private void recordStockReceipt(List<UUID> productIds, List<Integer> quantities,
                                     List<BigDecimal> unitCosts, List<String> notes, String poNumber) {
        inventoryService.recordPurchaseStocks(productIds, quantities, unitCosts, notes);

        List<Inventory> inventories = inventoryRepository.findByProductIdIn(productIds);
        List<Product> productsToUpdate = new ArrayList<>();
        for (Inventory inv : inventories) {
            Product product = inv.getProduct();
            product.setCostPrice(inv.getAverageCost());
            productsToUpdate.add(product);
        }
        productRepository.saveAll(productsToUpdate);
    }

    private void validateTransition(PurchaseOrderStatus current, PurchaseOrderStatus target) {
        if (current == target) {
            throw new IllegalArgumentException("Order is already in status: " + current);
        }

        if (target == PurchaseOrderStatus.CANCELLED) {
            return;
        }

        boolean valid = switch (current) {
            case DRAFT -> target == PurchaseOrderStatus.PENDING;
            case PENDING -> target == PurchaseOrderStatus.APPROVED;
            case APPROVED -> false;
            case PARTIALLY_RECEIVED -> false;
            case RECEIVED -> target == PurchaseOrderStatus.RETURNED;
            case RETURNED, CANCELLED -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("Cannot transition from " + current + " to " + target);
        }
    }

    private static boolean isDuplicatePoNumber(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause == null) return false;
        String msg = cause.getMessage();
        return msg != null && (msg.contains("uk_purchase_orders_po_number") || msg.contains("purchase_orders_po_number_key"));
    }

    @Recover
    public PurchaseOrderResponse recoverDuplicatePoNumber(DuplicatePoNumberException ex, PurchaseOrderRequest request, UUID createdByUserId) {
        log.error("Failed to create purchase order after retries: {}", ex.getMessage());
        throw new IllegalStateException("Unable to generate unique PO number. Please try again.", ex);
    }

}
