package com.ebee.purchase.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.S3Service;
import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.purchase.dto.AttachmentResponse;
import com.ebee.purchase.dto.PurchaseOrderItemRequest;
import com.ebee.purchase.dto.PurchaseOrderItemResponse;
import com.ebee.purchase.dto.PurchaseOrderRequest;
import com.ebee.purchase.dto.PurchaseOrderResponse;
import com.ebee.purchase.dto.PurchaseOrderStatusRequest;
import com.ebee.purchase.entity.PurchaseOrder;
import com.ebee.purchase.entity.PurchaseOrderAttachment;
import com.ebee.purchase.entity.PurchaseOrderItem;
import com.ebee.purchase.enums.PurchaseOrderStatus;
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
    private final S3Service s3Service;

    public PurchaseOrderServiceImpl(PurchaseOrderRepository purchaseOrderRepository,
                                    PurchaseOrderAttachmentRepository attachmentRepository,
                                    SupplierRepository supplierRepository,
                                    ProductRepository productRepository,
                                    UserRepository userRepository,
                                    PurchaseOrderMapper purchaseOrderMapper,
                                    InventoryService inventoryService,
                                    InventoryRepository inventoryRepository,
                                    S3Service s3Service) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.attachmentRepository = attachmentRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.s3Service = s3Service;
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
            orders = purchaseOrderRepository.findAll(pageable);
        }
        return orders.map(purchaseOrderMapper::toFullResponse);
    }

    @Override
    public PurchaseOrderResponse updateStatus(UUID id, PurchaseOrderStatusRequest request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", id));

        PurchaseOrderStatus current = order.getStatus();
        PurchaseOrderStatus target = request.status();

        validateTransition(current, target);

        if (target == PurchaseOrderStatus.RECEIVED) {
            recordStockReceipt(order);
        }

        order.setStatus(target);
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return purchaseOrderMapper.toFullResponse(saved);
    }

    @Override
    public PurchaseOrderResponse addAttachment(UUID orderId, MultipartFile file) {
        PurchaseOrder order = purchaseOrderRepository.findByIdWithAttachments(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrder", "id", orderId));

        String folder = "purchases/" + orderId;
        String fileUrl = s3Service.uploadFile(file, folder);

        PurchaseOrderAttachment attachment = new PurchaseOrderAttachment();
        attachment.setPurchaseOrder(order);
        attachment.setFileUrl(fileUrl);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFileType(file.getContentType());

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

        s3Service.deleteFile(attachment.getFileUrl());
        order.getAttachments().remove(attachment);
        attachmentRepository.delete(attachment);
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

    private void recordStockReceipt(PurchaseOrder order) {
        List<UUID> productIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<BigDecimal> unitCosts = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (int i = 0; i < order.getItems().size(); i++) {
            PurchaseOrderItem item = order.getItems().get(i);
            productIds.add(item.getProduct().getId());
            quantities.add(item.getQuantity());
            unitCosts.add(item.getUnitPrice() != null ? item.getUnitPrice() : ZERO);
            notes.add("PO " + order.getPoNumber() + " - Item " + (i + 1));
        }

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
            case APPROVED -> target == PurchaseOrderStatus.RECEIVED;
            case RECEIVED, CANCELLED -> false;
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
