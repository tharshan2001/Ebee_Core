package com.ebee.importer.service.importers;

import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.RowImporter;
import com.ebee.inventory.dto.LotInfo;
import com.ebee.inventory.service.InventoryService;
import com.ebee.location.entity.Location;
import com.ebee.location.repository.LocationRepository;
import com.ebee.location.service.LocationService;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stock on hand when you start using Ebee: one batch per row at its cost (with lot / expiry).
 * Refused for a product that already has stock or stock history at that location, so the same
 * file can't double your stock if it's imported twice.
 */
@Component
public class OpeningStockImporter implements RowImporter {

    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final LocationService locationService;
    private final InventoryService inventoryService;

    public OpeningStockImporter(ProductRepository productRepository, LocationRepository locationRepository,
                                LocationService locationService, InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.locationService = locationService;
        this.inventoryService = inventoryService;
    }

    @Override
    public ImportType type() {
        return ImportType.OPENING_STOCK;
    }

    private Optional<Location> location(ImportRow row) {
        String code = row.text("location_code");
        return code == null ? Optional.of(locationService.defaultLocation()) : locationRepository.findByCodeIgnoreCase(code);
    }

    @Override
    public void validate(List<ImportRow> rows, List<RowError> errors) {
        Set<String> seen = new HashSet<>();
        for (ImportRow row : rows) {
            String code = row.requiredText("item_code", errors);
            row.positiveInt("quantity", errors);
            row.decimal("unit_cost", true, false, errors);
            var expiry = row.date("expiry_date", errors);
            row.date("manufactured_date", errors);
            Optional<Location> loc = location(row);
            if (loc.isEmpty()) {
                errors.add(new RowError(row.rowNumber(), "location_code", "no location with code '" + row.text("location_code") + "'"));
            } else if (!loc.get().isActive()) {
                errors.add(new RowError(row.rowNumber(), "location_code", "location '" + loc.get().getCode() + "' is inactive"));
            }
            if (code == null) {
                continue;
            }
            Optional<Product> product = productRepository.findByItemCode(code);
            if (product.isEmpty()) {
                errors.add(new RowError(row.rowNumber(), "item_code", "no product with item code '" + code + "' (import products first)"));
                continue;
            }
            if (product.get().tracksExpiry() && expiry == null) {
                errors.add(new RowError(row.rowNumber(), "expiry_date", "is required: " + product.get().getName() + " tracks expiry"));
            }
            if (loc.isPresent() && inventoryService.hasStockHistory(product.get().getId(), loc.get().getId())) {
                errors.add(new RowError(row.rowNumber(), "item_code", product.get().getName() + " already has stock at "
                        + loc.get().getName() + "; use a stock adjustment instead"));
            }
            String key = code.toLowerCase(Locale.ROOT) + "|" + loc.map(Location::getId).orElse(null) + "|"
                    + (row.text("lot_number") == null ? "" : row.text("lot_number").toLowerCase(Locale.ROOT));
            if (!seen.add(key)) {
                errors.add(new RowError(row.rowNumber(), "lot_number", "same product, location and lot appear more than once"));
            }
        }
    }

    @Override
    public Counts apply(List<ImportRow> rows) {
        List<RowError> ignored = new ArrayList<>();
        // One stock call per location.
        Map<UUID, List<ImportRow>> byLocation = new LinkedHashMap<>();
        for (ImportRow row : rows) {
            byLocation.computeIfAbsent(location(row).orElseThrow().getId(), k -> new ArrayList<>()).add(row);
        }
        byLocation.forEach((locationId, locRows) -> {
            List<UUID> productIds = new ArrayList<>();
            List<Integer> quantities = new ArrayList<>();
            List<BigDecimal> costs = new ArrayList<>();
            List<LotInfo> lots = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (ImportRow row : locRows) {
                productIds.add(productRepository.findByItemCode(row.text("item_code")).orElseThrow().getId());
                quantities.add(row.positiveInt("quantity", ignored));
                costs.add(row.decimal("unit_cost", true, false, ignored));
                lots.add(new LotInfo(row.text("lot_number"), row.date("expiry_date", ignored), row.date("manufactured_date", ignored)));
                notes.add("Opening stock (import)");
            }
            inventoryService.addOpeningStock(locationId, productIds, quantities, costs, lots, notes);
        });
        return new Counts(rows.size(), 0, 0);
    }
}
