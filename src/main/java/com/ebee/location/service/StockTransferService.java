package com.ebee.location.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.inventory.service.InventoryService;
import com.ebee.location.dto.StockTransferRequest;
import com.ebee.location.entity.Location;
import com.ebee.location.entity.StockTransfer;
import com.ebee.location.entity.StockTransfer.Status;
import com.ebee.location.entity.StockTransferItem;
import com.ebee.location.repository.StockTransferRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.enums.StockMovementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Moves stock between locations. Dispatch takes the units from the source by FIFO and records
 * their cost; receiving adds them at the destination at that same cost, so cost of goods stays
 * accurate wherever the stock is finally sold.
 */
@Service
public class StockTransferService {

    private final StockTransferRepository transferRepository;
    private final LocationService locationService;
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final SecurityService securityService;

    public StockTransferService(StockTransferRepository transferRepository, LocationService locationService,
                                InventoryService inventoryService, ProductRepository productRepository,
                                SecurityService securityService) {
        this.transferRepository = transferRepository;
        this.locationService = locationService;
        this.inventoryService = inventoryService;
        this.productRepository = productRepository;
        this.securityService = securityService;
    }

    @Transactional
    public StockTransfer create(StockTransferRequest request) {
        if (request.fromLocationId().equals(request.toLocationId())) {
            throw new IllegalArgumentException("Choose two different locations");
        }
        Location from = locationService.requireActive(request.fromLocationId());
        Location to = locationService.requireActive(request.toLocationId());
        StockTransfer t = new StockTransfer(nextNumber(), from, to, request.notes(), securityService.getCurrentUserId());
        for (StockTransferRequest.Item item : request.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", item.productId()));
            t.addItem(new StockTransferItem(product, item.quantity()));
        }
        return transferRepository.save(t);
    }

    /** Take the stock out of the source location (FIFO) and mark it in transit. */
    @Transactional
    public StockTransfer dispatch(UUID id) {
        StockTransfer t = lock(id);
        requireStatus(t, Status.DRAFT, "dispatched");
        List<BigDecimal> costs = inventoryService.deductStock(t.getFromLocation().getId(), StockMovementType.TRANSFER_OUT,
                productIds(t), quantities(t), notes(t, "to " + t.getToLocation().getName()));
        for (int i = 0; i < t.getItems().size(); i++) {
            t.getItems().get(i).setTotalCost(costs.get(i));
        }
        t.setStatus(Status.IN_TRANSIT);
        t.setDispatchedAt(LocalDateTime.now());
        return transferRepository.save(t);
    }

    /** Add the stock at the destination at the cost it left the source with. */
    @Transactional
    public StockTransfer receive(UUID id) {
        StockTransfer t = lock(id);
        requireStatus(t, Status.IN_TRANSIT, "received");
        inventoryService.addStock(t.getToLocation().getId(), StockMovementType.TRANSFER_IN, productIds(t), quantities(t),
                unitCosts(t), notes(t, "from " + t.getFromLocation().getName()));
        t.setStatus(Status.RECEIVED);
        t.setReceivedAt(LocalDateTime.now());
        return transferRepository.save(t);
    }

    /** Cancel a draft, or bring in-transit stock back to the source at its original cost. */
    @Transactional
    public StockTransfer cancel(UUID id) {
        StockTransfer t = lock(id);
        if (t.getStatus() == Status.IN_TRANSIT) {
            inventoryService.addStock(t.getFromLocation().getId(), StockMovementType.TRANSFER_IN, productIds(t),
                    quantities(t), unitCosts(t), notes(t, "cancelled, returned to " + t.getFromLocation().getName()));
        } else if (t.getStatus() != Status.DRAFT) {
            throw new IllegalStateException("Transfer " + t.getTransferNumber() + " is " + t.getStatus() + " and can't be cancelled");
        }
        t.setStatus(Status.CANCELLED);
        t.setCancelledAt(LocalDateTime.now());
        return transferRepository.save(t);
    }

    @Transactional(readOnly = true)
    public StockTransfer get(UUID id) {
        return transferRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id));
    }

    @Transactional(readOnly = true)
    public List<StockTransfer> list() {
        return transferRepository.findAllByOrderByCreatedAtDesc();
    }

    private StockTransfer lock(UUID id) {
        return transferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("StockTransfer", "id", id));
    }

    private static void requireStatus(StockTransfer t, Status expected, String action) {
        if (t.getStatus() != expected) {
            throw new IllegalStateException("Transfer " + t.getTransferNumber() + " is " + t.getStatus()
                    + "; only " + expected + " transfers can be " + action);
        }
    }

    private static List<UUID> productIds(StockTransfer t) {
        return t.getItems().stream().map(i -> i.getProduct().getId()).toList();
    }

    private static List<Integer> quantities(StockTransfer t) {
        return t.getItems().stream().map(StockTransferItem::getQuantity).toList();
    }

    private static List<BigDecimal> unitCosts(StockTransfer t) {
        List<BigDecimal> costs = new ArrayList<>();
        t.getItems().forEach(i -> costs.add(i.getUnitCost()));
        return costs;
    }

    private static List<String> notes(StockTransfer t, String what) {
        String note = "Transfer " + t.getTransferNumber() + " " + what;
        return t.getItems().stream().map(i -> note).toList();
    }

    private synchronized String nextNumber() {
        String prefix = "ST-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        return String.format("%s%03d", prefix, transferRepository.countByTransferNumberStartingWith(prefix) + 1);
    }
}
