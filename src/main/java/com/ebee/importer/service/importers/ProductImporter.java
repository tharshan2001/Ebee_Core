package com.ebee.importer.service.importers;

import com.ebee.category.dto.CategoryRequest;
import com.ebee.category.entity.Category;
import com.ebee.category.repository.CategoryRepository;
import com.ebee.category.service.CategoryService;
import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.RowImporter;
import com.ebee.inventory.service.InventoryService;
import com.ebee.product.dto.ProductRequest;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.product.service.ProductService;
import com.ebee.tax.entity.TaxRate;
import com.ebee.tax.service.TaxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates products, or updates them when the item code already exists (blank cells leave the stored
 * value). A row without an item code always creates a product, which gets a generated code. Missing or deleted categories are created. A product with stock keeps the cost its stock
 * gives it. VAT is optional: {@code vat} is 1 or 0, and {@code vat_rate} names the rate when there is
 * more than one.
 */
@Component
public class ProductImporter implements RowImporter {

    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final TaxService taxService;
    private final InventoryService inventoryService;

    public ProductImporter(ProductRepository productRepository, ProductService productService,
                           CategoryRepository categoryRepository, CategoryService categoryService, TaxService taxService,
                           InventoryService inventoryService) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.taxService = taxService;
        this.inventoryService = inventoryService;
    }

    @Override
    public ImportType type() {
        return ImportType.PRODUCTS;
    }

    /** Only rates the create form offers, by lower-case name. */
    private Map<String, TaxRate> activeTaxRatesByName() {
        Map<String, TaxRate> m = new HashMap<>();
        for (TaxRate r : taxService.listRates()) {
            if (r.isActive()) {
                m.put(r.getName().toLowerCase(Locale.ROOT), r);
            }
        }
        return m;
    }

    /**
     * The rate to set from the {@code vat} (1 / 0) and {@code vat_rate} cells, or null to leave the
     * product as it is (new products then have no VAT). Records an error if the cells can't be met.
     *
     * @param existing the product being updated, or null for a new one
     */
    private UUID taxRateFor(ImportRow row, Product existing, Map<String, TaxRate> rates, List<RowError> errors) {
        int n = row.rowNumber();
        Boolean vat = row.bool("vat", errors);
        String name = row.text("vat_rate");
        TaxRate named = name == null ? null : rates.get(name.toLowerCase(Locale.ROOT));
        if (name != null && named == null) {
            errors.add(new RowError(n, "vat_rate", "'" + name + "' isn't an active tax rate (see Tax → Rates)"));
            return null;
        }
        if (vat == null) {
            return named == null ? null : named.getId();
        }
        if (vat) {
            if (named != null) {
                if (named.getRate().signum() == 0) {
                    errors.add(new RowError(n, "vat_rate", "'" + name + "' charges no VAT, but vat is 1"));
                }
                return named.getId();
            }
            List<TaxRate> charging = rates.values().stream().filter(r -> r.getRate().signum() > 0).toList();
            if (charging.size() != 1) {
                errors.add(new RowError(n, "vat", charging.isEmpty()
                        ? "there is no active VAT rate (see Tax → Rates)"
                        : "there is more than one VAT rate: name one in vat_rate"));
                return null;
            }
            return charging.get(0).getId();
        }
        if (named != null && named.getRate().signum() > 0) {
            errors.add(new RowError(n, "vat_rate", "'" + name + "' charges VAT, but vat is 0"));
            return null;
        }
        if (named != null) {
            return named.getId();
        }
        if (existing == null || existing.getTaxRateId() == null) {
            return null; // already no VAT
        }
        // A product's rate can't be cleared, only changed, so use a 0% rate such as "Exempt".
        Optional<TaxRate> exempt = rates.values().stream().filter(r -> r.getRate().signum() == 0).findFirst();
        if (exempt.isEmpty()) {
            errors.add(new RowError(n, "vat", "add an active 0% tax rate to remove VAT from an existing product"));
            return null;
        }
        return exempt.get().getId();
    }

    @Override
    public Counts validate(List<ImportRow> rows, List<RowError> errors, List<RowError> notices) {
        Map<String, TaxRate> rates = activeTaxRatesByName();
        Set<String> seen = new HashSet<>();
        Set<String> newCategories = new HashSet<>();
        Set<String> uncodedNames = new HashSet<>();
        int created = 0;
        int updated = 0;
        for (ImportRow row : rows) {
            int n = row.rowNumber();
            String code = row.text("item_code");
            String productName = row.requiredText("name", errors);
            String category = row.requiredText("category", errors);
            row.maxLength("item_code", 100, errors);
            row.maxLength("name", 255, errors);
            row.maxLength("category", 100, errors);
            row.maxLength("brand", 100, errors);
            BigDecimal price = row.decimal("price", false, false, errors);
            row.maxValue("price", price, MAX_PRICE, errors);
            BigDecimal cost = row.decimal("cost_price", false, false, errors);
            row.maxValue("cost_price", cost, MAX_PRICE, errors);
            row.bool("tracks_expiry", errors);
            if (category != null && categoryRepository.findByNameIgnoreCaseAndIsActiveTrue(category).isEmpty()
                    && newCategories.add(category.toLowerCase(Locale.ROOT))) {
                notices.add(new RowError(n, "category", "'" + category + "' will be added to your categories"));
            }
            if (code == null) {
                taxRateFor(row, null, rates, errors);
                created++;
                // Without a code there is nothing to match on, so say when this looks like a repeat.
                if (productName != null) {
                    if (!uncodedNames.add(productName.toLowerCase(Locale.ROOT))) {
                        notices.add(new RowError(n, "name", "'" + productName + "' is in the file more than once"));
                    }
                    productRepository.findByNameIgnoreCase(productName).stream().findFirst().ifPresent(p ->
                            notices.add(new RowError(n, "name", "a product with this name already exists (" + p.getItemCode()
                                    + "); a second one will be created")));
                }
                if (price == null && row.text("price") == null) {
                    notices.add(new RowError(n, "price", "blank: the product is created with price 0"));
                }
                continue;
            }
            if (!seen.add(code.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(n, "item_code", "'" + code + "' appears more than once in the file"));
            }
            Optional<Product> found = ItemCodes.find(productRepository, code);
            taxRateFor(row, found.orElse(null), rates, errors);
            if (found.isEmpty()) {
                created++;
                if (price == null && row.text("price") == null) {
                    notices.add(new RowError(n, "price", "blank: the product is created with price 0"));
                }
                continue;
            }
            updated++;
            Product p = found.get();
            notices.add(new RowError(n, "item_code", "will update the existing product " + p.getName()
                    + (Boolean.FALSE.equals(p.getIsActive()) ? " (deactivated; it stays deactivated)" : "")));
            if (cost != null && inventoryService.hasAnyStockHistory(p.getId())) {
                notices.add(new RowError(n, "cost_price", "ignored: this product has stock, which sets its cost"));
            }
            if (price != null && "FIXED".equals(p.getMaxDiscountType()) && p.getMaxDiscountValue() != null
                    && p.getMaxDiscountValue().compareTo(price) > 0) {
                errors.add(new RowError(n, "price", "is below this product's maximum discount of "
                        + p.getMaxDiscountValue()));
            }
        }
        return new Counts(created, updated, 0);
    }

    /** The category's stored spelling (category filters match exactly), creating or restoring it if needed. */
    private String categoryName(String typed) {
        Optional<Category> existing = categoryRepository.findByNameIgnoreCase(typed);
        if (existing.isPresent() && !Boolean.FALSE.equals(existing.get().getIsActive())) {
            return existing.get().getName();
        }
        String name = existing.map(Category::getName).orElse(typed);
        CategoryRequest request = new CategoryRequest();
        request.setName(name);
        categoryService.createCategory(request);
        return name;
    }

    @Override
    public Counts apply(List<ImportRow> rows) {
        Map<String, TaxRate> rates = activeTaxRatesByName();
        List<RowError> ignored = new java.util.ArrayList<>();
        int created = 0;
        int updated = 0;
        for (ImportRow row : rows) {
            ProductRequest r = new ProductRequest();
            r.setItemCode(row.text("item_code"));
            r.setName(row.text("name"));
            r.setCategory(categoryName(row.text("category")));
            r.setPrice(row.decimal("price", false, false, ignored));
            r.setCostPrice(row.decimal("cost_price", false, false, ignored));
            r.setBrand(row.text("brand"));
            r.setDescription(row.text("description"));
            r.setTracksExpiry(row.bool("tracks_expiry", ignored));

            Optional<Product> existing = r.getItemCode() == null
                    ? Optional.empty() : ItemCodes.find(productRepository, r.getItemCode());
            r.setTaxRateId(taxRateFor(row, existing.orElse(null), rates, ignored));
            if (existing.isPresent()) {
                UUID id = existing.get().getId();
                r.setItemCode(existing.get().getItemCode());
                if (r.getCostPrice() != null && inventoryService.hasAnyStockHistory(id)) {
                    r.setCostPrice(null);
                }
                productService.updateProduct(id, r);
                updated++;
            } else {
                productService.createProduct(r);
                created++;
            }
        }
        return new Counts(created, updated, 0);
    }
}
