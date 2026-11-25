package com.ebee.importer.service.importers;

import com.ebee.accounting.repository.CustomerLedgerRepository;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.enums.District;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.customer.dto.AddressRequest;
import com.ebee.customer.dto.CreditLimitRequest;
import com.ebee.customer.dto.CustomerRequest;
import com.ebee.customer.entity.Customer;
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

/**
 * Creates customers (no welcome SMS/email), or updates them when the phone number already
 * exists. An opening balance is posted once per customer as a ledger adjustment.
 */
@Component
public class CustomerImporter implements RowImporter {

    static final String OPENING_BALANCE = "Opening balance (import)";

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final CustomerLedgerRepository customerLedgerRepository;
    private final LedgerService ledgerService;
    private final SecurityService securityService;

    public CustomerImporter(CustomerRepository customerRepository, CustomerService customerService,
                            CustomerLedgerRepository customerLedgerRepository, LedgerService ledgerService,
                            SecurityService securityService) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.customerLedgerRepository = customerLedgerRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
    }

    @Override
    public ImportType type() {
        return ImportType.CUSTOMERS;
    }

    @Override
    public void validate(List<ImportRow> rows, List<RowError> errors) {
        Set<String> seen = new HashSet<>();
        for (ImportRow row : rows) {
            String phone = row.requiredText("phone", errors);
            row.requiredText("first_name", errors);
            row.requiredText("city", errors);
            row.requiredText("street", errors);
            String district = row.requiredText("district", errors);
            if (district != null && !District.isValid(district)) {
                errors.add(new RowError(row.rowNumber(), "district", "'" + district + "' isn't a supported district"));
            }
            row.decimal("credit_limit", false, false, errors);
            row.decimal("opening_balance", false, true, errors);
            String email = row.text("email");
            if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                errors.add(new RowError(row.rowNumber(), "email", "'" + email + "' isn't an email address"));
            }
            if (phone != null) {
                String normalized = PhoneUtils.normalize(phone);
                if (normalized == null || !normalized.matches("^0\\d{9}$")) {
                    errors.add(new RowError(row.rowNumber(), "phone", "'" + phone + "' isn't a 10-digit Sri Lankan number"));
                } else if (!seen.add(normalized)) {
                    errors.add(new RowError(row.rowNumber(), "phone", "'" + phone + "' appears more than once in the file"));
                }
            }
        }
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
            r.setLastName(row.text("last_name") != null ? row.text("last_name") : "");
            r.setEmail(row.text("email"));
            r.setVatNumber(row.text("vat_number"));
            AddressRequest a = new AddressRequest();
            a.setStreet(row.text("street"));
            a.setCity(row.text("city"));
            a.setDistrict(row.text("district"));
            a.setCountry("Sri Lanka");
            r.setAddress(a);

            Optional<Customer> existing = customerRepository.findByPhoneNumber(PhoneUtils.normalize(r.getPhoneNumber()));
            java.util.UUID id;
            if (existing.isPresent()) {
                id = existing.get().getId();
                r.setAddress(null); // keep the address on file
                customerService.updateCustomer(id, r);
                updated++;
            } else {
                id = customerService.createCustomer(r, false).getId();
                created++;
            }

            BigDecimal limit = row.decimal("credit_limit", false, false, ignored);
            if (limit != null) {
                customerService.updateCreditLimit(id, new CreditLimitRequest(limit));
            }
            BigDecimal opening = row.decimal("opening_balance", false, true, ignored);
            if (opening != null && opening.signum() != 0) {
                if (customerLedgerRepository.existsByCustomerIdAndDescription(id, OPENING_BALANCE)) {
                    balancesSkipped++;
                } else if (opening.signum() > 0) {
                    ledgerService.recordCustomerAdjustment(id, opening, LocalDate.now(), OPENING_BALANCE,
                            "OPENING_BALANCE", securityService.getCurrentUserId());
                } else {
                    ledgerService.recordCustomerCreditAdjustment(id, null, opening.negate(), OPENING_BALANCE,
                            securityService.getCurrentUserId());
                }
                Customer c = customerRepository.findById(id).orElseThrow();
                customerService.syncAllBalances(c);
                customerRepository.save(c);
            }
        }
        return new Counts(created, updated, balancesSkipped);
    }
}
