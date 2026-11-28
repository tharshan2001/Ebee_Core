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
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final NotificationPort notificationPort;
    private final CodeGenerationService codeGenerationService;
    private final CustomerAccountService customerAccountService;

    // ── balances, advance wallet, ledger, payments (CustomerAccountService) ──

    public void syncCustomerOutstanding(Customer customer) {
        customerAccountService.syncCustomerOutstanding(customer);
    }

    public void syncAdvanceBalance(Customer customer) {
        customerAccountService.syncAdvanceBalance(customer);
    }

    public void syncAllBalances(Customer customer) {
        customerAccountService.syncAllBalances(customer);
    }

    public void syncAndPublishDuesChange(Customer customer, DuesChangeType changeType) {
        customerAccountService.syncAndPublishDuesChange(customer, changeType);
    }

    public void publishDuesChange(Customer customer, BigDecimal previousOutstanding, BigDecimal previousAdvance, DuesChangeType changeType) {
        customerAccountService.publishDuesChange(customer, previousOutstanding, previousAdvance, changeType);
    }

    public BigDecimal getAdvanceBalance(UUID customerId) {
        return customerAccountService.getAdvanceBalance(customerId);
    }

    public Page<AdvanceTransactionResponse> getAdvanceHistory(UUID customerId, Pageable pageable) {
        return customerAccountService.getAdvanceHistory(customerId, pageable);
    }

    public Page<CustomerLedger> getCustomerLedger(UUID customerId, Pageable pageable) {
        return customerAccountService.getCustomerLedger(customerId, pageable);
    }

    public List<CustomerLedger> getCustomerLedgerFiltered(UUID customerId, String entryType, LocalDateTime startDate, LocalDateTime endDate) {
        return customerAccountService.getCustomerLedgerFiltered(customerId, entryType, startDate, endDate);
    }

    public BigDecimal getCustomerLedgerBalance(UUID customerId) {
        return customerAccountService.getCustomerLedgerBalance(customerId);
    }

    public CustomerAdvanceTransaction creditToAdvance(Customer customer, BigDecimal amount, AdvanceReferenceType refType, UUID referenceId, String description) {
        return customerAccountService.creditToAdvance(customer, amount, refType, referenceId, description);
    }

    public CustomerAdvanceTransaction debitFromAdvance(Customer customer, BigDecimal amount, AdvanceReferenceType refType, UUID referenceId, String description) {
        return customerAccountService.debitFromAdvance(customer, amount, refType, referenceId, description);
    }

    public CustomerAdvanceTransaction manualAdjustAdvance(UUID customerId, AdjustAdvanceRequest request) {
        return customerAccountService.manualAdjustAdvance(customerId, request);
    }

    public OutstandingAdjustmentResponse manualAdjustOutstanding(UUID customerId, AdjustOutstandingRequest request) {
        return customerAccountService.manualAdjustOutstanding(customerId, request);
    }

    public CustomerPaymentResponse recordCustomerPayment(UUID customerId, RecordPaymentRequest request) {
        return customerAccountService.recordCustomerPayment(customerId, request);
    }

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
                .lastName(request.getLastName() != null ? request.getLastName() : "") // optional, never null
                .phoneNumber(request.getPhoneNumber())
                .phoneNumber2(secondPhone(request.getPhoneNumber2(), request.getPhoneNumber()))
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
        if (request.getPhoneNumber2() != null) {
            customer.setPhoneNumber2(secondPhone(request.getPhoneNumber2(), customer.getPhoneNumber()));
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

    @Transactional
    public CustomerResponse updateCreditLimit(UUID id, CreditLimitRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customer.setCreditLimit(request.creditLimit());
        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    private Address buildAddress(AddressRequest request, Customer customer) {
        Address.AddressBuilder builder = Address.builder()
                .customer(customer)
                .street(blankToNull(request.getStreet()))
                .addressLine2(blankToNull(request.getAddressLine2()))
                .city(blankToNull(request.getCity()))
                .postalCode(blankToNull(request.getPostalCode()))
                .country(request.getCountry() != null ? request.getCountry() : "Sri Lanka");

        if (request.getDistrict() != null) {
            builder.district(District.fromDisplayName(request.getDistrict()));
        }

        return builder.build();
    }

    private void updateAddress(Address address, AddressRequest request) {
        address.setStreet(blankToNull(request.getStreet()));
        address.setAddressLine2(blankToNull(request.getAddressLine2()));
        address.setCity(blankToNull(request.getCity()));
        if (request.getDistrict() != null) {
            address.setDistrict(District.fromDisplayName(request.getDistrict()));
        }
        address.setPostalCode(blankToNull(request.getPostalCode()));
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
        String town = Stream.of(a.getCity(), a.getPostalCode()).filter(Objects::nonNull).collect(Collectors.joining(" "));
        return Stream.of(a.getStreet(), a.getAddressLine2(), town, a.getDistrict().getDisplayName(), a.getCountry())
                .filter(line -> line != null && !line.isEmpty())
                .collect(Collectors.joining("\n"));
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
        response.setPhoneNumber2(customer.getPhoneNumber2());
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

    private static String secondPhone(String value, String primary) {
        if (blankToNull(value) == null) {
            return null;
        }
        String phone = PhoneUtils.normalize(value);
        if (phone.equals(primary)) {
            throw new IllegalArgumentException("Phone 2 must be different from the main phone number");
        }
        return phone;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
