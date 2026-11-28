package com.ebee.importer.service.importers;

import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

/** How import files find a product: by item code ignoring case, preferring an exact match. */
final class ItemCodes {

    private ItemCodes() {
    }

    static Optional<Product> find(ProductRepository products, String code) {
        List<Product> matches = products.findByItemCodeIgnoreCase(code);
        return matches.stream().filter(p -> code.equals(p.getItemCode())).findFirst()
                .or(() -> matches.stream().findFirst());
    }
}
