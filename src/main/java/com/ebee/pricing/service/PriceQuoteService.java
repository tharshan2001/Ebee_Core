package com.ebee.pricing.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.pricing.dto.PricingDtos.QuoteResponse;
import com.ebee.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Looks up product and customer for a price quote (keeps the controller free of repositories). */
@Service
public class PriceQuoteService {

    private final PricingService pricing;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public PriceQuoteService(PricingService pricing, ProductRepository productRepository,
                             CustomerRepository customerRepository) {
        this.pricing = pricing;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public QuoteResponse quote(UUID productId, UUID customerId, UUID productUnitId) {
        var product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        UUID groupId = customerId == null ? null : customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId)).getPriceGroupId();
        var q = pricing.quote(product, groupId, productUnitId);
        return new QuoteResponse(productId, q.productUnit() != null ? q.productUnit().getId() : null,
                q.salesUnitName(), q.unitsPerSalesUnit(), q.listPrice(), q.groupPrice());
    }
}
