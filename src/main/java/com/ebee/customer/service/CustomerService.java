package com.ebee.customer.service;

import com.ebee.accounting.entity.CustomerLedger;
import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.result.Result;
import com.ebee.common.security.SecurityService;
import com.ebee.common.service.CodeGenerationService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.customer.dto.AddressRequest;
import com.ebee.customer.dto.AddressResponse;
import com.ebee.customer.dto.AdvanceTransactionResponse;
import com.ebee.customer.dto.AdjustAdvanceRequest;
import com.ebee.customer.dto.AdjustOutstandingRequest;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.OutstandingAdjustmentResponse;
import com.ebee.customer.dto.CustomerPaymentResponse;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.entity.CustomerAdvanceTransaction;
import com.ebee.customer.enums.AdvanceReferenceType;
import com.ebee.customer.enums.AdvanceTransactionType;
import com.ebee.customer.enums.DuesChangeType;
import com.ebee.customer.event.CustomerDuesChangedEvent;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAdvanceTransactionRepository advanceTransactionRepository;
    private final NotificationPort notificationPort;
    private final OrderPaymentService orderPaymentService;
    private final OrderRepository orderRepository;
    private final CodeGenerationService codeGenerationService;
    private final ApplicationEventPublisher eventPublisher;
    private final LedgerService ledgerService;
    private final CustomerLedgerRepository customerLedgerRepository;
    private final SecurityService securityService;
    private final com.ebee.finance.service.MoneyAccountService moneyAccountService;

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        return createCustomer(request, true);
    }

    /** {@code notify=false} skips the welcome SMS/email (bulk imports). */
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request, boolean notify) {
        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (customerRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("A customer with this phone already exists");
        }

        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .bookNo(request.getBookNo())
                .vatNumber(blankToNull(request.getVatNumber()))
                .customerCode(generateCustomerCode(request))
                .build();

        if (request.getAddress() != null) {
            customer.setAddress(buildAddress(request.getAddress(), customer));
        }

        customer = customerRepository.save(customer);

        if (notify) {
            sendWelcomeNotification(customer);
        }

        return toResponse(customer);
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (request.getFirstName() != null) {
            customer.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            customer.setLastName(request.getLastName());
        }
        if (request.getPhoneNumber() != null &&
            !customer.getPhoneNumber().equals(request.getPhoneNumber()) &&
            customerRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("A customer with this phone already exists");
        }
        if (request.getPhoneNumber() != null) {
            customer.setPhoneNumber(request.getPhoneNumber());
        }
        if (Boolean.TRUE.equals(request.getClearEmail())) {
            customer.setEmail(null);
        } else if (request.getEmail() != null) {
            customer.setEmail(request.getEmail().isEmpty() ? null : request.getEmail());
        }
        if (request.getVatNumber() != null) {
            customer.setVatNumber(blankToNull(request.getVatNumber()));
        }
        if (request.getBookNo() != null) {
            customer.setBookNo(request.getBookNo());
        }

        if (request.getAddress() != null) {
            if (customer.getAddress() != null) {
                updateAddress(customer.getAddress(), request.getAddress());
            } else {
                customer.setAddress(buildAddress(request.getAddress(), customer));
            }
        }

        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    @Transactional
    public Customer findOrCreateByPhone(String phoneNumber, String firstName, String lastName) {
        String normalized = PhoneUtils.normalize(phoneNumber);
        return customerRepository.findByPhoneNumber(normalized)
                .orElseGet(() -> {
                    Customer customer = Customer.builder()
                            .firstName(firstName)
                            .lastName(lastName)
                            .phoneNumber(normalized)
                            .customerCode(codeGenerationService.generateCode(null))
                            .build();
                    customer = customerRepository.save(customer);
                    sendWelcomeNotification(customer);
                    return customer;
                });
    }

    @Transactional(readOnly = true)
    public Customer findByPhone(String phoneNumber) {
        String normalized = PhoneUtils.normalize(phoneNumber);
        return customerRepository.findByPhoneNumber(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "phoneNumber", phoneNumber));
    }

    @Transactional(readOnly = true)
    public Result<CustomerResponse> getCustomerById(UUID id) {
        return customerRepository.findById(id)
                .map(c -> Result.<CustomerResponse>ok(toResponse(c)))
                .orElseGet(() -> Result.<CustomerResponse>notFound("Customer", "id", id));
    }

    @Transactional(readOnly = true)
    public Result<CustomerResponse> getCustomerByCode(String customerCode) {
        return customerRepository.findByCustomerCode(customerCode)
                .map(c -> Result.<CustomerResponse>ok(toResponse(c)))
                .orElseGet(() -> Result.<CustomerResponse>notFound("Customer", "customerCode", customerCode));
    }

    @Transactional(readOnly = true)
    public Result<CustomerResponse> getCustomerByPhone(String phoneNumber) {
        String normalized = PhoneUtils.normalize(phoneNumber);
        return customerRepository.findByPhoneNumber(normalized)
                .map(c -> Result.<CustomerResponse>ok(toResponse(c)))
                .orElseGet(() -> Result.<CustomerResponse>notFound("Customer", "phoneNumber", phoneNumber));
    }

    @Transactional(readOnly = true)
    public Result<CustomerResponse> getCustomerByBookNo(String bookNo) {
        return customerRepository.findByBookNo(bookNo)
                .map(c -> Result.<CustomerResponse>ok(toResponse(c)))
                .orElseGet(() -> Result.<CustomerResponse>notFound("Customer", "bookNo", bookNo));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomersPaginated(Pageable pageable) {
        Page<Customer> customers = customerRepository.findAll(pageable);
        return customers.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> searchCustomersPaginated(String search, Pageable pageable) {
        Page<Customer> customers = customerRepository.findBySearchTerm(search, pageable);
        return customers.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomers(String search) {
        return customerRepository.findBySearchTerm(search).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void incrementTotalOrders(UUID customerId) {
        customerRepository.incrementTotalOrders(customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersWithDues() {
        return customerRepository
                .findByOutstandingBalanceGreaterThan(BigDecimal.ZERO, Sort.by(Sort.Direction.DESC, "outstandingBalance"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void syncCustomerOutstanding(Customer customer) {
        BigDecimal actual = customerLedgerRepository.calculateBalance(customer.getId());
        customer.setOutstandingBalance(actual);
    }

    public void syncAdvanceBalance(Customer customer) {
        BigDecimal actual = advanceTransactionRepository.sumAdvanceByCustomerId(customer.getId());
        customer.setAdvanceBalance(actual);
    }

    public void syncAllBalances(Customer customer) {
        syncCustomerOutstanding(customer);
        syncAdvanceBalance(customer);
    }

    public void syncAndPublishDuesChange(Customer customer, DuesChangeType changeType) {
        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal previousAdvance = customer.getAdvanceBalance();
        syncCustomerOutstanding(customer);
        customerRepository.save(customer);
        publishDuesChange(customer, previousOutstanding, previousAdvance, changeType);
    }

    public void publishDuesChange(Customer customer, BigDecimal previousOutstanding,
                                  BigDecimal previousAdvance, DuesChangeType changeType) {
        eventPublisher.publishEvent(new CustomerDuesChangedEvent(
                this, customer,
                previousOutstanding, customer.getOutstandingBalance(),
                previousAdvance, customer.getAdvanceBalance(),
                changeType
        ));
    }

    public BigDecimal getAdvanceBalance(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        return customer.getAdvanceBalance();
    }

    @Transactional(readOnly = true)
    public Page<AdvanceTransactionResponse> getAdvanceHistory(UUID customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return advanceTransactionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(this::toAdvanceTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustomerLedger> getCustomerLedger(UUID customerId, Pageable pageable) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return customerLedgerRepository.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<CustomerLedger> getCustomerLedgerFiltered(UUID customerId, String entryType,
                                                          LocalDateTime startDate, LocalDateTime endDate) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        List<CustomerLedger> allEntries = customerLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        return allEntries.stream()
                .filter(e -> entryType == null || e.getTransactionType().equals(entryType))
                .filter(e -> startDate == null || !e.getCreatedAt().isBefore(startDate))
                .filter(e -> endDate == null || !e.getCreatedAt().isAfter(endDate))
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getCustomerLedgerBalance(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return customerLedgerRepository.calculateBalance(customerId);
    }

    @Transactional
    public CustomerAdvanceTransaction creditToAdvance(Customer customer, BigDecimal amount,
                                                       AdvanceReferenceType refType, UUID referenceId,
                                                       String description) {
        BigDecimal previousAdvance = customer.getAdvanceBalance();
        BigDecimal newBalance = previousAdvance.add(amount);

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .amount(amount)
                .balanceAfter(newBalance)
                .transactionType(AdvanceTransactionType.ADVANCE_CREDIT)
                .referenceType(refType)
                .referenceId(referenceId)
                .description(description)
                .build();

        customer.setAdvanceBalance(newBalance);
        customerRepository.save(customer);

        // Wallet only: the advance balance is not part of the receivable ledger. When the credit
        // comes from an overpaid order, OrderSettlementService posts the matching ADVANCE_TRANSFER.

        publishDuesChange(customer, customer.getOutstandingBalance(), previousAdvance,
                DuesChangeType.ADVANCE_CREDITED);

        return advanceTransactionRepository.save(transaction);
    }

    @Transactional
    public CustomerAdvanceTransaction debitFromAdvance(Customer customer, BigDecimal amount,
                                                        AdvanceReferenceType refType, UUID referenceId,
                                                        String description) {
        if (customer.getAdvanceBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient advance balance");
        }

        BigDecimal previousAdvance = customer.getAdvanceBalance();
        BigDecimal newBalance = previousAdvance.subtract(amount);

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .amount(amount.negate())
                .balanceAfter(newBalance)
                .transactionType(AdvanceTransactionType.ADVANCE_USED)
                .referenceType(refType)
                .referenceId(referenceId)
                .description(description)
                .build();

        customer.setAdvanceBalance(newBalance);
        customerRepository.save(customer);

        publishDuesChange(customer, customer.getOutstandingBalance(), previousAdvance,
                DuesChangeType.ADVANCE_USED);

        return advanceTransactionRepository.save(transaction);
    }

    @Transactional
    public CustomerAdvanceTransaction manualAdjustAdvance(UUID customerId, AdjustAdvanceRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        BigDecimal previousAdvance = customer.getAdvanceBalance();
        BigDecimal newBalance = previousAdvance.add(request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Advance balance cannot be negative");
        }

        CustomerAdvanceTransaction transaction = CustomerAdvanceTransaction.builder()
                .customer(customer)
                .amount(request.amount())
                .balanceAfter(newBalance)
                .transactionType(AdvanceTransactionType.MANUAL_ADJUSTMENT)
                .referenceType(AdvanceReferenceType.ADMIN_ADJUSTMENT)
                .description(request.description())
                .build();

        customer.setAdvanceBalance(newBalance);
        customerRepository.save(customer);
        advanceTransactionRepository.save(transaction);
        // Wallet only: manual advance changes don't touch the receivable ledger.

        publishDuesChange(customer, customer.getOutstandingBalance(), previousAdvance,
                DuesChangeType.MANUAL_ADJUSTMENT);

        return transaction;
    }

    @Transactional
    public OutstandingAdjustmentResponse manualAdjustOutstanding(UUID customerId, AdjustOutstandingRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        BigDecimal previousOutstanding = customer.getOutstandingBalance();
        BigDecimal amountAdded = request.amount();
        BigDecimal newOutstanding = previousOutstanding.add(amountAdded);

        if (newOutstanding.compareTo(BigDecimal.ZERO) < 0) {
            newOutstanding = BigDecimal.ZERO;
        }

        customer.setOutstandingBalance(newOutstanding);
        customerRepository.save(customer);

        ledgerService.recordCustomerAdjustment(
                customerId,
                amountAdded,
                request.date(),
                request.description(),
                "Outstanding Adjustment",
                securityService.getCurrentUserId()
        );

        publishDuesChange(customer, previousOutstanding, customer.getAdvanceBalance(),
                DuesChangeType.MANUAL_ADJUSTMENT);

        return new OutstandingAdjustmentResponse(
                customerId,
                previousOutstanding,
                newOutstanding,
                amountAdded,
                request.date(),
                request.description()
        );
    }

    @Transactional
    public CustomerResponse updateCreditLimit(UUID id, CreditLimitRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customer.setCreditLimit(request.creditLimit());
        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    @Transactional
    public CustomerPaymentResponse recordCustomerPayment(UUID customerId, RecordPaymentRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        List<Order> unpaidOrders = orderRepository.findByCustomerIdAndPaymentStatusInWithDetails(
                customerId, List.of(PaymentStatus.UNPAID, PaymentStatus.PARTIALLY_PAID));

        BigDecimal remaining = request.amount();
        int settledCount = 0;

        if (!unpaidOrders.isEmpty()) {
            for (Order order : unpaidOrders) {
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
                BigDecimal payAmount = remaining.min(order.getOutstandingBalance());
                orderPaymentService.recordPayment(order.getId(),
                        new RecordPaymentRequest(payAmount, request.paymentMethod(), request.chequeNumber(),
                                request.proofFileUrl(), request.accountId()), false);
                remaining = remaining.subtract(payAmount);
                settledCount++;
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal outstanding = customerLedgerRepository.calculateBalance(customerId);
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal adjustmentPayment = remaining.min(outstanding);
                ledgerService.recordCustomerPayment(
                        customerId,
                        null,
                        adjustmentPayment,
                        "Payment for outstanding adjustment via " + request.paymentMethod(),
                        securityService.getCurrentUserId()
                );
                remaining = remaining.subtract(adjustmentPayment);
                moneyAccountService.recordMovement(com.ebee.finance.entity.AccountTransaction.Direction.IN, adjustmentPayment, request.paymentMethod().name(),
                        request.accountId(), com.ebee.finance.entity.AccountTransaction.Type.CUSTOMER_RECEIPT, "CUSTOMER", customerId,
                        "Account payment via " + request.paymentMethod());
            }
        }

        customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        syncCustomerOutstanding(customer);
        customer = customerRepository.save(customer);

        return new CustomerPaymentResponse(
                customer.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                request.amount().subtract(remaining),
                settledCount,
                customer.getOutstandingBalance()
        );
    }

    private Address buildAddress(AddressRequest request, Customer customer) {
        Address.AddressBuilder builder = Address.builder()
                .customer(customer)
                .street(request.getStreet())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .postalCode(request.getPostalCode())
                .country(request.getCountry() != null ? request.getCountry() : "Sri Lanka");

        if (request.getDistrict() != null) {
            builder.district(District.fromDisplayName(request.getDistrict()));
        }

        return builder.build();
    }

    private void updateAddress(Address address, AddressRequest request) {
        address.setStreet(request.getStreet());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        if (request.getDistrict() != null) {
            address.setDistrict(District.fromDisplayName(request.getDistrict()));
        }
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry() != null ? request.getCountry() : "Sri Lanka");
    }

    private void sendWelcomeNotification(Customer customer) {
        try {
            String subject = "Welcome to Ebee!";
            String addr = customer.getAddress() != null
                    ? formatAddressBlock(customer.getAddress())
                    : "";
            Map<String, String> p = new HashMap<>();
            p.put("firstName", customer.getFirstName());
            p.put("lastName", customer.getLastName());
            p.put("addressBlock", addr.isEmpty() ? "" : "<p>Address:<br>" + addr.replace("\n", "<br>") + "</p>");
            p.put("addressBlockTxt", addr.isEmpty() ? "" : "Address: " + addr.replace("\n", ", "));
            notificationPort.notifyCustomer(customer, subject, "customer-welcome", p, Set.of("addressBlock"));
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to customer {}: {}", customer.getPhoneNumber(), e.getMessage());
        }
    }

    private String formatAddressBlock(Address a) {
        StringBuilder sb = new StringBuilder();
        sb.append(a.getStreet());
        if (a.getAddressLine2() != null) sb.append("\n").append(a.getAddressLine2());
        sb.append("\n").append(a.getCity());
        if (a.getPostalCode() != null) sb.append(" ").append(a.getPostalCode());
        sb.append("\n").append(a.getDistrict().getDisplayName());
        if (a.getCountry() != null) sb.append("\n").append(a.getCountry());
        return sb.toString();
    }

    private String generateCustomerCode(CustomerRequest request) {
        District district = null;
        if (request.getAddress() != null && request.getAddress().getDistrict() != null) {
            try {
                district = District.fromDisplayName(request.getAddress().getDistrict());
            } catch (IllegalArgumentException e) {
                // ignore, use null -> XX prefix
            }
        }
        return codeGenerationService.generateCode(district);
    }

    private CustomerResponse toResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setCustomerCode(customer.getCustomerCode());
        response.setFirstName(customer.getFirstName());
        response.setLastName(customer.getLastName());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setEmail(customer.getEmail());
        response.setBookNo(customer.getBookNo());
        response.setVatNumber(customer.getVatNumber());
        response.setPriceGroupId(customer.getPriceGroupId());
        response.setTotalOrders(customer.getTotalOrders());
        response.setCreditLimit(customer.getCreditLimit());
        response.setOutstandingBalance(customer.getOutstandingBalance());
        response.setAdvanceBalance(customer.getAdvanceBalance());
        if (customer.getAddress() != null) {
            AddressResponse addrResponse = new AddressResponse();
            addrResponse.setId(customer.getAddress().getId());
            addrResponse.setStreet(customer.getAddress().getStreet());
            addrResponse.setAddressLine2(customer.getAddress().getAddressLine2());
            addrResponse.setCity(customer.getAddress().getCity());
            addrResponse.setDistrict(customer.getAddress().getDistrict() != null
                    ? customer.getAddress().getDistrict().getDisplayName()
                    : null);
            addrResponse.setPostalCode(customer.getAddress().getPostalCode());
            addrResponse.setCountry(customer.getAddress().getCountry());
            response.setAddress(addrResponse);
        }
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        return response;
    }

    private AdvanceTransactionResponse toAdvanceTransactionResponse(CustomerAdvanceTransaction tx) {
        return new AdvanceTransactionResponse(
                tx.getId(),
                tx.getCustomer().getId(),
                tx.getOrder() != null ? tx.getOrder().getId() : null,
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getTransactionType(),
                tx.getReferenceType(),
                tx.getReferenceId(),
                tx.getDescription(),
                tx.getCreatedAt()
        );
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
