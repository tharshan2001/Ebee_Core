package com.ebee.customer.service;

import com.ebee.common.enums.District;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.common.service.CodeGenerationService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.customer.dto.AddressRequest;
import com.ebee.customer.dto.AddressResponse;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.CustomerPaymentResponse;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.dto.CustomerResponse;
import com.ebee.customer.entity.Address;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.notification.NotificationPort;
import com.ebee.order.dto.RecordPaymentRequest;
import com.ebee.order.entity.Order;
import com.ebee.order.enums.PaymentStatus;
import com.ebee.order.repository.OrderRepository;
import com.ebee.order.service.OrderPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final NotificationPort notificationPort;
    private final OrderPaymentService orderPaymentService;
    private final OrderRepository orderRepository;
    private final CodeGenerationService codeGenerationService;

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        request.setPhoneNumber(PhoneUtils.normalize(request.getPhoneNumber()));
        if (customerRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("A customer with this phone already exists");
        }

        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .customerCode(generateCustomerCode(request))
                .build();

        if (request.getAddress() != null) {
            customer.setAddress(buildAddress(request.getAddress(), customer));
        }

        customer = customerRepository.save(customer);

        sendWelcomeNotification(customer);

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
        if (request.getEmail() != null) {
            customer.setEmail(request.getEmail());
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
    public CustomerResponse getCustomerById(UUID id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByCode(String customerCode) {
        Customer customer = customerRepository.findByCustomerCode(customerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerCode", customerCode));
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerByPhone(String phoneNumber) {
        String normalized = PhoneUtils.normalize(phoneNumber);
        Customer customer = customerRepository.findByPhoneNumber(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "phoneNumber", phoneNumber));
        return toResponse(customer);
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
    public CustomerResponse recalculateCustomerOutstanding(UUID customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        BigDecimal actual = orderRepository.sumOutstandingByCustomerId(customerId);
        customer.setOutstandingBalance(actual);
        customer = customerRepository.save(customer);
        return toResponse(customer);
    }

    @Transactional
    public int recalculateAllCustomerOutstanding() {
        List<Customer> all = customerRepository.findAll();
        int corrected = 0;
        for (Customer customer : all) {
            BigDecimal actual = orderRepository.sumOutstandingByCustomerId(customer.getId());
            if (customer.getOutstandingBalance().compareTo(actual) != 0) {
                log.warn("Customer {} outstanding drift: recorded={} actual={}",
                        customer.getId(), customer.getOutstandingBalance(), actual);
                customer.setOutstandingBalance(actual);
                customerRepository.save(customer);
                corrected++;
            }
        }
        return corrected;
    }

    public void syncCustomerOutstanding(Customer customer) {
        BigDecimal actual = orderRepository.sumOutstandingByCustomerId(customer.getId());
        if (customer.getOutstandingBalance().compareTo(actual) != 0) {
            log.warn("Auto-correcting customer {} outstanding: recorded={} actual={}",
                    customer.getId(), customer.getOutstandingBalance(), actual);
            customer.setOutstandingBalance(actual);
        }
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

        if (unpaidOrders.isEmpty()) {
            throw new IllegalArgumentException("Customer has no unpaid orders");
        }

        BigDecimal remaining = request.amount();
        int settledCount = 0;

        for (Order order : unpaidOrders) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal payAmount = remaining.min(order.getOutstandingBalance());
            orderPaymentService.recordPayment(order.getId(),
                    new RecordPaymentRequest(payAmount, request.paymentMethod()));
            remaining = remaining.subtract(payAmount);
            settledCount++;
        }

        customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

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
        response.setTotalOrders(customer.getTotalOrders());
        response.setCreditLimit(customer.getCreditLimit());
        response.setOutstandingBalance(customer.getOutstandingBalance());
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
}
