package com.ebee.report.service;

import com.ebee.inventory.entity.Inventory;
import com.ebee.inventory.repository.InventoryRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.report.dto.InventorySummaryReport;
import com.ebee.report.dto.LowStockReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    public InventorySummaryReport getSummary() {
        List<Inventory> all = inventoryRepository.findAll();
        long totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        long lowStock = 0;
        long outOfStock = 0;

        for (Inventory inv : all) {
            int qty = inv.getQuantity() != null ? inv.getQuantity() : 0;
            totalQty += qty;

            Product product = inv.getProduct();
            if (product != null && product.getCostPrice() != null) {
                totalValue = totalValue.add(product.getCostPrice().multiply(BigDecimal.valueOf(qty)));
            }

            if (inv.getReorderLevel() != null && qty <= inv.getReorderLevel()) {
                lowStock++;
            }
            if (qty == 0) outOfStock++;
        }

        return InventorySummaryReport.builder()
                .totalStockQty(totalQty)
                .totalStockValue(totalValue)
                .lowStockCount(lowStock)
                .outOfStockCount(outOfStock)
                .build();
    }

    public List<LowStockReport> getLowStock() {
        List<Inventory> low = inventoryRepository.findLowStockInventories();
        List<LowStockReport> list = new ArrayList<>();

        for (Inventory inv : low) {
            int qty = inv.getQuantity() != null ? inv.getQuantity() : 0;
            int reorder = inv.getReorderLevel() != null ? inv.getReorderLevel() : 0;
            int shortfall = Math.max(0, reorder - qty);
            Product product = inv.getProduct();

            list.add(LowStockReport.builder()
                    .productId(product != null ? product.getId() : null)
                    .productName(product != null ? product.getName() : "Unknown")
                    .itemCode(product != null ? product.getItemCode() : "")
                    .currentStock(qty)
                    .reorderLevel(reorder)
                    .shortfall(shortfall)
                    .build());
        }
        return list;
    }
}
