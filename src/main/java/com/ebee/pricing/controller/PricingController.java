package com.ebee.pricing.controller;

import com.ebee.common.response.ApiResponse;
import com.ebee.pricing.dto.PricingDtos.GroupPriceResponse;
import com.ebee.pricing.dto.PricingDtos.PriceGroupRequest;
import com.ebee.pricing.dto.PricingDtos.PriceGroupResponse;
import com.ebee.pricing.dto.PricingDtos.ProductUnitRequest;
import com.ebee.pricing.dto.PricingDtos.ProductUnitResponse;
import com.ebee.pricing.dto.PricingDtos.QuoteResponse;
import com.ebee.pricing.dto.PricingDtos.UnitRequest;
import com.ebee.pricing.dto.PricingDtos.UnitResponse;
import com.ebee.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricing;
    private final com.ebee.pricing.service.PriceQuoteService quotes;

    public PricingController(PricingService pricing, com.ebee.pricing.service.PriceQuoteService quotes) {
        this.pricing = pricing;
        this.quotes = quotes;
    }

    // ── price groups ───────────────────────────────────────────────────

    @GetMapping("/groups")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<PriceGroupResponse>>> groups() {
        return ok("Price groups", pricing.listGroups().stream().map(PriceGroupResponse::of).toList());
    }

    @PostMapping("/groups")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PriceGroupResponse>> createGroup(@Valid @RequestBody PriceGroupRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Price group created",
                PriceGroupResponse.of(pricing.createGroup(r.name(), r.description()))));
    }

    @PutMapping("/groups/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PriceGroupResponse>> updateGroup(@PathVariable UUID id, @Valid @RequestBody PriceGroupRequest r) {
        return ok("Price group updated", PriceGroupResponse.of(pricing.updateGroup(id, r.name(), r.description(), r.active())));
    }

    @PutMapping("/groups/{groupId}/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GroupPriceResponse>> setGroupPrice(@PathVariable UUID groupId, @PathVariable UUID productId,
                                                                         @RequestParam BigDecimal price) {
        return ok("Group price set", GroupPriceResponse.of(pricing.setGroupPrice(groupId, productId, price)));
    }

    @DeleteMapping("/groups/{groupId}/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeGroupPrice(@PathVariable UUID groupId, @PathVariable UUID productId) {
        pricing.removeGroupPrice(groupId, productId);
        return ResponseEntity.ok(ApiResponse.success("Group price removed; the normal price applies"));
    }

    @GetMapping("/products/{productId}/group-prices")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<GroupPriceResponse>>> groupPrices(@PathVariable UUID productId) {
        return ok("Group prices", pricing.groupPricesForProduct(productId).stream().map(GroupPriceResponse::of).toList());
    }

    /** Put a customer in a price group (omit groupId for normal prices). */
    @PutMapping("/customers/{customerId}/group")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> setCustomerGroup(@PathVariable UUID customerId,
                                                              @RequestParam(required = false) UUID groupId) {
        pricing.setCustomerGroup(customerId, groupId);
        return ResponseEntity.ok(ApiResponse.success("Customer price group updated"));
    }

    // ── units ──────────────────────────────────────────────────────────

    @GetMapping("/units")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<UnitResponse>>> units() {
        return ok("Units", pricing.listUnits().stream().map(UnitResponse::of).toList());
    }

    @PostMapping("/units")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(@Valid @RequestBody UnitRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Unit created",
                UnitResponse.of(pricing.createUnit(r.name()))));
    }

    @GetMapping("/products/{productId}/units")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<List<ProductUnitResponse>>> productUnits(@PathVariable UUID productId) {
        return ok("Selling units", pricing.productUnits(productId).stream().map(ProductUnitResponse::of).toList());
    }

    @PutMapping("/products/{productId}/units/{unitId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductUnitResponse>> setProductUnit(@PathVariable UUID productId, @PathVariable UUID unitId,
                                                                           @Valid @RequestBody ProductUnitRequest r) {
        return ok("Selling unit saved", ProductUnitResponse.of(
                pricing.setProductUnit(productId, unitId, r.unitsPerPack(), r.price(), r.active())));
    }

    // ── quote ──────────────────────────────────────────────────────────

    /** The list price a customer pays for a product (optionally by the pack), before discounts and VAT. */
    @GetMapping("/quote")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public ResponseEntity<ApiResponse<QuoteResponse>> quote(@RequestParam UUID productId,
                                                            @RequestParam(required = false) UUID customerId,
                                                            @RequestParam(required = false) UUID productUnitId) {
        return ok("Price quote", quotes.quote(productId, customerId, productUnitId));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T data) {
        return ResponseEntity.ok(ApiResponse.success(m, data));
    }
}
