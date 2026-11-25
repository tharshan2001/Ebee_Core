package com.ebee.inventory.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.inventory.dto.InventoryLayerResponse;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.entity.InventoryLayer;
import com.ebee.inventory.mapper.InventoryMapper;
import com.ebee.inventory.repository.InventoryLayerRepository;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.stock.dto.StockRequest;
import com.ebee.stock.dto.StockResponse;
import com.ebee.stock.enums.StockMovementType;
import com.ebee.stock.entity.Stock;
import com.ebee.stock.mapper.StockMapper;
import com.ebee.stock.repository.StockRepository;
import com.ebee.stock.service.StockMovementRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryLayerRepository inventoryLayerRepository;
    private final ProductRepository productRepository;
    private final InventoryMapper inventoryMapper;
    private final StockMapper stockMapper;
    private final StockRepository stockRepository;
    private final StockMovementRecorder stockMovementRecorder;
    private final InventoryAlertService inventoryAlertService;
    private final com.ebee.location.service.LocationService locationService;

    // ── location helpers ───────────────────────────────────────────────

    private UUID loc(UUID locationId) {
        return locationId != null ? locationId : locationService.defaultLocation().getId();
    }

    /** Stock row for a product at a location (default location when null). */
    private Inventory inventoryAt(UUID productId, UUID locationId) {
        UUID l = loc(locationId);
        return inventoryRepository.findByProductIdAndLocationId(productId, l)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
    }

    /** Stock row for a product at a location, created empty if the product has never been there. */
    private Inventory ensureInventory(Product product, com.ebee.location.entity.Location location) {
        return inventoryRepository.findByProductIdAndLocationId(product.getId(), location.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .product(product)
                        .location(location)
                        .quantity(0)
                        .reservedQuantity(0)
                        .averageCost(BigDecimal.ZERO)
                        .build()));
    }

    /** Lock (and create if missing, for inbound moves) the rows for these products at one location. */
    private Map<UUID, Inventory> lockRows(Collection<UUID> productIds, UUID locationId, boolean createMissing) {
        UUID l = loc(locationId);
        if (createMissing) {
            com.ebee.location.entity.Location location = locationService.require(l);
            for (UUID pid : new TreeSet<>(productIds)) {
                if (inventoryRepository.findByProductIdAndLocationId(pid, l).isEmpty()) {
                    Product product = productRepository.findById(pid)
                            .orElseThrow(() -> new ResourceNotFoundException("Product", "id", pid));
                    ensureInventory(product, location);
                }
            }
        }
        return inventoryRepository.findByProductIdInWithLock(new TreeSet<>(productIds), l).stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));
    }

    @Transactional
    public InventoryResponse createInventory(UUID productId, Integer initialQuantity, Integer reorderLevel) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        com.ebee.location.entity.Location location = locationService.defaultLocation();
        if (inventoryRepository.findByProductIdAndLocationId(productId, location.getId()).isPresent()) {
            throw new IllegalArgumentException("Inventory already exists for product: " + productId);
        }

        Inventory inventory = Inventory.builder()
                .product(product)
                .location(location)
                .quantity(initialQuantity != null ? initialQuantity : 0)
                .reservedQuantity(0)
                .reorderLevel(reorderLevel)
                .build();

        Inventory saved = inventoryRepository.save(inventory);
        inventoryAlertService.checkAndNotify(saved);
        return inventoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryByProductId(UUID productId) {
        Inventory inventory = inventoryAt(productId, null);
        return inventoryMapper.toResponse(inventory);
    }

    /** Stock for a product at every location. */
    @Transactional(readOnly = true)
    public List<InventoryResponse> getStockByLocation(UUID productId) {
        return inventoryRepository.findAllByProductId(productId).stream().map(inventoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryById(UUID id) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "id", id));
        return inventoryMapper.toResponse(inventory);
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventories() {
        return inventoryRepository.findAllWithProduct().stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getLowStockInventories() {
        return inventoryRepository.findLowStockInventories().stream()
                .map(inventoryMapper::toResponse)
                .toList();
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public InventoryResponse adjustStock(StockRequest request) {
        Inventory inventory = request.getQuantityChange() != null && request.getQuantityChange() > 0
                ? ensureInventory(productRepository.findById(request.getProductId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getProductId())),
                        locationService.resolve(request.getLocationId(), null))
                : inventoryAt(request.getProductId(), request.getLocationId());

        int quantityBefore = inventory.getQuantity();
        int quantityAfter = quantityBefore + request.getQuantityChange();

        if (quantityAfter < 0) {
            String msg = quantityBefore == 0
                    ? "Out of stock."
                    : "Insufficient stock. Current stock: " + quantityBefore;
            throw new IllegalArgumentException(msg);
        }

        if (quantityAfter < inventory.getReservedQuantity()) {
            throw new IllegalArgumentException(
                    "Cannot reduce stock below reserved quantity. Reserved: "
                            + inventory.getReservedQuantity() + ", would leave: " + quantityAfter);
        }

        int change = request.getQuantityChange();
        if (change < 0) {
            consumeFifoLayers(inventory, -change);
            recalculateAverageCost(inventory);
        } else if (change > 0) {
            addLayer(inventory, change, resolveReturnCost(inventory, null));
            recalculateAverageCost(inventory);
        }

        inventory.setQuantity(quantityAfter);
        inventoryRepository.save(inventory);

        stockMovementRecorder.record(inventory, request.getQuantityChange(), quantityBefore, quantityAfter,
                request.getMovementType(), request.getNotes());

        inventoryAlertService.checkAndNotify(inventory);
        return inventoryMapper.toResponse(inventory);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public StockResponse recordOrderStock(UUID productId, Integer quantity, StockMovementType movementType, String notes) {
        Inventory inventory = inventoryAt(productId, null);

        int quantityBefore = inventory.getQuantity();
        int quantityChange = movementType == StockMovementType.OUT ? -quantity : quantity;
        int quantityAfter = quantityBefore + quantityChange;

        if (quantityAfter < 0) {
            String msg = quantityBefore == 0
                    ? "Out of stock: " + inventory.getProduct().getName()
                    : "Insufficient stock for product: " + inventory.getProduct().getName();
            throw new IllegalArgumentException(msg);
        }

        if (quantityAfter < inventory.getReservedQuantity()) {
            throw new IllegalArgumentException(
                    "Cannot reduce stock below reserved quantity for product: " + inventory.getProduct().getName()
                            + ". Reserved: " + inventory.getReservedQuantity() + ", would leave: " + quantityAfter);
        }

        if (movementType == StockMovementType.OUT) {
            consumeFifoLayers(inventory, quantity);
            recalculateAverageCost(inventory);
        } else if (movementType == StockMovementType.RETURN) {
            addLayer(inventory, quantity, resolveReturnCost(inventory, null));
            recalculateAverageCost(inventory);
        }

        inventory.setQuantity(quantityAfter);
        inventoryRepository.save(inventory);

        Stock stock = stockMovementRecorder.record(inventory, quantityChange, quantityBefore, quantityAfter, movementType, notes);
        inventoryAlertService.checkAndNotify(inventory);
        return stockMapper.toResponse(stock);
    }

    /**
     * OUT or RETURN for several lines at once. Rows are locked (PESSIMISTIC_WRITE, in product-id
     * order) so concurrent orders for the same product wait instead of failing on a version conflict.
     * RETURN lines go back in at the current average cost; use {@link #returnStock} to pass the
     * original cost.
     */
    @Transactional
    public List<StockResponse> recordOrderStocks(List<UUID> productIds, List<Integer> quantities,
                                                  StockMovementType movementType, List<String> notes) {
        return recordOrderStocks(null, productIds, quantities, movementType, notes);
    }

    @Transactional
    public List<StockResponse> recordOrderStocks(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                                  StockMovementType movementType, List<String> notes) {
        return applyMovements(locationId, productIds, quantities, movementType, notes, null, null);
    }

    /**
     * Deducts stock FIFO and returns the actual cost of the layers consumed for each line
     * (same order as the input). Used for order-line cost of goods.
     */
    @Transactional
    public List<BigDecimal> deductStock(List<UUID> productIds, List<Integer> quantities, List<String> notes) {
        return deductStock(null, productIds, quantities, notes);
    }

    /** FIFO deduction at a location (default when null), returning the cost consumed per row. */
    @Transactional
    public List<BigDecimal> deductStock(UUID locationId, List<UUID> productIds, List<Integer> quantities, List<String> notes) {
        return deductStock(locationId, StockMovementType.OUT, productIds, quantities, notes);
    }

    @Transactional
    public List<BigDecimal> deductStock(UUID locationId, StockMovementType type, List<UUID> productIds,
                                        List<Integer> quantities, List<String> notes) {
        List<BigDecimal> lineCosts = new ArrayList<>();
        applyMovements(locationId, productIds, quantities, type, notes, null, lineCosts);
        return lineCosts;
    }

    /**
     * Puts stock back. {@code unitCosts} entries are the cost each unit originally left at
     * (null = use the current average cost).
     */
    @Transactional
    public List<StockResponse> returnStock(List<UUID> productIds, List<Integer> quantities,
                                           List<BigDecimal> unitCosts, List<String> notes) {
        return returnStock(null, productIds, quantities, unitCosts, notes);
    }

    @Transactional
    public List<StockResponse> returnStock(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                           List<BigDecimal> unitCosts, List<String> notes) {
        return addStock(locationId, StockMovementType.RETURN, productIds, quantities, unitCosts, notes);
    }

    /**
     * FIFO / earliest-expiry deduction that also says which batches each row came from
     * (for recording the lots an order took).
     */
    @Transactional
    public List<List<com.ebee.inventory.dto.LotAllocation>> deductStockWithLots(UUID locationId, List<UUID> productIds,
                                                                              List<Integer> quantities, List<String> notes) {
        List<List<com.ebee.inventory.dto.LotAllocation>> out = new ArrayList<>();
        applyMovements(locationId, productIds, quantities, StockMovementType.OUT, notes, null, null, null, out);
        return out;
    }

    /** Return stock as batches carrying the given lot details and unit costs (null entries = unknown). */
    @Transactional
    public List<StockResponse> returnStock(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                           List<BigDecimal> unitCosts, List<com.ebee.inventory.dto.LotInfo> lots,
                                           List<String> notes) {
        return applyMovements(locationId, productIds, quantities, StockMovementType.RETURN, notes, unitCosts, lots,
                null, null);
    }

    /** Opening stock at a location: one batch per row at its cost, with optional lot / expiry. */
    @Transactional
    public List<StockResponse> addOpeningStock(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                               List<BigDecimal> unitCosts, List<com.ebee.inventory.dto.LotInfo> lots,
                                               List<String> notes) {
        return applyMovements(locationId, productIds, quantities, StockMovementType.OPENING, notes, unitCosts, lots,
                null, null);
    }

    /** Whether a product already has stock (or ever had stock moved) at a location — opening stock is refused then. */
    @Transactional(readOnly = true)
    public boolean hasStockHistory(UUID productId, UUID locationId) {
        return inventoryRepository.findByProductIdAndLocationId(productId, loc(locationId))
                .map(inv -> inv.getQuantity() > 0 || stockRepository.existsByInventoryId(inv.getId()))
                .orElse(false);
    }

    /** Inbound move (RETURN or TRANSFER_IN) at a location, each row at the given unit cost. */
    @Transactional
    public List<StockResponse> addStock(UUID locationId, StockMovementType type, List<UUID> productIds,
                                        List<Integer> quantities, List<BigDecimal> unitCosts, List<String> notes) {
        return applyMovements(locationId, productIds, quantities, type, notes, unitCosts, null);
    }

    private static boolean isOutbound(StockMovementType t) {
        return t == StockMovementType.OUT || t == StockMovementType.TRANSFER_OUT;
    }

    private List<StockResponse> applyMovements(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                               StockMovementType movementType, List<String> notes,
                                               List<BigDecimal> returnUnitCosts, List<BigDecimal> lineCostsOut) {
        return applyMovements(locationId, productIds, quantities, movementType, notes, returnUnitCosts, null,
                lineCostsOut, null);
    }

    private List<StockResponse> applyMovements(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                               StockMovementType movementType, List<String> notes,
                                               List<BigDecimal> returnUnitCosts,
                                               List<com.ebee.inventory.dto.LotInfo> inboundLots,
                                               List<BigDecimal> lineCostsOut,
                                               List<List<com.ebee.inventory.dto.LotAllocation>> allocationsOut) {
        if (movementType != StockMovementType.OUT && movementType != StockMovementType.RETURN
                && movementType != StockMovementType.TRANSFER_OUT && movementType != StockMovementType.TRANSFER_IN
                && movementType != StockMovementType.OPENING) {
            throw new IllegalArgumentException("Unsupported batch movement: " + movementType);
        }
        boolean outbound = isOutbound(movementType);
        Map<UUID, Inventory> inventoryMap = lockRows(productIds, locationId, !outbound);

        // Validate against the combined quantity per product (the same product can appear on several
        // lines, e.g. a regular item and inside a combo).
        Map<UUID, Integer> netChange = new LinkedHashMap<>();
        for (int i = 0; i < productIds.size(); i++) {
            UUID pid = productIds.get(i);
            if (!inventoryMap.containsKey(pid)) {
                throw new ResourceNotFoundException("Inventory", "productId", pid);
            }
            if (quantities.get(i) == null || quantities.get(i) <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            int change = outbound ? -quantities.get(i) : quantities.get(i);
            netChange.merge(pid, change, Integer::sum);
        }
        for (Map.Entry<UUID, Integer> e : netChange.entrySet()) {
            Inventory inv = inventoryMap.get(e.getKey());
            int projected = inv.getQuantity() + e.getValue();
            if (projected < 0) {
                throw new IllegalArgumentException(
                        (inv.getQuantity() == 0 ? "Out of stock: " : "Insufficient stock for product: ")
                                + inv.getProduct().getName());
            }
            if (projected < inv.getReservedQuantity()) {
                throw new IllegalArgumentException(
                        "Cannot reduce stock below reserved quantity for product: " + inv.getProduct().getName()
                                + ". Reserved: " + inv.getReservedQuantity() + ", would leave: " + projected);
            }
        }

        List<StockResponse> results = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Inventory inv = inventoryMap.get(productIds.get(i));
            int qty = quantities.get(i);
            int before = inv.getQuantity();
            int change = outbound ? -qty : qty;
            int after = before + change;

            if (outbound) {
                List<com.ebee.inventory.dto.LotAllocation> taken = consumeLayers(inv, qty);
                if (lineCostsOut != null) {
                    lineCostsOut.add(taken.stream().map(com.ebee.inventory.dto.LotAllocation::cost)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                }
                if (allocationsOut != null) {
                    allocationsOut.add(taken);
                }
            } else {
                BigDecimal unitCost = returnUnitCosts != null ? returnUnitCosts.get(i) : null;
                addLayer(inv, qty, resolveReturnCost(inv, unitCost), inboundLots != null ? inboundLots.get(i) : null);
            }
            recalculateAverageCost(inv);

            inv.setQuantity(after);
            Stock stock = stockMovementRecorder.record(inv, change, before, after, movementType, notes.get(i));
            inventoryAlertService.checkAndNotify(inv);
            results.add(stockMapper.toResponse(stock));
        }
        inventoryRepository.saveAll(inventoryMap.values());
        return results;
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public InventoryResponse reserveStock(UUID productId, Integer quantity) {
        Inventory inventory = inventoryAt(productId, null);

        int available = inventory.getQuantity() - inventory.getReservedQuantity();
        if (available < quantity) {
            String msg = available == 0
                    ? "Out of stock"
                    : "Insufficient available stock. Available: " + available;
            throw new IllegalArgumentException(msg);
        }

        int reservedBefore = inventory.getReservedQuantity();
        inventory.setReservedQuantity(reservedBefore + quantity);
        Inventory saved = inventoryRepository.save(inventory);

        stockMovementRecorder.record(inventory, 0, inventory.getQuantity(), inventory.getQuantity(),
                StockMovementType.RESERVE, "Reserved " + quantity + " units",
                reservedBefore, reservedBefore + quantity);

        return inventoryMapper.toResponse(saved);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public InventoryResponse releaseReservedStock(UUID productId, Integer quantity) {
        Inventory inventory = inventoryAt(productId, null);

        if (inventory.getReservedQuantity() < quantity) {
            throw new IllegalArgumentException("Cannot release more than reserved quantity");
        }

        int reservedBefore = inventory.getReservedQuantity();
        inventory.setReservedQuantity(reservedBefore - quantity);
        Inventory saved = inventoryRepository.save(inventory);

        stockMovementRecorder.record(inventory, 0, inventory.getQuantity(), inventory.getQuantity(),
                StockMovementType.RELEASE, "Released " + quantity + " units",
                reservedBefore, reservedBefore - quantity);

        return inventoryMapper.toResponse(saved);
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public InventoryResponse confirmOrderStock(UUID productId, Integer quantity) {
        Inventory inventory = inventoryAt(productId, null);

        if (inventory.getReservedQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient reserved stock");
        }

        int quantityBefore = inventory.getQuantity();
        int reservedBefore = inventory.getReservedQuantity();

        consumeFifoLayers(inventory, quantity);
        recalculateAverageCost(inventory);

        inventory.setReservedQuantity(reservedBefore - quantity);
        inventory.setQuantity(quantityBefore - quantity);

        Inventory saved = inventoryRepository.save(inventory);

        stockMovementRecorder.record(inventory, -quantity, quantityBefore, inventory.getQuantity(),
                StockMovementType.OUT, "Confirmed order: released " + quantity + " reserved units",
                reservedBefore, inventory.getReservedQuantity());

        inventoryAlertService.checkAndNotify(saved);
        return inventoryMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public boolean hasSufficientStock(UUID productId, Integer quantity) {
        Inventory inventory = inventoryAt(productId, null);
        return inventory.hasSufficientStock(quantity);
    }

    @Transactional(readOnly = true)
    public void checkStockAvailability(List<UUID> productIds, List<Integer> quantities) {
        checkStockAvailability(null, productIds, quantities);
    }

    @Transactional(readOnly = true)
    public void checkStockAvailability(UUID locationId, List<UUID> productIds, List<Integer> quantities) {
        Map<UUID, Inventory> inventoryMap = inventoryRepository.findByProductIdInAndLocationId(productIds, loc(locationId))
                .stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));

        for (int i = 0; i < productIds.size(); i++) {
            Inventory inv = inventoryMap.get(productIds.get(i));
            if (inv == null) {
                throw new ResourceNotFoundException("Inventory", "productId", productIds.get(i));
            }
            if (inv.getQuantity() < quantities.get(i)) {
                throw new IllegalArgumentException(
                        (inv.getQuantity() == 0 ? "Out of stock: " : "Insufficient stock for ")
                                + inv.getProduct().getName()
                                + " (available: " + inv.getQuantity()
                                + ", requested: " + quantities.get(i) + ")");
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getAverageCosts(List<UUID> productIds) {
        return inventoryRepository.findByProductIdInAndLocationId(productIds, loc(null))
                .stream()
                .collect(Collectors.toMap(
                        inv -> inv.getProduct().getId(),
                        inv -> inv.getAverageCost() != null ? inv.getAverageCost() : BigDecimal.ZERO
                ));
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 2))
    @Transactional
    public List<StockResponse> recordPurchaseStocks(List<UUID> productIds, List<Integer> quantities,
                                                     List<BigDecimal> unitCosts, List<String> notes) {
        return recordPurchaseStocks(null, productIds, quantities, unitCosts, notes);
    }

    /** Receive purchased stock into a location (rows are created for products new to it). */
    @Transactional
    public List<StockResponse> recordPurchaseStocks(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                                     List<BigDecimal> unitCosts, List<String> notes) {
        return recordPurchaseStocks(locationId, productIds, quantities, unitCosts, null, notes);
    }

    /** Receive purchased stock with lot / expiry details per row (required for products that track expiry). */
    @Transactional
    public List<StockResponse> recordPurchaseStocks(UUID locationId, List<UUID> productIds, List<Integer> quantities,
                                                     List<BigDecimal> unitCosts, List<com.ebee.inventory.dto.LotInfo> lots,
                                                     List<String> notes) {
        Map<UUID, Inventory> inventoryMap = lockRows(productIds, locationId, true);

        for (int i = 0; i < productIds.size(); i++) {
            Inventory inv = inventoryMap.get(productIds.get(i));
            if (inv == null) {
                throw new ResourceNotFoundException("Inventory", "productId", productIds.get(i));
            }
            int change = quantities.get(i);
            int projectedQuantity = inv.getQuantity() + change;
            if (projectedQuantity < 0) {
                throw new IllegalArgumentException(
                        (inv.getQuantity() == 0 ? "Out of stock: " : "Insufficient stock for product: ")
                                + inv.getProduct().getName());
            }
            if (projectedQuantity < inv.getReservedQuantity()) {
                throw new IllegalArgumentException(
                        "Cannot reduce stock below reserved quantity for product: " + inv.getProduct().getName()
                                + ". Reserved: " + inv.getReservedQuantity() + ", would leave: " + projectedQuantity);
            }
        }

        List<StockResponse> results = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Inventory inv = inventoryMap.get(productIds.get(i));
            int qty = quantities.get(i);
            BigDecimal unitCost = unitCosts.get(i);
            int before = inv.getQuantity();
            int after = before + qty;

            InventoryLayer layer = new InventoryLayer(inv, qty, qty, unitCost, LocalDateTime.now());
            applyLot(layer, inv, lots != null ? lots.get(i) : null);
            inventoryLayerRepository.save(layer);

            recalculateAverageCost(inv);

            inv.setQuantity(after);

            Stock stock = stockMovementRecorder.record(inv, qty, before, after, StockMovementType.PURCHASE, notes.get(i));
            inventoryAlertService.checkAndNotify(inv);
            results.add(stockMapper.toResponse(stock));
        }
        inventoryRepository.saveAll(inventoryMap.values());
        return results;
    }

    @Transactional(readOnly = true)
    public Integer getAvailableStock(UUID productId) {
        Inventory inventory = inventoryAt(productId, null);
        return inventory.getAvailableQuantity();
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> getStockHistoryByProductId(UUID productId, Pageable pageable) {
        Inventory inventory = inventoryAt(productId, null);

        return stockRepository.findByInventoryIdOrderByCreatedAtDesc(inventory.getId(), pageable)
                .map(stockMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<InventoryLayerResponse> getLayersByProductId(UUID productId, Pageable pageable) {
        Inventory inventory = inventoryAt(productId, null);

        return inventoryLayerRepository
                .findByInventoryIdOrderByReceivedAtAsc(inventory.getId(), pageable)
                .map(layer -> new InventoryLayerResponse(
                        layer.getId(),
                        inventory.getId(),
                        layer.getQuantity(),
                        layer.getRemainingQuantity(),
                        layer.getUnitCost(),
                        layer.getReceivedAt()));
    }

    // ── expiry ─────────────────────────────────────────────────────────

    /** Batches with stock that expire within {@code days} days (or already have), optionally at one location. */
    @Transactional(readOnly = true)
    public List<com.ebee.inventory.dto.ExpiringStockResponse> expiringStock(int days, UUID locationId) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate until = today.plusDays(Math.max(days, 0));
        List<InventoryLayer> layers = locationId != null
                ? inventoryLayerRepository.findExpiringByAtLocation(until, locationId)
                : inventoryLayerRepository.findExpiringBy(until);
        return layers.stream().map(l -> {
            Inventory inv = l.getInventory();
            return new com.ebee.inventory.dto.ExpiringStockResponse(l.getId(), inv.getProduct().getId(),
                    inv.getProduct().getName(), inv.getProduct().getItemCode(),
                    inv.getLocation() != null ? inv.getLocation().getId() : null,
                    inv.getLocation() != null ? inv.getLocation().getName() : null,
                    l.getLotNumber(), l.getExpiryDate(),
                    java.time.temporal.ChronoUnit.DAYS.between(today, l.getExpiryDate()),
                    l.getRemainingQuantity(), l.getUnitCost(),
                    l.getUnitCost().multiply(BigDecimal.valueOf(l.getRemainingQuantity())));
        }).toList();
    }

    /**
     * Remove every expired batch's remaining stock (movement type EXPIRED). Units still needed for
     * reservations are kept and reported, never pushed below the reserved quantity.
     */
    @Transactional
    public com.ebee.inventory.dto.WriteOffResult writeOffExpired(UUID locationId) {
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        List<InventoryLayer> expired = locationId != null
                ? inventoryLayerRepository.findExpiringByAtLocation(yesterday, locationId)
                : inventoryLayerRepository.findExpiringBy(yesterday);
        int batches = 0;
        int units = 0;
        int kept = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (InventoryLayer layer : expired) {
            Inventory inv = inventoryRepository.findByProductIdWithLock(layer.getInventory().getProduct().getId(),
                    layer.getInventory().getLocation().getId()).orElse(layer.getInventory());
            int removable = Math.min(layer.getRemainingQuantity(), inv.getQuantity() - inv.getReservedQuantity());
            kept += layer.getRemainingQuantity() - Math.max(removable, 0);
            if (removable <= 0) {
                continue;
            }
            int before = inv.getQuantity();
            layer.setRemainingQuantity(layer.getRemainingQuantity() - removable);
            inventoryLayerRepository.save(layer);
            inv.setQuantity(before - removable);
            recalculateAverageCost(inv);
            inventoryRepository.save(inv);
            stockMovementRecorder.record(inv, -removable, before, inv.getQuantity(), StockMovementType.EXPIRED,
                    "Expired" + (layer.getLotNumber() != null ? " lot " + layer.getLotNumber() : "")
                            + " (expiry " + layer.getExpiryDate() + ")");
            inventoryAlertService.checkAndNotify(inv);
            batches++;
            units += removable;
            value = value.add(layer.getUnitCost().multiply(BigDecimal.valueOf(removable)));
        }
        return new com.ebee.inventory.dto.WriteOffResult(batches, units, value, kept);
    }

    /** Total cost of the batches consumed (see {@link #consumeLayers}). */
    private BigDecimal consumeFifoLayers(Inventory inventory, int quantity) {
        return consumeLayers(inventory, quantity).stream().map(com.ebee.inventory.dto.LotAllocation::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Takes {@code quantity} units from the batches and says which batches they came from.
     * Products that track expiry: earliest expiry first, expired batches skipped (never sold).
     * Other products: oldest received first (FIFO).
     */
    private List<com.ebee.inventory.dto.LotAllocation> consumeLayers(Inventory inventory, int quantity) {
        List<InventoryLayer> layers = new ArrayList<>(inventoryLayerRepository
                .findByInventoryIdAndRemainingQuantityGreaterThanOrderByReceivedAtAsc(inventory.getId(), 0));
        boolean byExpiry = inventory.getProduct() != null && inventory.getProduct().tracksExpiry();
        if (byExpiry) {
            java.time.LocalDate today = java.time.LocalDate.now();
            layers.removeIf(l -> l.isExpired(today));
            layers.sort(Comparator.comparing(InventoryLayer::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(InventoryLayer::getReceivedAt));
        }

        int toConsume = quantity;
        List<com.ebee.inventory.dto.LotAllocation> taken = new ArrayList<>();
        for (InventoryLayer layer : layers) {
            if (toConsume <= 0) break;
            int consumable = Math.min(toConsume, layer.getRemainingQuantity());
            layer.setRemainingQuantity(layer.getRemainingQuantity() - consumable);
            taken.add(new com.ebee.inventory.dto.LotAllocation(inventory.getProduct().getId(), layer, consumable,
                    layer.getUnitCost()));
            toConsume -= consumable;
        }
        inventoryLayerRepository.saveAll(layers);

        if (toConsume > 0) {
            throw new IllegalArgumentException((byExpiry ? "Not enough unexpired stock for product: "
                    : "Insufficient stock layers for product: ") + inventory.getProduct().getName());
        }
        return taken;
    }

    private void addLayer(Inventory inventory, int quantity, BigDecimal unitCost) {
        addLayer(inventory, quantity, unitCost, null);
    }

    private void addLayer(Inventory inventory, int quantity, BigDecimal unitCost, com.ebee.inventory.dto.LotInfo lot) {
        InventoryLayer layer = new InventoryLayer(inventory, quantity, quantity, unitCost, LocalDateTime.now());
        applyLot(layer, inventory, lot);
        inventoryLayerRepository.save(layer);
    }

    /** Copies lot details onto a new batch; products that track expiry must have an expiry date. */
    private static void applyLot(InventoryLayer layer, Inventory inventory, com.ebee.inventory.dto.LotInfo lot) {
        if (lot != null) {
            layer.setLotNumber(lot.lotNumber() != null && !lot.lotNumber().isBlank() ? lot.lotNumber().trim() : null);
            layer.setExpiryDate(lot.expiryDate());
            layer.setManufacturedDate(lot.manufacturedDate());
        }
        if (inventory.getProduct() != null && inventory.getProduct().tracksExpiry() && layer.getExpiryDate() == null) {
            throw new IllegalArgumentException("An expiry date is required for " + inventory.getProduct().getName());
        }
    }

    /** Cost for stock coming back: the original cost if known, else the current average, else the last receipt. */
    private BigDecimal resolveReturnCost(Inventory inventory, BigDecimal originalUnitCost) {
        if (originalUnitCost != null && originalUnitCost.signum() > 0) {
            return originalUnitCost;
        }
        if (inventory.getAverageCost() != null && inventory.getAverageCost().signum() > 0) {
            return inventory.getAverageCost();
        }
        return inventoryLayerRepository.findTopByInventoryIdOrderByReceivedAtDesc(inventory.getId())
                .map(InventoryLayer::getUnitCost)
                .filter(c -> c.signum() > 0)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Weighted average cost of the stock still on hand: Σ(remaining × unitCost) / Σ remaining.
     * When nothing is left, the last average is kept so new returns/adjustments have a sensible cost.
     */
    private void recalculateAverageCost(Inventory inventory) {
        List<InventoryLayer> layers = inventoryLayerRepository
                .findByInventoryIdAndRemainingQuantityGreaterThanOrderByReceivedAtAsc(inventory.getId(), 0);
        long units = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (InventoryLayer layer : layers) {
            units += layer.getRemainingQuantity();
            value = value.add(layer.getUnitCost().multiply(BigDecimal.valueOf(layer.getRemainingQuantity())));
        }
        if (units > 0) {
            inventory.setAverageCost(value.divide(BigDecimal.valueOf(units), 4, java.math.RoundingMode.HALF_UP));
        }
    }

}