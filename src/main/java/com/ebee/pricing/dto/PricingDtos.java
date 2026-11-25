package com.ebee.pricing.dto;

import com.ebee.pricing.entity.PriceGroup;
import com.ebee.pricing.entity.ProductGroupPrice;
import com.ebee.pricing.entity.ProductUnit;
import com.ebee.pricing.entity.Unit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.UUID;

/** Request / response records for the pricing API. */
public final class PricingDtos {

    private PricingDtos() {
    }

    public record PriceGroupRequest(@NotBlank String name, String description, Boolean active) {
    }

    public record PriceGroupResponse(UUID id, String name, String description, boolean active) {
        public static PriceGroupResponse of(PriceGroup g) {
            return new PriceGroupResponse(g.getId(), g.getName(), g.getDescription(), g.isActive());
        }
    }

    public record GroupPriceResponse(UUID productId, UUID priceGroupId, BigDecimal price) {
        public static GroupPriceResponse of(ProductGroupPrice p) {
            return new GroupPriceResponse(p.getProductId(), p.getPriceGroupId(), p.getPrice());
        }
    }

    public record UnitRequest(@NotBlank String name) {
    }

    public record UnitResponse(UUID id, String name) {
        public static UnitResponse of(Unit u) {
            return new UnitResponse(u.getId(), u.getName());
        }
    }

    /** price: fixed pack price; omit to charge the per-unit price × unitsPerPack. */
    public record ProductUnitRequest(@Min(2) int unitsPerPack, BigDecimal price, Boolean active) {
    }

    public record ProductUnitResponse(UUID id, UUID productId, UUID unitId, String unitName, String label,
                                      int unitsPerPack, BigDecimal price, boolean active) {
        public static ProductUnitResponse of(ProductUnit p) {
            return new ProductUnitResponse(p.getId(), p.getProductId(), p.getUnit().getId(), p.getUnit().getName(),
                    p.label(), p.getUnitsPerPack(), p.getPrice(), p.isActive());
        }
    }

    /** List price for one sales unit before catalog / per-order discounts and VAT. */
    public record QuoteResponse(UUID productId, UUID productUnitId, String salesUnitName, int unitsPerSalesUnit,
                                BigDecimal listPrice, boolean groupPrice) {
    }
}
