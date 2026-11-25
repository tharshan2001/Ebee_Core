package com.ebee.importer.service.importers;

import com.ebee.category.dto.CategoryRequest;
import com.ebee.category.repository.CategoryRepository;
import com.ebee.category.service.CategoryService;
import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.RowImporter;
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

/** Creates products, or updates them when the item code already exists. Missing categories are created. */
@Component
public class ProductImporter implements RowImporter {

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final TaxService taxService;

    public ProductImporter(ProductRepository productRepository, ProductService productService,
                           CategoryRepository categoryRepository, CategoryService categoryService, TaxService taxService) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.taxService = taxService;
    }

    @Override
    public ImportType type() {
        return ImportType.PRODUCTS;
    }

    private Map<String, UUID> taxRatesByName() {
        Map<String, UUID> m = new HashMap<>();
        for (TaxRate r : taxService.listRates()) {
            m.put(r.getName().toLowerCase(Locale.ROOT), r.getId());
        }
        return m;
    }

    @Override
    public void validate(List<ImportRow> rows, List<RowError> errors) {
        Map<String, UUID> rates = taxRatesByName();
        Set<String> seen = new HashSet<>();
        for (ImportRow row : rows) {
            String code = row.requiredText("item_code", errors);
            row.requiredText("name", errors);
            row.requiredText("category", errors);
            row.decimal("price", true, false, errors);
            row.decimal("cost_price", false, false, errors);
            row.bool("tracks_expiry", errors);
            String rate = row.text("vat_rate");
            if (rate != null && !rates.containsKey(rate.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(row.rowNumber(), "vat_rate", "'" + rate + "' isn't a tax rate (see Tax → Rates)"));
            }
            if (code != null && !seen.add(code.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(row.rowNumber(), "item_code", "'" + code + "' appears more than once in the file"));
            }
        }
    }

    @Override
    public Counts apply(List<ImportRow> rows) {
        Map<String, UUID> rates = taxRatesByName();
        List<RowError> ignored = new java.util.ArrayList<>();
        int created = 0;
        int updated = 0;
        for (ImportRow row : rows) {
            String category = row.text("category");
            if (categoryRepository.findByNameIgnoreCase(category).isEmpty()) {
                CategoryRequest c = new CategoryRequest();
                c.setName(category);
                categoryService.createCategory(c);
            }
            ProductRequest r = new ProductRequest();
            r.setItemCode(row.text("item_code"));
            r.setName(row.text("name"));
            r.setCategory(category);
            r.setPrice(row.decimal("price", true, false, ignored));
            BigDecimal cost = row.decimal("cost_price", false, false, ignored);
            r.setCostPrice(cost);
            r.setBrand(row.text("brand"));
            r.setDescription(row.text("description"));
            String rate = row.text("vat_rate");
            r.setTaxRateId(rate != null ? rates.get(rate.toLowerCase(Locale.ROOT)) : null);
            r.setTracksExpiry(row.bool("tracks_expiry", ignored));

            Optional<Product> existing = productRepository.findByItemCode(r.getItemCode());
            if (existing.isPresent()) {
                productService.updateProduct(existing.get().getId(), r);
                updated++;
            } else {
                if (r.getCostPrice() == null) {
                    r.setCostPrice(BigDecimal.ZERO);
                }
                productService.createProduct(r);
                created++;
            }
        }
        return new Counts(created, updated, 0);
    }
}
