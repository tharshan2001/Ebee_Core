package com.ebee.importer.service.importers;

import com.ebee.accounting.repository.SupplierLedgerRepository;
import com.ebee.accounting.service.LedgerService;
import com.ebee.common.security.SecurityService;
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
 * Creates suppliers (matched by name; existing ones are left as they are). An opening balance
 * is posted once: positive = what you owe them, negative = what they owe you (prepaid).
 */
@Component
public class SupplierImporter implements RowImporter {

    static final String OPENING_BALANCE = "Opening balance (import)";

    private final SupplierRepository supplierRepository;
    private final SupplierService supplierService;
    private final SupplierLedgerRepository supplierLedgerRepository;
    private final LedgerService ledgerService;
    private final SecurityService securityService;

    public SupplierImporter(SupplierRepository supplierRepository, SupplierService supplierService,
                            SupplierLedgerRepository supplierLedgerRepository, LedgerService ledgerService,
                            SecurityService securityService) {
        this.supplierRepository = supplierRepository;
        this.supplierService = supplierService;
        this.supplierLedgerRepository = supplierLedgerRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
    }

    @Override
    public ImportType type() {
        return ImportType.SUPPLIERS;
    }

    @Override
    public void validate(List<ImportRow> rows, List<RowError> errors) {
        Set<String> seen = new HashSet<>();
        for (ImportRow row : rows) {
            String name = row.requiredText("name", errors);
            row.requiredText("address_line1", errors);
            row.requiredText("city", errors);
            row.decimal("opening_balance", false, true, errors);
            if (name != null && !seen.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(new RowError(row.rowNumber(), "name", "'" + name + "' appears more than once in the file"));
            }
        }
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
            if (opening != null && opening.signum() != 0
                    && !supplierLedgerRepository.existsBySupplierIdAndDescription(id, OPENING_BALANCE)) {
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
