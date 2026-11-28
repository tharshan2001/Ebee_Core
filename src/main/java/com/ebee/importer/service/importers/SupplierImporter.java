package com.ebee.importer.service.importers;

import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.accounting.service.AccountingPeriodService;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.enums.District;
import com.ebee.common.security.SecurityService;
import com.ebee.common.util.PhoneUtils;
import com.ebee.importer.service.ImportRow;
import com.ebee.importer.service.ImportType;
import com.ebee.importer.service.RowError;
import com.ebee.importer.service.RowImporter;
import com.ebee.supplier.dto.SupplierRequest;
import com.ebee.supplier.entity.Supplier;
import com.ebee.supplier.repository.SupplierRepository;
import com.ebee.supplier.service.SupplierService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates suppliers (matched by name; existing ones keep their details). An opening balance is
 * posted only for a supplier with no ledger history: positive = what you owe them, negative = what
 * they owe you (prepaid).
 */
@Component
public class SupplierImporter implements RowImporter {

    static final String OPENING_BALANCE = "Opening balance (import)";
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final SupplierRepository supplierRepository;
    private final SupplierService supplierService;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final LedgerService ledgerService;
    private final SecurityService securityService;
    private final AccountingPeriodService accountingPeriodService;

    public SupplierImporter(SupplierRepository supplierRepository, SupplierService supplierService,
                            SupplierLedgerRepository supplierLedgerRepository, LedgerService ledgerService,
                            SecurityService securityService, AccountingPeriodService accountingPeriodService) {
        this.supplierRepository = supplierRepository;
        this.supplierService = supplierService;
        this.supplierLedgerRepository = supplierLedgerRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
        this.accountingPeriodService = accountingPeriodService;
    }

    @Override
    public ImportType type() {
        return ImportType.SUPPLIERS;
    }

    @Override
    public Counts validate(List<ImportRow> rows, List<RowError> errors, List<RowError> notices) {
        Set<String> seen = new HashSet<>();
        boolean periodBlocked = accountingPeriodService.isCurrentPeriodBlocked();
        int created = 0;
        int skipped = 0;
        for (ImportRow row : rows) {
            String name = row.requiredText("name", errors);
            row.requiredText("address_line1", errors);
            row.requiredText("city", errors);
            row.maxLength("name", 255, errors);
            row.maxLength("address_line1", 255, errors);
            row.maxLength("city", 100, errors);
            row.maxLength("contact_person", 255, errors);
            row.maxLength("email", 255, errors);
            String district = row.text("district");
            if (district != null && !District.isValid(district)) {
                errors.add(new RowError(row.rowNumber(), "district", "'" + district + "' isn't a supported district"));
            }
            try {
                PhoneUtils.contactPhone(row.text("phone"));
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(row.rowNumber(), "phone", e.getMessage()));
            }
            BigDecimal opening = row.decimal("opening_balance", false, true, errors);
            row.maxValue("opening_balance", opening, MAX_AMOUNT, errors);
            if (name == null) {
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(row.rowNumber(), "name", "'" + name + "' appears more than once in the file"));
            }
            Optional<Supplier> existing = supplierRepository.findFirstByNameIgnoreCase(name);
            boolean hasHistory = existing.isPresent() && supplierLedgerRepository.existsBySupplierId(existing.get().getId());
            if (existing.isPresent()) {
                skipped++;
                notices.add(new RowError(row.rowNumber(), "name", "'" + name + "' already exists"
                        + (existing.get().isActive() ? "" : " (deactivated)") + "; its details are not changed"));
            } else {
                created++;
            }
            if (opening != null && opening.signum() != 0) {
                if (hasHistory) {
                    notices.add(new RowError(row.rowNumber(), "opening_balance",
                            "not posted: '" + name + "' already has transactions"));
                } else if (periodBlocked) {
                    errors.add(new RowError(row.rowNumber(), "opening_balance",
                            "this month's accounting period is closed; reopen it before importing balances"));
                } else if (existing.isPresent()) {
                    notices.add(new RowError(row.rowNumber(), "opening_balance",
                            "will be posted to the existing supplier '" + name + "'"));
                }
            }
        }
        return new Counts(created, 0, skipped);
    }

    @Override
    public Counts apply(List<ImportRow> rows) {
        List<RowError> ignored = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        for (ImportRow row : rows) {
            Optional<Supplier> existing = supplierRepository.findFirstByNameIgnoreCase(row.text("name"));
            UUID id;
            if (existing.isPresent()) {
                id = existing.get().getId();
                skipped++;
            } else {
                id = supplierService.createSupplier(new SupplierRequest(row.text("name"), row.text("contact_person"),
                        row.text("email"), row.text("phone"), row.text("address_line1"), null, row.text("city"),
                        null, row.text("district"), "Sri Lanka")).id();
                created++;
            }
            BigDecimal opening = row.decimal("opening_balance", false, true, ignored);
            if (opening != null && opening.signum() != 0 && !supplierLedgerRepository.existsBySupplierId(id)) {
                UUID actor = securityService.getCurrentUserId();
                if (opening.signum() > 0) {
                    ledgerService.recordSupplierInvoice(id, null, opening, OPENING_BALANCE, LocalDate.now(), "OPENING_BALANCE", actor);
                } else {
                    ledgerService.recordSupplierAdjustment(id, opening.negate(), LocalDate.now(), OPENING_BALANCE, "OPENING_BALANCE", actor);
                }
                Supplier s = supplierRepository.findById(id).orElseThrow();
                s.setOutstandingBalance(supplierLedgerRepository.calculateBalance(id));
                supplierRepository.save(s);
            }
        }
        return new Counts(created, 0, skipped);
    }
}
