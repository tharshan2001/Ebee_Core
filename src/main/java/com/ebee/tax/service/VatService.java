package com.ebee.tax.service;

import com.ebee.tax.dto.VatReport;
import com.ebee.tax.entity.VatEntry;
import com.ebee.tax.entity.VatEntry.Direction;
import com.ebee.tax.entity.VatEntry.SourceType;
import com.ebee.tax.repository.VatEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Posts VAT journal entries and builds the VAT return for a period. */
@Service
public class VatService {

    private final VatEntryRepository vatEntryRepository;

    public VatService(VatEntryRepository vatEntryRepository) {
        this.vatEntryRepository = vatEntryRepository;
    }

    /**
     * Record a taxable event. Amounts are signed (negative for credit notes / purchase returns).
     * Nothing is written when the tax is zero.
     */
    @Transactional
    public void post(Direction direction, SourceType sourceType, UUID sourceId, String reference,
                     BigDecimal taxableAmount, BigDecimal taxAmount) {
        if (taxAmount == null || taxAmount.signum() == 0) {
            return;
        }
        vatEntryRepository.save(new VatEntry(LocalDate.now(), direction, sourceType, sourceId, reference,
                taxableAmount, taxAmount));
    }

    @Transactional(readOnly = true)
    public VatReport report(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        Map<SourceType, BigDecimal[]> byType = new EnumMap<>(SourceType.class);
        for (Object[] r : vatEntryRepository.summarize(from, to)) {
            byType.put((SourceType) r[1], new BigDecimal[]{(BigDecimal) r[2], (BigDecimal) r[3]});
        }
        BigDecimal[] zero = {BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal[] sales = byType.getOrDefault(SourceType.ORDER, zero);
        BigDecimal[] creditNotes = byType.getOrDefault(SourceType.CREDIT_NOTE, zero);
        BigDecimal[] purchases = byType.getOrDefault(SourceType.PURCHASE_RECEIPT, zero);
        BigDecimal[] purchaseReturns = byType.getOrDefault(SourceType.PURCHASE_RETURN, zero);

        BigDecimal outputTax = sales[1].add(creditNotes[1]);
        BigDecimal inputTax = purchases[1].add(purchaseReturns[1]);
        return new VatReport(from, to,
                sales[0], sales[1], creditNotes[0], creditNotes[1], outputTax,
                purchases[0], purchases[1], purchaseReturns[0], purchaseReturns[1], inputTax,
                outputTax.subtract(inputTax));
    }
}
