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

    @Transactional
    public InventoryResponse createInventory(UUID productId, Integer initialQuantity, Integer reorderLevel) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (inventoryRepository.findByProductId(productId).isPresent()) {
            throw new IllegalArgumentException("Inventory already exists for product: " + productId);
        }

        Inventory inventory = Inventory.builder()
                .product(product)
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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        return inventoryMapper.toResponse(inventory);
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
        Inventory inventory = inventoryRepository.findByProductId(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", request.getProductId()));

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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

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
        return applyMovements(productIds, quantities, movementType, notes, null, null);
    }

    /**
     * Deducts stock FIFO and returns the actual cost of the layers consumed for each line
     * (same order as the input). Used for order-line cost of goods.
     */
    @Transactional
    public List<BigDecimal> deductStock(List<UUID> productIds, List<Integer> quantities, List<String> notes) {
        List<BigDecimal> lineCosts = new ArrayList<>();
        applyMovements(productIds, quantities, StockMovementType.OUT, notes, null, lineCosts);
        return lineCosts;
    }

    /**
     * Puts stock back. {@code unitCosts} entries are the cost each unit originally left at
     * (null = use the current average cost).
     */
    @Transactional
    public List<StockResponse> returnStock(List<UUID> productIds, List<Integer> quantities,
                                           List<BigDecimal> unitCosts, List<String> notes) {
        return applyMovements(productIds, quantities, StockMovementType.RETURN, notes, unitCosts, null);
    }

    private List<StockResponse> applyMovements(List<UUID> productIds, List<Integer> quantities,
                                               StockMovementType movementType, List<String> notes,
                                               List<BigDecimal> returnUnitCosts, List<BigDecimal> lineCostsOut) {
        if (movementType != StockMovementType.OUT && movementType != StockMovementType.RETURN) {
            throw new IllegalArgumentException("Only OUT and RETURN are supported here, got " + movementType);
        }
        Map<UUID, Inventory> inventoryMap = inventoryRepository.findByProductIdInWithLock(new TreeSet<>(productIds))
                .stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));

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
            int change = movementType == StockMovementType.OUT ? -quantities.get(i) : quantities.get(i);
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
            int change = movementType == StockMovementType.OUT ? -qty : qty;
            int after = before + change;

            if (movementType == StockMovementType.OUT) {
                BigDecimal cost = consumeFifoLayers(inv, qty);
                if (lineCostsOut != null) {
                    lineCostsOut.add(cost);
                }
            } else {
                BigDecimal unitCost = returnUnitCosts != null ? returnUnitCosts.get(i) : null;
                addLayer(inv, qty, resolveReturnCost(inv, unitCost));
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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        return inventory.hasSufficientStock(quantity);
    }

    @Transactional(readOnly = true)
    public void checkStockAvailability(List<UUID> productIds, List<Integer> quantities) {
        Map<UUID, Inventory> inventoryMap = inventoryRepository.findByProductIdIn(productIds)
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
        return inventoryRepository.findByProductIdIn(productIds)
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
        Map<UUID, Inventory> inventoryMap = inventoryRepository.findByProductIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));

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
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        return inventory.getAvailableQuantity();
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> getStockHistoryByProductId(UUID productId, Pageable pageable) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

        return stockRepository.findByInventoryIdOrderByCreatedAtDesc(inventory.getId(), pageable)
                .map(stockMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<InventoryLayerResponse> getLayersByProductId(UUID productId, Pageable pageable) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

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

    /** Consumes the oldest layers first and returns the total cost of what was consumed. */
    private BigDecimal consumeFifoLayers(Inventory inventory, int quantity) {
        List<InventoryLayer> layers = inventoryLayerRepository
                .findByInventoryIdAndRemainingQuantityGreaterThanOrderByReceivedAtAsc(inventory.getId(), 0);

        int toConsume = quantity;
        BigDecimal cost = BigDecimal.ZERO;
        for (InventoryLayer layer : layers) {
            if (toConsume <= 0) break;
            int consumable = Math.min(toConsume, layer.getRemainingQuantity());
            layer.setRemainingQuantity(layer.getRemainingQuantity() - consumable);
            cost = cost.add(layer.getUnitCost().multiply(BigDecimal.valueOf(consumable)));
            toConsume -= consumable;
        }
        inventoryLayerRepository.saveAll(layers);

        if (toConsume > 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock layers for product: " + inventory.getProduct().getName());
        }
        return cost;
    }

    private void addLayer(Inventory inventory, int quantity, BigDecimal unitCost) {
        inventoryLayerRepository.save(new InventoryLayer(inventory, quantity, quantity, unitCost, LocalDateTime.now()));
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