package com.ebee.importer.service.importers;

import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.service.AccountingPeriodService;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.enums.District;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.customer.dto.AddressRequest;
import com.ebee.customer.dto.AdjustAdvanceRequest;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerAdvanceTransactionRepository;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.customer.service.CustomerService;
import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.RowImporter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates customers (no welcome SMS/email), or updates them when the phone number already exists:
 * blank cells leave the stored value, and the address on file is kept. New customers need what the
 * create form needs. An opening balance is posted only for a customer with no history: positive =
 * they owe you (ledger), negative = their advance.
 */
@Component
public class CustomerImporter implements RowImporter {

    static final String OPENING_BALANCE = "Opening balance (import)";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");
    private static final List<String> ADDRESS_COLUMNS = List.of("district", "city", "street");

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final CustomerLedgerRepository customerLedgerRepository;
    private final CustomerAdvanceTransactionRepository advanceRepository;
    private final LedgerService ledgerService;
    private final SecurityService securityService;
    private final AccountingPeriodService accountingPeriodService;

    public CustomerImporter(CustomerRepository customerRepository, CustomerService customerService,
                            CustomerLedgerRepository customerLedgerRepository,
                            CustomerAdvanceTransactionRepository advanceRepository, LedgerService ledgerService,
                            SecurityService securityService, AccountingPeriodService accountingPeriodService) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.customerLedgerRepository = customerLedgerRepository;
        this.advanceRepository = advanceRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
        this.accountingPeriodService = accountingPeriodService;
    }

    @Override
    public ImportType type() {
        return ImportType.CUSTOMERS;
    }

    private boolean hasHistory(UUID id) {
        return customerLedgerRepository.existsByCustomerId(id) || advanceRepository.existsByCustomerId(id);
    }

    @Override
    public Counts validate(List<ImportRow> rows, List<RowError> errors, List<RowError> notices) {
        Set<String> seenPhones = new HashSet<>();
        Set<String> seenBookNos = new HashSet<>();
        boolean periodBlocked = accountingPeriodService.isCurrentPeriodBlocked();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (ImportRow row : rows) {
            int n = row.rowNumber();
            String phone = row.requiredText("phone", errors);
            row.requiredText("first_name", errors);
            String district = row.text("district");
            if (district != null && !District.isValid(district)) {
                errors.add(new RowError(n, "district", "'" + district + "' isn't a supported district"));
            }
            row.maxLength("first_name", 100, errors);
            row.maxLength("last_name", 100, errors);
            row.maxLength("street", 255, errors);
            row.maxLength("address_line2", 255, errors);
            row.maxLength("city", 100, errors);
            row.maxLength("postal_code", 20, errors);
            row.maxLength("email", 255, errors);
            row.maxLength("vat_number", 50, errors);
            row.maxLength("book_no", 50, errors);
            BigDecimal limit = row.decimal("credit_limit", false, false, errors);
            row.maxValue("credit_limit", limit, MAX_AMOUNT, errors);
            BigDecimal opening = row.decimal("opening_balance", false, true, errors);
            row.maxValue("opening_balance", opening, MAX_AMOUNT, errors);
            String email = row.text("email");
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                errors.add(new RowError(n, "email", "'" + email + "' isn't an email address"));
            }

            Optional<Customer> existing = Optional.empty();
            if (phone != null) {
                String normalized = PhoneUtils.normalize(phone);
                if (normalized == null || !normalized.matches("^0\\d{9}$")) {
                    errors.add(new RowError(n, "phone", "'" + phone + "' isn't a 10-digit Sri Lankan number"));
                } else {
                    if (!seenPhones.add(normalized)) {
                        errors.add(new RowError(n, "phone", "'" + phone + "' appears more than once in the file"));
                    }
                    existing = customerRepository.findByPhoneNumber(normalized);
                }
            }

            String bookNo = row.text("book_no");
            if (bookNo != null) {
                UUID self = existing.map(Customer::getId).orElse(null);
                if (!seenBookNos.add(bookNo)) {
                    errors.add(new RowError(n, "book_no", "'" + bookNo + "' appears more than once in the file"));
                } else if (customerRepository.findByBookNo(bookNo).filter(c -> !c.getId().equals(self)).isPresent()) {
                    errors.add(new RowError(n, "book_no", "'" + bookNo + "' belongs to another customer"));
                }
            }

            if (existing.isPresent()) {
                updated++;
                Customer c = existing.get();
                String name = (c.getFirstName() + " " + (c.getLastName() == null ? "" : c.getLastName())).trim();
                notices.add(new RowError(n, "phone", "will update the existing customer " + name));
                if (ADDRESS_COLUMNS.stream().anyMatch(col -> row.text(col) != null)) {
                    notices.add(new RowError(n, "street", "the address on file is kept; change it on the customer's page"));
                }
            } else {
                created++;
                ADDRESS_COLUMNS.forEach(col -> row.requiredText(col, errors));
                if (row.text("last_name") == null) {
                    errors.add(new RowError(n, "last_name", "is required for a new customer"));
                }
            }

            if (opening != null && opening.signum() != 0) {
                if (existing.isPresent() && hasHistory(existing.get().getId())) {
                    skipped++;
                    notices.add(new RowError(n, "opening_balance", "not posted: this customer already has transactions"));
                } else if (opening.signum() > 0 && periodBlocked) {
                    errors.add(new RowError(n, "opening_balance",
                            "this month's accounting period is closed; reopen it before importing balances"));
                }
            }
        }
        return new Counts(created, updated, skipped);
    }

    @Override
    public Counts apply(List<ImportRow> rows) {
        List<RowError> ignored = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int balancesSkipped = 0;
        for (ImportRow row : rows) {
            CustomerRequest r = new CustomerRequest();
            r.setPhoneNumber(row.text("phone"));
            r.setFirstName(row.text("first_name"));
            r.setLastName(row.text("last_name"));
            r.setEmail(row.text("email"));
            r.setVatNumber(row.text("vat_number"));
            r.setBookNo(row.text("book_no"));

            Optional<Customer> existing = customerRepository.findByPhoneNumber(PhoneUtils.normalize(r.getPhoneNumber()));
            UUID id;
            if (existing.isPresent()) {
                id = existing.get().getId();
                customerService.updateCustomer(id, r); // no address: the one on file is kept
                updated++;
            } else {
                AddressRequest a = new AddressRequest();
                a.setStreet(row.text("street"));
                a.setAddressLine2(row.text("address_line2"));
                a.setCity(row.text("city"));
                a.setDistrict(row.text("district"));
                a.setPostalCode(row.text("postal_code"));
                a.setCountry("Sri Lanka");
                r.setAddress(a);
                id = customerService.createCustomer(r, false).getId();
                created++;
            }

            BigDecimal limit = row.decimal("credit_limit", false, false, ignored);
            if (limit != null) {
                customerService.updateCreditLimit(id, new CreditLimitRequest(limit));
            }
            BigDecimal opening = row.decimal("opening_balance", false, true, ignored);
            if (opening != null && opening.signum() != 0) {
                if (hasHistory(id)) {
                    balancesSkipped++;
                    continue;
                }
                if (opening.signum() > 0) {
                    ledgerService.recordCustomerAdjustment(id, opening, LocalDate.now(), OPENING_BALANCE,
                            "OPENING_BALANCE", securityService.getCurrentUserId());
                } else {
                    customerService.manualAdjustAdvance(id, new AdjustAdvanceRequest(opening.negate(), OPENING_BALANCE));
                }
                Customer c = customerRepository.findById(id).orElseThrow();
                customerService.syncAllBalances(c);
                customerRepository.save(c);
            }
        }
        return new Counts(created, updated, balancesSkipped);
    }
}
