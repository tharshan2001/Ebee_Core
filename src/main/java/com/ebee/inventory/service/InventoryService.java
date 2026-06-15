package com.ebee.inventory.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.inventory.dto.InventoryResponse;
import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.mapper.InventoryMapper;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
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
        return inventoryRepository.findAll().stream()
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

        inventory.setQuantity(quantityAfter);
        inventoryRepository.save(inventory);

        Stock stock = stockMovementRecorder.record(inventory, quantityChange, quantityBefore, quantityAfter, movementType, notes);
        inventoryAlertService.checkAndNotify(inventory);
        return stockMapper.toResponse(stock);
    }

    @Transactional
    public List<StockResponse> recordOrderStocks(List<UUID> productIds, List<Integer> quantities,
                                                  StockMovementType movementType, List<String> notes) {
        Map<UUID, Inventory> inventoryMap = inventoryRepository.findByProductIdIn(productIds)
                .stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), Function.identity()));

        for (int i = 0; i < productIds.size(); i++) {
            Inventory inv = inventoryMap.get(productIds.get(i));
            if (inv == null) {
                throw new ResourceNotFoundException("Inventory", "productId", productIds.get(i));
            }
            int change = movementType == StockMovementType.OUT ? -quantities.get(i) : quantities.get(i);
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
            int before = inv.getQuantity();
            int change = movementType == StockMovementType.OUT ? -qty : qty;
            int after = before + change;
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

        inventory.setReservedQuantity(inventory.getReservedQuantity() + quantity);
        Inventory saved = inventoryRepository.save(inventory);
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

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        Inventory saved = inventoryRepository.save(inventory);
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

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory.setQuantity(inventory.getQuantity() - quantity);

        Inventory saved = inventoryRepository.save(inventory);
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
    public Integer getAvailableStock(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        return inventory.getAvailableQuantity();
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getStockHistoryByProductId(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
        
        List<Stock> stocks = stockRepository.findByInventoryIdOrderByCreatedAtDesc(inventory.getId());
        return stockMapper.toResponseList(stocks);
    }

}