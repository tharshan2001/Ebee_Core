package com.ebee.order.service;

import com.ebee.accounting.service.LedgerService;
import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.audit.enums.AuditAction;
import com.ebee.audit.service.AuditLogService;
import com.ebee.combo.entity.Combo;
import com.ebee.combo.entity.ComboItem;
import com.ebee.combo.entity.ComboRule;
import com.ebee.combo.enums.ComboRuleType;
import com.ebee.combo.repository.ComboRepository;
import com.ebee.commission.service.CommissionCalculationService;
import com.ebee.commission.service.CommissionLifecycleService;
import com.ebee.common.enums.District;
import com.ebee.common.response.PageResponse;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.customer.event.CustomerDuesChangedEvent;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.customer.service.CustomerService;
import com.ebee.delivery.enums.DeliveryStatus;
import com.ebee.inventory.service.InventoryService;
import com.ebee.commission.entity.Commission;
import com.ebee.product.entity.Product;
import com.ebee.product.repository.ProductRepository;
import com.ebee.user.entity.User;
import com.ebee.user.repository.UserRepository;
import com.ebee.order.dto.OrderItemRequest;
import com.ebee.order.dto.OrderRequest;
import com.ebee.order.dto.OrderResponse;
import com.ebee.order.dto.OrderSettlementRequest;
import com.ebee.order.dto.PaymentResponse;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.dto.ReturnItemRequest;
import com.ebee.order.dto.ReturnOrderItemRequest;
import com.ebee.order.dto.UseAdvanceRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.entity.OrderItem;
import com.ebee.order.entity.Payment;
import com.ebee.order.enums.OrderSource;
import com.ebee.order.enums.OrderStatus;
import com.ebee.order.enums.PaymentMethod;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.enums.RefundChoice;
import com.ebee.order.event.OrderStatusChangedEvent;
import com.ebee.order.exception.DuplicateOrderNumberException;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.security.SecurityService;
import com.ebee.order.mapper.OrderMapper;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.repository.PaymentRepository;
import com.ebee.order.util.OrderItemSummaryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final SecurityService securityService;
    private final InventoryService inventoryService;
    private final OrderNumberService orderNumberService;
    private final CommissionCalculationService commissionCalculationService;
    private final CommissionLifecycleService commissionLifecycleService;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final OrderNotificationService orderNotificationService;
    private final AuditLogService auditLogService;
    private final OrderPaymentService orderPaymentService;
    private final OrderSettlementService orderSettlementService;
    private final OrderPdfService orderPdfService;
    private final LedgerService ledgerService;
    private final com.ebee.tax.service.TaxService taxService;
    private final com.ebee.tax.service.VatService vatService;
    private final com.ebee.finance.service.MoneyAccountService moneyAccountService;
    private final com.ebee.location.service.LocationService locationService;
    private final com.ebee.order.repository.OrderItemLotRepository orderItemLotRepository;
    private final com.ebee.pricing.service.PricingService pricingService;
    private final ComboRepository comboRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Retryable(retryFor = DuplicateOrderNumberException.class, maxAttempts = 5, backoff = @Backoff(delay = 50, multiplier = 2))
    @Transactional
    @Override
    public OrderResponse createOrder(OrderRequest request) {
        CreationContext ctx = new CreationContext();
        var taxSettings = taxService.getSettings();
        ctx.vatOn = taxSettings.isVatRegistered();
        ctx.pricesIncludeTax = taxSettings.isPricesIncludeTax();

        ctx.agent = resolveAgent(request);
        ctx.currentUser = resolveCurrentUser();
        ctx.customer = customerService.findByPhone(request.getCustomerPhoneNumber());
        // Ship from: chosen location, else the agent's home location, else the default (L1).
        ctx.location = locationService.resolve(request.getLocationId(),
                ctx.agent != null && ctx.agent.getLocation() != null ? ctx.agent.getLocation().getId() : null);

        District district = resolveDistrict(ctx.customer);
        OrderSource orderSource = ctx.agent != null ? OrderSource.AGENT : OrderSource.DIRECT;
        ctx.orderNumber = orderNumberService.generateOrderNumber(district, orderSource);

        ctx.order = Order.builder()
                .agent(ctx.agent)
                .customer(ctx.customer)
                .createdBy(ctx.currentUser)
                .orderNumber(ctx.orderNumber)
                .notes(request.getNotes())
                .status(OrderStatus.PENDING)
                .orderSource(orderSource)
                .location(ctx.location)
                .paymentMethod(request.getPaymentMethod())
                .items(new ArrayList<>())
                .build();

        ItemProcessing split = splitItems(request.getItems());
        processRegularItems(split.regularItems(), ctx);
        processComboItems(split.comboItems(), ctx);

        ctx.rowLots = inventoryService.deductStockWithLots(ctx.location.getId(),
                ctx.stockProductIds, ctx.stockQuantities, ctx.stockNotes);
        applyLineCosts(ctx, ctx.rowLots.stream().map(row -> row.stream()
                .map(com.ebee.inventory.dto.LotAllocation::cost).reduce(BigDecimal.ZERO, BigDecimal::add)).toList());

        ctx.order.setTotalAmount(ctx.totalAmount);
        ctx.order.setTaxTotal(ctx.taxTotal);
        orderPaymentService.initPayment(ctx.order, request.getPaymentMethod(),
                request.getPaidAmount(), request.getChequeNumber(), request.getProofFileUrl());

        Order savedOrder;
        try {
            savedOrder = orderRepository.save(ctx.order);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateOrderNumber(ex)) {
                throw new DuplicateOrderNumberException("Duplicate order number: " + ctx.order.getOrderNumber(), ex);
            }
            throw ex;
        }

        recordPostSaveOperations(savedOrder, ctx, request);

        return orderMapper.toResponse(savedOrder);
    }

    private static class CreationContext {
        Agent agent;
        User currentUser;
        Customer customer;
        Order order;
        String orderNumber;
        List<UUID> stockProductIds = new ArrayList<>();
        List<Integer> stockQuantities = new ArrayList<>();
        List<String> stockNotes = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        com.ebee.location.entity.Location location;
        /** Batches each stock row came from (same order as stockProductIds). */
        List<List<com.ebee.inventory.dto.LotAllocation>> rowLots = List.of();
        boolean vatOn;
        boolean pricesIncludeTax = true;
        BigDecimal taxTotal = BigDecimal.ZERO;
        Map<UUID, BigDecimal> rateCache = new java.util.HashMap<>();
        /** Which stock rows (indexes into stockProductIds) belong to each order line. */
        Map<OrderItem, List<Integer>> lineRows = new java.util.IdentityHashMap<>();
    }

    /** Which batches each saved order line took (recalls, and returns back to the same lot). */
    private void recordSoldLots(CreationContext ctx) {
        List<com.ebee.order.entity.OrderItemLot> lots = new ArrayList<>();
        ctx.lineRows.forEach((item, rows) -> {
            for (int row : rows) {
                if (row >= ctx.rowLots.size()) {
                    continue;
                }
                for (var a : ctx.rowLots.get(row)) {
                    lots.add(new com.ebee.order.entity.OrderItemLot(item, a.productId(),
                            a.layer() != null ? a.layer().getId() : null,
                            a.layer() != null ? a.layer().getLotNumber() : null,
                            a.layer() != null ? a.layer().getExpiryDate() : null,
                            a.quantity(), a.unitCost()));
                }
            }
        });
        if (!lots.isEmpty()) {
            orderItemLotRepository.saveAll(lots);
        }
    }

    /** costPrice = actual FIFO cost of the stock this line consumed, per unit of the line. */
    private static void applyLineCosts(CreationContext ctx, List<BigDecimal> rowCosts) {
        ctx.lineRows.forEach((item, rows) -> {
            BigDecimal total = BigDecimal.ZERO;
            for (int row : rows) {
                total = total.add(rowCosts.get(row));
            }
            item.setCostPrice(total.divide(BigDecimal.valueOf(item.getQuantity()), 4, RoundingMode.HALF_UP));
        });
    }

    private record ItemProcessing(List<OrderItemRequest> regularItems, List<OrderItemRequest> comboItems) {}

    private ItemProcessing splitItems(List<OrderItemRequest> items) {
        List<OrderItemRequest> regularItems = new ArrayList<>();
        List<OrderItemRequest> comboItems = new ArrayList<>();
        for (OrderItemRequest item : items) {
            if (item.getComboId() != null) {
                comboItems.add(item);
            } else {
                regularItems.add(item);
            }
        }
        return new ItemProcessing(regularItems, comboItems);
    }

    private Agent resolveAgent(OrderRequest request) {
        if (securityService.isAdmin()) {
            if (request.getAgentCode() != null) {
                Agent agent = agentRepository.findByAgentCode(request.getAgentCode())
                        .orElseThrow(() -> new ResourceNotFoundException("Agent", "agentCode", request.getAgentCode()));
                if (!Boolean.TRUE.equals(agent.getIsActive())) {
                    throw new IllegalArgumentException("Agent is not active");
                }
                return agent;
            }
            return null;
        }
        UUID userId = securityService.getCurrentUserId();
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        if (!Boolean.TRUE.equals(agent.getIsActive())) {
            throw new IllegalArgumentException("Agent is not active");
        }
        return agent;
    }

    private User resolveCurrentUser() {
        return userRepository.findById(securityService.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", securityService.getCurrentUserId()));
    }

    private District resolveDistrict(Customer customer) {
        District district = (customer.getAddress() != null) ? customer.getAddress().getDistrict() : null;
        if (district == null) {
            throw new IllegalArgumentException("Customer has no district. Update their address first.");
        }
        return district;
    }

    private void processRegularItems(List<OrderItemRequest> regularItems, CreationContext ctx) {
        if (regularItems.isEmpty()) return;

        List<String> itemCodes = regularItems.stream()
                .map(OrderItemRequest::getProductItemCode)
                .toList();
        Map<String, Product> productMap = productRepository.findByItemCodeIn(itemCodes).stream()
                .collect(Collectors.toMap(Product::getItemCode, Function.identity()));

        for (String itemCode : itemCodes) {
            Product product = productMap.get(itemCode);
            if (product == null) throw new ResourceNotFoundException("Product", "itemCode", itemCode);
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException("Product is not active: " + product.getName());
            }
        }

        for (int i = 0; i < regularItems.size(); i++) {
            OrderItemRequest itemRequest = regularItems.get(i);
            Product product = productMap.get(itemRequest.getProductItemCode());

            if ((itemRequest.getDiscountType() == null) != (itemRequest.getDiscountValue() == null)) {
                throw new IllegalArgumentException(
                        "discountType and discountValue must both be provided together for item '"
                                + itemRequest.getProductItemCode() + "'");
            }

            String discountType = itemRequest.getDiscountType() != null ? itemRequest.getDiscountType().name() : null;
            BigDecimal rate = taxRateFor(ctx, product);
            // List price: the customer's group price (else normal price), × pack size unless the pack has its own price.
            var quote = pricingService.quote(product, ctx.customer != null ? ctx.customer.getPriceGroupId() : null,
                    itemRequest.getUnitId());
            // Discounts apply to the list price first; VAT is worked out on the result.
            var tax = com.ebee.tax.service.TaxCalculator.forUnit(
                    product.getPriceWithDiscount(quote.listPrice(), quote.unitsPerSalesUnit(), discountType,
                            itemRequest.getDiscountValue()), rate, ctx.pricesIncludeTax);
            BigDecimal originalUnitPrice = com.ebee.tax.service.TaxCalculator.forUnit(
                    quote.listPrice(), rate, ctx.pricesIncludeTax).grossUnitPrice();
            BigDecimal unitPrice = tax.grossUnitPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            BigDecimal lineTax = tax.unitTax().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            OrderItem item = OrderItem.builder()
                    .order(ctx.order)
                    .product(product)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .originalUnitPrice(originalUnitPrice)
                    .subtotal(subtotal)
                    .discountType(discountType)
                    .discountValue(itemRequest.getDiscountValue())
                    .taxRate(tax.unitTax().signum() > 0 ? rate : null)
                    .unitTax(tax.unitTax())
                    .taxAmount(lineTax)
                    .salesUnitId(quote.productUnit() != null ? quote.productUnit().getId() : null)
                    .salesUnitName(quote.salesUnitName())
                    .unitsPerSalesUnit(quote.unitsPerSalesUnit())
                    .build();

            ctx.order.getItems().add(item);
            ctx.totalAmount = ctx.totalAmount.add(subtotal);
            ctx.taxTotal = ctx.taxTotal.add(lineTax);

            ctx.lineRows.put(item, List.of(ctx.stockProductIds.size()));
            ctx.stockProductIds.add(product.getId());
            ctx.stockQuantities.add(item.toStockUnits(itemRequest.getQuantity()));
            ctx.stockNotes.add("Order " + ctx.orderNumber + " - Item " + (i + 1)
                    + (quote.productUnit() != null ? " (" + itemRequest.getQuantity() + " × " + quote.salesUnitName() + ")" : ""));
        }
    }

    private void processComboItems(List<OrderItemRequest> comboItems, CreationContext ctx) {
        for (OrderItemRequest itemRequest : comboItems) {
            Combo combo = comboRepository.findByIdWithDetails(itemRequest.getComboId())
                    .orElseThrow(() -> new ResourceNotFoundException("Combo", "id", itemRequest.getComboId()));
            validateCombo(combo);

            ComboRule rule = combo.getRule();
            List<UUID> comboStockProductIds = new ArrayList<>();
            List<Integer> comboStockQuantities = new ArrayList<>();
            List<String> comboStockNotes = new ArrayList<>();

            for (ComboItem ci : combo.getItems()) {
                comboStockProductIds.add(ci.getProduct().getId());
                comboStockQuantities.add(ci.getQuantity());
                comboStockNotes.add("Order " + ctx.orderNumber + " - Combo " + combo.getName()
                        + " - " + ci.getProduct().getName());
            }

            if (rule != null && rule.getRuleType() == ComboRuleType.FREE_PRODUCT
                    && !Boolean.TRUE.equals(itemRequest.getApplyFreeProductAsDiscount())) {
                comboStockProductIds.add(rule.getFreeProduct().getId());
                comboStockQuantities.add(1);
                comboStockNotes.add("Order " + ctx.orderNumber + " - Combo " + combo.getName()
                        + " - FREE " + rule.getFreeProduct().getName());
            }

            inventoryService.checkStockAvailability(ctx.location.getId(), comboStockProductIds, comboStockQuantities);
            int firstRow = ctx.stockProductIds.size();
            int componentRows = combo.getItems().size();
            ctx.stockProductIds.addAll(comboStockProductIds);
            ctx.stockQuantities.addAll(comboStockQuantities);
            ctx.stockNotes.addAll(comboStockNotes);

            BigDecimal comboPrice = combo.calculateComboPrice();
            // VAT: split the combo price across its products by list price; each part at its own rate.
            List<com.ebee.tax.service.TaxCalculator.Share> shares = new ArrayList<>();
            for (ComboItem ci : combo.getItems()) {
                shares.add(new com.ebee.tax.service.TaxCalculator.Share(
                        ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())),
                        taxRateFor(ctx, ci.getProduct())));
            }
            var comboTax = com.ebee.tax.service.TaxCalculator.forBundle(
                    calculateComboUnitPrice(combo, rule, itemRequest, comboPrice), shares, ctx.pricesIncludeTax);
            BigDecimal unitPrice = comboTax.grossUnitPrice();
            BigDecimal comboListGross = com.ebee.tax.service.TaxCalculator.forBundle(
                    comboPrice, shares, ctx.pricesIncludeTax).grossUnitPrice();

            OrderItem comboItem = OrderItem.builder()
                    .order(ctx.order)
                    .product(null)
                    .combo(combo)
                    .quantity(1)
                    .unitPrice(unitPrice)
                    .originalUnitPrice(comboListGross)
                    .subtotal(unitPrice)
                    .taxRate(comboTax.unitTax().signum() > 0 ? com.ebee.tax.service.TaxCalculator.commonRate(shares) : null)
                    .unitTax(comboTax.unitTax())
                    .taxAmount(comboTax.unitTax())
                    .build();
            ctx.order.getItems().add(comboItem);
            List<Integer> comboRows = new ArrayList<>();
            for (int r = 0; r < componentRows; r++) {
                comboRows.add(firstRow + r);
            }
            ctx.lineRows.put(comboItem, comboRows);
            ctx.totalAmount = ctx.totalAmount.add(unitPrice);
            ctx.taxTotal = ctx.taxTotal.add(comboTax.unitTax());

            if (rule != null && rule.getRuleType() == ComboRuleType.FREE_PRODUCT
                    && !Boolean.TRUE.equals(itemRequest.getApplyFreeProductAsDiscount())) {
                OrderItem freeItem = OrderItem.builder()
                        .order(ctx.order)
                        .product(rule.getFreeProduct())
                        .quantity(1)
                        .unitPrice(BigDecimal.ZERO)
                        .originalUnitPrice(rule.getFreeProduct().getPrice())
                        .subtotal(BigDecimal.ZERO)
                        .isFreeProduct(true)
                        .build();
                ctx.order.getItems().add(freeItem);
                ctx.lineRows.put(freeItem, List.of(firstRow + componentRows));
            }
        }
    }

    /** Product's VAT rate when VAT is on, else null. Cached per order. */
    private BigDecimal taxRateFor(CreationContext ctx, Product product) {
        if (!ctx.vatOn || product.getTaxRateId() == null) {
            return null;
        }
        return ctx.rateCache.computeIfAbsent(product.getTaxRateId(),
                id -> taxService.effectiveRates(List.of(id)).get(id));
    }

    private void validateCombo(Combo combo) {
        if (!Boolean.TRUE.equals(combo.getIsActive())) {
            throw new IllegalArgumentException("Combo '" + combo.getName() + "' is not active");
        }
        for (ComboItem ci : combo.getItems()) {
            if (!Boolean.TRUE.equals(ci.getProduct().getIsActive())) {
                throw new IllegalArgumentException("Combo '" + combo.getName() + "' contains inactive product: "
                        + ci.getProduct().getName() + ". Remove or replace it before ordering.");
            }
        }
        ComboRule rule = combo.getRule();
        if (rule != null && rule.getRuleType() == ComboRuleType.FREE_PRODUCT) {
            if (!Boolean.TRUE.equals(rule.getFreeProduct().getIsActive())) {
                throw new IllegalArgumentException("Combo '" + combo.getName() + "' free product '"
                        + rule.getFreeProduct().getName() + "' is no longer available.");
            }
        }
    }

    private BigDecimal calculateComboUnitPrice(Combo combo, ComboRule rule,
            OrderItemRequest itemRequest, BigDecimal comboPrice) {
        if (rule == null) return comboPrice;
        if (rule.getRuleType() == ComboRuleType.FREE_PRODUCT) {
            if (Boolean.TRUE.equals(itemRequest.getApplyFreeProductAsDiscount())) {
                return comboPrice.subtract(rule.getFreeProduct().getPrice()).max(BigDecimal.ZERO);
            }
            return comboPrice;
        }
        if (rule.getRuleType() == ComboRuleType.DISCOUNT) {
            return switch (rule.getDiscountType()) {
                case "FIXED" -> comboPrice.subtract(rule.getDiscountValue()).max(BigDecimal.ZERO);
                case "PERCENTAGE" -> comboPrice.multiply(
                        BigDecimal.ONE.subtract(rule.getDiscountValue()
                                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)))
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                default -> comboPrice;
            };
        }
        return comboPrice;
    }

    private void recordPostSaveOperations(Order savedOrder, CreationContext ctx, OrderRequest request) {
        recordSoldLots(ctx);
        if (ctx.customer != null) {
            ledgerService.recordCustomerInvoice(
                    ctx.customer.getId(), savedOrder.getId(), savedOrder.getTotalAmount(),
                    "Order " + savedOrder.getOrderNumber(), ctx.currentUser.getId());
        }

        if (ctx.customer != null && savedOrder.getPaidAmount() != null
                && savedOrder.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            Payment initialPayment = new Payment();
            initialPayment.setOrderId(savedOrder.getId());
            initialPayment.setAmount(savedOrder.getPaidAmount());
            initialPayment.setPaymentMethod(savedOrder.getPaymentMethod());
            initialPayment.setChequeNumber(request.getChequeNumber());
            initialPayment.setProofFileUrl(request.getProofFileUrl());
            initialPayment.setRecordedBy(securityService.getCurrentUserId());
            initialPayment.setRecordedByName(securityService.getCurrentUserEmail());
            paymentRepository.save(initialPayment);

            // Upfront money must reach the receivable ledger, otherwise a fully paid order
            // still shows as owed once outstanding is re-synced from the ledger.
            ledgerService.recordCustomerPayment(
                    ctx.customer.getId(), initialPayment.getId(), savedOrder.getPaidAmount(),
                    "Upfront payment for Order " + savedOrder.getOrderNumber() + " via " + savedOrder.getPaymentMethod(),
                    ctx.currentUser.getId());
            moneyAccountService.recordMovement(com.ebee.finance.entity.AccountTransaction.Direction.IN, savedOrder.getPaidAmount(),
                    savedOrder.getPaymentMethod() != null ? savedOrder.getPaymentMethod().name() : null,
                    request.getAccountId(), com.ebee.finance.entity.AccountTransaction.Type.CUSTOMER_RECEIPT,
                    "PAYMENT", initialPayment.getId(), "Upfront payment for Order " + savedOrder.getOrderNumber());
        }

        if (ctx.customer != null) {
            BigDecimal previousOutstanding = ctx.customer.getOutstandingBalance();
            BigDecimal previousAdvance = ctx.customer.getAdvanceBalance();
            orderPaymentService.syncCustomerOutstanding(ctx.customer);
            customerRepository.save(ctx.customer);
            eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                    this, ctx.customer,
                    previousOutstanding, ctx.customer.getOutstandingBalance(),
                    previousAdvance, ctx.customer.getAdvanceBalance(),
                    DuesChangeType.ORDER_CREATED));
        }

        vatService.post(com.ebee.tax.entity.VatEntry.Direction.OUTPUT, com.ebee.tax.entity.VatEntry.SourceType.ORDER,
                savedOrder.getId(), savedOrder.getOrderNumber(),
                savedOrder.getTotalAmount().subtract(savedOrder.getTaxTotal()), savedOrder.getTaxTotal());

        Commission commission = null;
        if (ctx.agent != null) {
            commission = commissionCalculationService.calculateCommissionForOrder(savedOrder);
        }

        auditLogService.record(AuditAction.CREATE, "Order", savedOrder.getId(),
                "Order " + savedOrder.getOrderNumber() + " created for "
                + (ctx.customer != null ? OrderItemSummaryBuilder.customerDisplayName(ctx.customer) : "walk-in")
                + " via " + savedOrder.getOrderSource());

        if (ctx.customer != null) {
            customerService.incrementTotalOrders(ctx.customer.getId());
        }

        orderNotificationService.sendOrderCreated(savedOrder, commission);
        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, savedOrder, null, LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    @Override
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithDetails(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return orderMapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByAgentId(UUID agentId) {
        agentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "id", agentId));
        List<Order> orders = orderRepository.findByAgentIdWithDetails(agentId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getMyOrders() {
        UUID userId = securityService.getCurrentUserId();
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent", "userId", userId));
        List<Order> orders = orderRepository.findByAgentIdWithDetails(agent.getId());
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        customerService.getCustomerById(customerId);
        List<Order> orders = orderRepository.findByCustomerIdWithDetails(customerId);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId, List<PaymentStatus> paymentStatuses) {
        List<OrderResponse> allOrders = getOrdersByCustomerId(customerId);
        if (paymentStatuses == null || paymentStatuses.isEmpty()) return allOrders;
        Set<PaymentStatus> statuses = EnumSet.copyOf(paymentStatuses);
        return allOrders.stream()
                .filter(o -> statuses.contains(o.getPaymentStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAllWithDetails();
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getUnassignedOrders() {
        List<Order> orders = orderRepository.findByTripIsNull();
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getUnassignedOrdersByStatus(OrderStatus status) {
        List<Order> orders = orderRepository.findByTripIsNullAndStatus(status);
        return orderMapper.toResponseList(orders);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OrderResponse> getUnassignedOrdersIncludingFailed() {
        List<Order> shipped = orderRepository.findByTripIsNullAndStatus(OrderStatus.SHIPPED);
        return orderMapper.toResponseList(shipped);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<OrderResponse> getUnassignedOrdersPaginated(Pageable pageable) {
        Page<Order> ordersPage = orderRepository.findUnassignedOrdersPaged(pageable);
        return PageResponse.from(ordersPage.map(orderMapper::toResponse));
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getAllOrdersPaginated(Pageable pageable) {
        Page<Order> orders = orderRepository.findAllWithDetails(pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getOrdersByStatusPaginated(OrderStatus status, Pageable pageable) {
        Page<Order> orders = orderRepository.findByStatusWithDetails(status, pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public Page<OrderResponse> getCustomerOrdersPaginated(UUID customerId, Pageable pageable) {
        customerService.getCustomerById(customerId);
        Page<Order> orders = orderRepository.findByCustomerIdWithDetailsFull(customerId, pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Transactional
    @Override
    public OrderResponse updateOrderStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot update status of a " + order.getStatus().name().toLowerCase() + " order");
        }

        if (status == OrderStatus.DELIVERED
                || status == OrderStatus.RETURNED
                || status == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Use delivery or cancel endpoints after shipping");
        }
        validateForwardTransition(order, status);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(status);
        if (status == OrderStatus.SHIPPED && order.getDeliveryStatus() == null) {
            order.setDeliveryStatus(DeliveryStatus.PENDING);
            order.setDeliveryFailureReason(null);
            order.setDeliveryPhotoUrl(null);
            order.setDeliverySignatureUrl(null);
            order.setDeliveryScheduledDate(null);
            order.setDeliveryNotes(null);
            order.setTrip(null);
            order.setDeliveryAssignedTo(null);
        }
        Order updatedOrder = orderRepository.save(order);

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " status changed from "
                + oldStatus + " to " + status);

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    /** Fulfilment order a manual status update may move along (forward only; steps may be skipped). */
    private static final List<OrderStatus> FULFILMENT_STEPS =
            List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.SHIPPED);

    /**
     * Manual updates only move forward: PENDING → CONFIRMED → PROCESSING → SHIPPED. Going back
     * (e.g. SHIPPED → PENDING while the order is on a trip) would desync delivery and stock.
     * Exception kept from before: SHIPPED again on a shipped order whose delivery record was
     * cleared, which re-initialises its delivery so it can be put on a trip.
     */
    private static void validateForwardTransition(Order order, OrderStatus target) {
        OrderStatus current = order.getStatus();
        if (current == OrderStatus.SHIPPED && target == OrderStatus.SHIPPED && order.getDeliveryStatus() == null) {
            return;
        }
        int from = FULFILMENT_STEPS.indexOf(current);
        int to = FULFILMENT_STEPS.indexOf(target);
        if (to < 0) {
            throw new IllegalArgumentException("Status " + target + " can't be set manually");
        }
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("Order " + order.getOrderNumber() + " can't go from " + current
                    + " to " + target + "; order status only moves forward (PENDING → CONFIRMED → PROCESSING → SHIPPED)");
        }
    }

    @Transactional
    @Override
    public OrderResponse cancelOrder(UUID id, OrderSettlementRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order is already cancelled");
        }

        if (order.getStatus() == OrderStatus.SHIPPED
                || order.getStatus() == OrderStatus.DELIVERED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot cancel a " + order.getStatus().name().toLowerCase() + " order");
        }

        boolean stockAlreadyRestored = order.getDeliveryStatus() == DeliveryStatus.FAILED_PERMANENT;

        commissionLifecycleService.cancelCommissionForOrder(id);

        BigDecimal previousOutstanding = order.getCustomer().getOutstandingBalance();
        BigDecimal previousAdvance = order.getCustomer().getAdvanceBalance();

        OrderSettlementService.Settlement settlement = orderSettlementService.cancel(
                order, request.refundChoice(), !stockAlreadyRestored, "Order cancelled");

        DuesChangeType changeType = !settlement.moneyReturned() ? DuesChangeType.CANCEL_UNPAID
                : settlement.toAdvance().signum() > 0 ? DuesChangeType.CANCEL_ADVANCE
                : DuesChangeType.CANCEL_REFUND;

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        customerRepository.save(order.getCustomer());

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, order.getCustomer(),
                previousOutstanding, order.getCustomer().getOutstandingBalance(),
                previousAdvance, order.getCustomer().getAdvanceBalance(),
                changeType
        ));

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " cancelled");

        orderNotificationService.sendOrderCancelled(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional
    @Override
    public OrderResponse returnOrder(UUID id, ReturnOrderItemRequest request) {
        Order order = orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (order.getStatus() == OrderStatus.RETURNED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot return an order that is already " + order.getStatus());
        }
        // Same rule as return/exchange requests: only goods the customer actually received come back.
        // Orders that haven't been delivered yet are cancelled instead.
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Only delivered orders can be returned (order " + order.getOrderNumber()
                    + " is " + order.getStatus() + "); cancel it instead");
        }

        Map<UUID, OrderItem> orderItemMap = order.getItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, oi -> oi));

        List<OrderSettlementService.ReturnLine> lines = new ArrayList<>();
        if (request.items() == null || request.items().isEmpty()) {
            order.getItems().stream()
                    .filter(oi -> oi.getReturnableQuantity() > 0)
                    .forEach(oi -> lines.add(new OrderSettlementService.ReturnLine(oi, oi.getReturnableQuantity())));
        } else {
            for (ReturnItemRequest itemReq : request.items()) {
                OrderItem orderItem = orderItemMap.get(itemReq.orderItemId());
                if (orderItem == null) {
                    throw new IllegalArgumentException("Order item " + itemReq.orderItemId() + " does not belong to this order");
                }
                if (orderItem.getCombo() != null && itemReq.quantity() != orderItem.getQuantity()) {
                    throw new IllegalArgumentException("Combo items must be returned in full quantity");
                }
                lines.add(new OrderSettlementService.ReturnLine(orderItem, itemReq.quantity()));
            }
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Nothing left to return on order " + order.getOrderNumber());
        }

        Customer customer = order.getCustomer();
        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();

        OrderSettlementService.Settlement settlement = orderSettlementService.returnLines(
                order, lines, request.refundChoice(), "Order return");
        // Re-base commission on the reduced total (cancels it when everything came back).
        commissionLifecycleService.adjustCommissionForOrder(order);

        DuesChangeType changeType = !settlement.moneyReturned() ? DuesChangeType.CANCEL_UNPAID
                : settlement.toAdvance().signum() > 0 ? DuesChangeType.RETURN_ADVANCE
                : DuesChangeType.RETURN_REFUND;

        boolean allReturned = order.getItems().stream().allMatch(OrderItem::isFullyReturned);
        OrderStatus oldStatus = order.getStatus();
        if (allReturned) {
            order.setStatus(OrderStatus.RETURNED);
        }

        Order updatedOrder = orderRepository.save(order);
        customerRepository.save(customer);

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                changeType
        ));

        auditLogService.record(AuditAction.STATUS_CHANGE, "Order", updatedOrder.getId(),
                "Order " + updatedOrder.getOrderNumber() + " returned");

        orderNotificationService.sendOrderStatus(updatedOrder);

        eventPublisher.publishEvent(new OrderStatusChangedEvent(this, updatedOrder, oldStatus, LocalDateTime.now()));

        return orderMapper.toResponse(updatedOrder);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateInvoicePdf(UUID id) {
        return orderPdfService.generateInvoicePdf(id);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateReceiptPdf(UUID id) {
        return orderPdfService.generateReceiptPdf(id);
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] generateShippingLabelPdf(UUID id) {
        return orderPdfService.generateShippingLabelPdf(id);
    }

    @Transactional
    @Override
    public OrderResponse recordPayment(UUID id, RecordPaymentRequest request) {
        return orderPaymentService.recordPayment(id, request);
    }

    @Override
    public List<PaymentResponse> getPaymentHistory(UUID orderId) {
        return orderPaymentService.getPaymentHistory(orderId);
    }

    @Transactional
    @Override
    public OrderResponse useAdvance(UUID orderId, UseAdvanceRequest request) {
        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Customer customer = order.getCustomer();
        if (customer == null) {
            throw new IllegalArgumentException("Order has no customer assigned");
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.RETURNED) {
            throw new IllegalArgumentException("Cannot use advance on a " + order.getStatus().name().toLowerCase() + " order");
        }

        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();

        orderPaymentService.useAdvanceForOrder(order, request.amount());

        Order updatedOrder = orderRepository.save(order);
        customerRepository.save(customer);

        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                DuesChangeType.ADVANCE_USED
        ));

        auditLogService.record(AuditAction.UPDATE, "Order", order.getId(),
                "Used advance " + request.amount() + " for order " + order.getOrderNumber());

        return orderMapper.toResponse(updatedOrder);
    }

    @Recover
    public OrderResponse recoverDuplicateOrderNumber(DuplicateOrderNumberException ex, OrderRequest request) {
        log.error("Failed to create order after retries: {}", ex.getMessage());
        throw new IllegalStateException("Couldn't generate order number. Try again.", ex);
    }

    /**
     * Every other failure in createOrder (out of stock, credit limit, missing district…) is passed
     * through unchanged. Without this, Spring Retry looks for a matching @Recover method, finds
     * none, and wraps the error in ExhaustedRetryException("Cannot locate recovery method"), so the
     * API answered 500 instead of the real 400 message.
     */
    @Recover
    public OrderResponse rethrowOrderFailure(RuntimeException ex, OrderRequest request) {
        throw ex;
    }

    private static boolean isDuplicateOrderNumber(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause == null) return false;
        String msg = cause.getMessage();
        return msg != null && (msg.contains("uk_orders_order_number") || msg.contains("orders_order_number_key"));
    }
}
