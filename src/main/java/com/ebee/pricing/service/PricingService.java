package com.ebee.pricing.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.pricing.entity.PriceGroup;
import com.ebee.pricing.entity.ProductGroupPrice;
import com.ebee.pricing.entity.ProductUnit;
import com.ebee.pricing.entity.Unit;
import com.ebee.pricing.repository.PriceGroupRepository;
import com.ebee.pricing.repository.ProductGroupPriceRepository;
import com.ebee.pricing.repository.ProductUnitRepository;
import com.ebee.pricing.repository.UnitRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Customer price groups and selling units. {@link #quote} decides a line's list price:
 * <ol>
 *   <li>Per stock unit: the customer's group price for the product, else the normal price.</li>
 *   <li>Sold by the pack: the pack's fixed price if set, else (1) × units per pack.</li>
 * </ol>
 * Catalog and per-order discounts then apply to that list price (see Product.getPriceWithDiscount).
 */
@Service
public class PricingService {

    /** List price for one sales unit, and how many stock units that is. */
    public record Quote(BigDecimal listPrice, int unitsPerSalesUnit, ProductUnit productUnit, boolean groupPrice) {
        public String salesUnitName() {
            return productUnit != null ? productUnit.label() : null;
        }
    }

    private final PriceGroupRepository groupRepository;
    private final ProductGroupPriceRepository groupPriceRepository;
    private final UnitRepository unitRepository;
    private final ProductUnitRepository productUnitRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public PricingService(PriceGroupRepository groupRepository, ProductGroupPriceRepository groupPriceRepository,
                          UnitRepository unitRepository, ProductUnitRepository productUnitRepository,
                          ProductRepository productRepository, CustomerRepository customerRepository) {
        this.groupRepository = groupRepository;
        this.groupPriceRepository = groupPriceRepository;
        this.unitRepository = unitRepository;
        this.productUnitRepository = productUnitRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    // ── quoting ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Quote quote(Product product, UUID priceGroupId, UUID productUnitId) {
        BigDecimal perUnit = product.getPrice();
        boolean usedGroup = false;
        if (priceGroupId != null) {
            var group = groupRepository.findById(priceGroupId).filter(PriceGroup::isActive);
            if (group.isPresent()) {
                var gp = groupPriceRepository.findByProductIdAndPriceGroupId(product.getId(), priceGroupId);
                if (gp.isPresent()) {
                    perUnit = gp.get().getPrice();
                    usedGroup = true;
                }
            }
        }
        if (productUnitId == null) {
            return new Quote(perUnit, 1, null, usedGroup);
        }
        ProductUnit pu = productUnitRepository.findById(productUnitId)
                .orElseThrow(() -> new ResourceNotFoundException("ProductUnit", "id", productUnitId));
        if (!pu.getProductId().equals(product.getId())) {
            throw new IllegalArgumentException("'" + pu.label() + "' isn't a selling unit of " + product.getName());
        }
        if (!pu.isActive()) {
            throw new IllegalArgumentException("'" + pu.label() + "' is no longer sold for " + product.getName());
        }
        BigDecimal packPrice = pu.getPrice() != null
                ? pu.getPrice()
                : perUnit.multiply(BigDecimal.valueOf(pu.getUnitsPerPack()));
        return new Quote(packPrice, pu.getUnitsPerPack(), pu, usedGroup && pu.getPrice() == null);
    }

    // ── price groups ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PriceGroup> listGroups() {
        return groupRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public PriceGroup createGroup(String name, String description) {
        if (groupRepository.existsByNameIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("A price group named '" + name + "' already exists");
        }
        return groupRepository.save(new PriceGroup(name.trim(), description));
    }

    @Transactional
    public PriceGroup updateGroup(UUID id, String name, String description, Boolean active) {
        PriceGroup g = requireGroup(id);
        if (name != null && !name.isBlank()) {
            g.setName(name.trim());
        }
        g.setDescription(description);
        if (active != null) {
            g.setActive(active);
        }
        return groupRepository.save(g);
    }

    @Transactional
    public ProductGroupPrice setGroupPrice(UUID groupId, UUID productId, BigDecimal price) {
        requireGroup(groupId);
        requireProduct(productId);
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or more");
        }
        ProductGroupPrice gp = groupPriceRepository.findByProductIdAndPriceGroupId(productId, groupId)
                .orElseGet(() -> new ProductGroupPrice(productId, groupId, price));
        gp.setPrice(price);
        return groupPriceRepository.save(gp);
    }

    /** Back to the product's normal price for this group. */
    @Transactional
    public void removeGroupPrice(UUID groupId, UUID productId) {
        groupPriceRepository.findByProductIdAndPriceGroupId(productId, groupId).ifPresent(groupPriceRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<ProductGroupPrice> groupPricesForProduct(UUID productId) {
        return groupPriceRepository.findByProductId(productId);
    }

    /** Put a customer in a price group (null = normal prices). */
    @Transactional
    public void setCustomerGroup(UUID customerId, UUID groupId) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        if (groupId != null) {
            requireGroup(groupId);
        }
        customer.setPriceGroupId(groupId);
        customerRepository.save(customer);
    }

    // ── units ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Unit> listUnits() {
        return unitRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Unit createUnit(String name) {
        if (unitRepository.existsByNameIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("A unit named '" + name + "' already exists");
        }
        return unitRepository.save(new Unit(name.trim()));
    }

    @Transactional(readOnly = true)
    public List<ProductUnit> productUnits(UUID productId) {
        return productUnitRepository.findByProductIdOrderByUnitsPerPackAsc(productId);
    }

    /** Add or change how a product is sold by the pack. */
    @Transactional
    public ProductUnit setProductUnit(UUID productId, UUID unitId, int unitsPerPack, BigDecimal price, Boolean active) {
        requireProduct(productId);
        Unit unit = unitRepository.findById(unitId).orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
        if (unitsPerPack <= 1) {
            throw new IllegalArgumentException("A pack must hold more than 1 unit");
        }
        if (price != null && price.signum() < 0) {
            throw new IllegalArgumentException("Pack price must be zero or more");
        }
        ProductUnit pu = productUnitRepository.findByProductIdAndUnitId(productId, unitId)
                .orElseGet(() -> new ProductUnit(productId, unit, unitsPerPack, price));
        pu.setUnitsPerPack(unitsPerPack);
        pu.setPrice(price);
        if (active != null) {
            pu.setActive(active);
        }
        return productUnitRepository.save(pu);
    }

    private PriceGroup requireGroup(UUID id) {
        return groupRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("PriceGroup", "id", id));
    }

    private void requireProduct(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product", "id", id);
        }
    }
}
