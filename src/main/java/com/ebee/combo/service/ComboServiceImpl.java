package com.ebee.combo.service;

import com.ebee.combo.dto.ComboItemRequest;
import com.ebee.combo.dto.ComboRequest;
import com.ebee.combo.dto.ComboResponse;
import com.ebee.combo.dto.ComboRuleRequest;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.combo.entity.ComboRule;
import com.ebee.combo.enums.ComboRuleType;
import com.ebee.combo.exception.ComboImmutabilityException;
import com.ebee.combo.mapper.ComboMapper;
import com.ebee.combo.repository.ComboRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.enums.DiscountType;
import com.ebee.common.result.Result;
import com.ebee.order.repository.OrderItemRepository;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComboServiceImpl implements ComboService {

    private final ComboRepository comboRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final ComboMapper comboMapper;

    @Transactional
    @Override
    public ComboResponse createCombo(ComboRequest request) {
        Set<UUID> productIds = request.getItems().stream()
                .map(ComboItemRequest::getProductId)
                .collect(Collectors.toSet());

        if (request.getRule() != null && request.getRule().getFreeProductId() != null) {
            productIds.add(request.getRule().getFreeProductId());
        }

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (ComboItemRequest item : request.getItems()) {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw new ResourceNotFoundException("Product", "id", item.getProductId());
            }
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException(
                        "Product '" + product.getName() + "' is not active and cannot be added to a combo");
            }
        }

        Combo combo = Combo.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isActive(true)
                .items(new ArrayList<>())
                .build();

        for (ComboItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());
            ComboItem item = ComboItem.builder()
                    .combo(combo)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .build();
            combo.getItems().add(item);
        }

        if (request.getRule() != null) {
            ComboRuleRequest ruleReq = request.getRule();
            validateRuleRequest(ruleReq, productMap);

            ComboRule rule = ComboRule.builder()
                    .combo(combo)
                    .ruleType(ruleReq.getRuleType())
                    .discountType(ruleReq.getDiscountType() != null ? ruleReq.getDiscountType().name() : null)
                    .discountValue(ruleReq.getDiscountValue())
                    .build();

            if (ruleReq.getRuleType() == ComboRuleType.FREE_PRODUCT) {
                Product freeProduct = productMap.get(ruleReq.getFreeProductId());
                rule.setFreeProduct(freeProduct);
            }

            combo.setRule(rule);
        }

        Combo saved = comboRepository.save(combo);
        log.info("Created combo '{}' with {} items", saved.getName(), saved.getItems().size());
        return comboMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public ComboResponse updateCombo(UUID id, ComboRequest request) {
        Combo existing = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo", "id", id));

        if (orderItemRepository.existsByComboId(id)) {
            return cloneCombo(existing, request);
        }

        Set<UUID> productIds = request.getItems().stream()
                .map(ComboItemRequest::getProductId)
                .collect(Collectors.toSet());

        if (request.getRule() != null && request.getRule().getFreeProductId() != null) {
            productIds.add(request.getRule().getFreeProductId());
        }

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (ComboItemRequest item : request.getItems()) {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw new ResourceNotFoundException("Product", "id", item.getProductId());
            }
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException(
                        "Product '" + product.getName() + "' is not active and cannot be added to a combo");
            }
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.getItems().clear();
        comboRepository.flush();

        for (ComboItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());
            ComboItem item = ComboItem.builder()
                    .combo(existing)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .build();
            existing.getItems().add(item);
        }

        if (existing.getRule() != null) {
            existing.setRule(null);
        }

        if (request.getRule() != null) {
            ComboRuleRequest ruleReq = request.getRule();
            validateRuleRequest(ruleReq, productMap);

            ComboRule rule = ComboRule.builder()
                    .combo(existing)
                    .ruleType(ruleReq.getRuleType())
                    .discountType(ruleReq.getDiscountType() != null ? ruleReq.getDiscountType().name() : null)
                    .discountValue(ruleReq.getDiscountValue())
                    .build();

            if (ruleReq.getRuleType() == ComboRuleType.FREE_PRODUCT) {
                Product freeProduct = productMap.get(ruleReq.getFreeProductId());
                rule.setFreeProduct(freeProduct);
            }

            existing.setRule(rule);
        }

        Combo saved = comboRepository.save(existing);
        log.info("Updated combo '{}' (id={})", saved.getName(), saved.getId());
        return comboMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public void deleteCombo(UUID id) {
        Combo combo = comboRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Combo", "id", id));

        if (orderItemRepository.existsByComboId(id)) {
            throw new ComboImmutabilityException(
                    "Cannot delete combo '" + combo.getName()
                            + "' — it has been used in orders. Deactivate it instead.");
        }

        combo.setIsActive(false);
        comboRepository.save(combo);
        log.info("Deactivated combo '{}' (id={})", combo.getName(), id);
    }

    @Transactional(readOnly = true)
    @Override
    public Result<ComboResponse> getComboById(UUID id) {
        return comboRepository.findByIdWithDetails(id)
                .map(combo -> Result.<ComboResponse>ok(comboMapper.toResponse(combo)))
                .orElseGet(() -> Result.<ComboResponse>notFound("Combo", "id", id));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ComboResponse> listActiveCombos(Pageable pageable) {
        return comboRepository.findAllByIsActiveTrue(pageable).map(comboMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ComboResponse> listActiveCombos(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return comboRepository.findAllByIsActiveTrue(pageable).map(comboMapper::toResponse);
        }
        return comboRepository.findAllByIsActiveTrueAndNameContainingIgnoreCase(search.trim(), pageable)
                .map(comboMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ComboResponse> listAllCombos(Pageable pageable) {
        return comboRepository.findAll(pageable).map(comboMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<ComboResponse> listAllCombos(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return comboRepository.findAll(pageable).map(comboMapper::toResponse);
        }
        return comboRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                .map(comboMapper::toResponse);
    }

    private ComboResponse cloneCombo(Combo existing, ComboRequest request) {
        Set<UUID> productIds = request.getItems().stream()
                .map(ComboItemRequest::getProductId)
                .collect(Collectors.toSet());

        if (request.getRule() != null && request.getRule().getFreeProductId() != null) {
            productIds.add(request.getRule().getFreeProductId());
        }

        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (ComboItemRequest item : request.getItems()) {
            Product product = productMap.get(item.getProductId());
            if (product == null) {
                throw new ResourceNotFoundException("Product", "id", item.getProductId());
            }
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException(
                        "Product '" + product.getName() + "' is not active and cannot be added to a combo");
            }
        }

        existing.setIsActive(false);
        comboRepository.save(existing);

        Combo clone = Combo.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isActive(true)
                .items(new ArrayList<>())
                .build();

        for (ComboItemRequest itemReq : request.getItems()) {
            Product product = productMap.get(itemReq.getProductId());
            ComboItem item = ComboItem.builder()
                    .combo(clone)
                    .product(product)
                    .quantity(itemReq.getQuantity())
                    .build();
            clone.getItems().add(item);
        }

        if (request.getRule() != null) {
            ComboRuleRequest ruleReq = request.getRule();
            validateRuleRequest(ruleReq, productMap);

            ComboRule rule = ComboRule.builder()
                    .combo(clone)
                    .ruleType(ruleReq.getRuleType())
                    .discountType(ruleReq.getDiscountType() != null ? ruleReq.getDiscountType().name() : null)
                    .discountValue(ruleReq.getDiscountValue())
                    .build();

            if (ruleReq.getRuleType() == ComboRuleType.FREE_PRODUCT) {
                Product freeProduct = productMap.get(ruleReq.getFreeProductId());
                rule.setFreeProduct(freeProduct);
            }

            clone.setRule(rule);
        }

        Combo saved = comboRepository.save(clone);
        log.info("Cloned combo '{}' (id={}) → new combo '{}' (id={})",
                existing.getName(), existing.getId(), saved.getName(), saved.getId());
        return comboMapper.toResponse(saved);
    }

    private void validateRuleRequest(ComboRuleRequest ruleReq, Map<UUID, Product> productMap) {
        if (ruleReq.getRuleType() == ComboRuleType.FREE_PRODUCT) {
            if (ruleReq.getFreeProductId() == null) {
                throw new IllegalArgumentException("FREE_PRODUCT rule requires a freeProductId");
            }
            Product freeProduct = productMap.get(ruleReq.getFreeProductId());
            if (freeProduct == null) {
                throw new ResourceNotFoundException("Product", "id", ruleReq.getFreeProductId());
            }
            if (!Boolean.TRUE.equals(freeProduct.getIsActive())) {
                throw new IllegalArgumentException(
                        "Free product '" + freeProduct.getName() + "' is not active");
            }
        } else if (ruleReq.getRuleType() == ComboRuleType.DISCOUNT) {
            if (ruleReq.getDiscountType() == null || ruleReq.getDiscountValue() == null) {
                throw new IllegalArgumentException("DISCOUNT rule requires discountType and discountValue");
            }
            if (ruleReq.getDiscountValue().signum() < 0) {
                throw new IllegalArgumentException("discountValue must not be negative");
            }
            if (ruleReq.getDiscountType() == DiscountType.PERCENTAGE
                    && ruleReq.getDiscountValue().compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("PERCENTAGE discount cannot exceed 100");
            }
        }
    }
}
