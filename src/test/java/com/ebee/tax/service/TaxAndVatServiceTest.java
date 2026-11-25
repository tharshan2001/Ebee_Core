package com.ebee.tax.service;

import com.ebee.tax.dto.BusinessSettingsRequest;
import com.ebee.tax.entity.BusinessSettings;
import com.ebee.tax.entity.TaxRate;
import com.ebee.tax.entity.VatEntry;
import com.ebee.tax.repository.BusinessSettingsRepository;
import com.ebee.tax.repository.TaxRateRepository;
import com.ebee.tax.repository.VatEntryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaxAndVatServiceTest {

    private final TaxRateRepository rateRepo = mock(TaxRateRepository.class);
    private final BusinessSettingsRepository settingsRepo = mock(BusinessSettingsRepository.class);
    private final TaxService taxService = new TaxService(rateRepo, settingsRepo);

    private BusinessSettings settings(boolean vatOn) {
        BusinessSettings s = new BusinessSettings();
        s.setVatRegistered(vatOn);
        s.setVatRegistrationNumber(vatOn ? "123456789-7000" : null);
        when(settingsRepo.findById(BusinessSettings.SINGLETON_ID)).thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void vatOff_noEffectiveRates_evenForTaxedProducts() {
        settings(false);
        assertThat(taxService.effectiveRates(List.of(UUID.randomUUID()))).isEmpty();
        verify(rateRepo, never()).findAllById(any());
    }

    @Test
    void vatOn_returnsActiveNonZeroRatesOnly() {
        settings(true);
        TaxRate vat = new TaxRate("VAT 18%", new BigDecimal("18.00"));
        TaxRate exempt = new TaxRate("Exempt", BigDecimal.ZERO);
        UUID vatId = UUID.randomUUID();
        UUID exemptId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(vat, "id", vatId);
        org.springframework.test.util.ReflectionTestUtils.setField(exempt, "id", exemptId);
        when(rateRepo.findAllById(any())).thenReturn(List.of(vat, exempt));

        var rates = taxService.effectiveRates(List.of(vatId, exemptId));

        assertThat(rates).containsOnlyKeys(vatId);
        assertThat(rates.get(vatId)).isEqualByComparingTo("18.00");
    }

    @Test
    void turningVatOn_requiresRegistrationNumber() {
        when(settingsRepo.findById(BusinessSettings.SINGLETON_ID)).thenReturn(Optional.of(new BusinessSettings()));

        assertThatThrownBy(() -> taxService.updateSettings(new BusinessSettingsRequest(true, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registration number");
    }

    @Test
    void vatReport_outputMinusInput_withCreditNotesAndReturnsReducingTheirSide() {
        VatEntryRepository repo = mock(VatEntryRepository.class);
        when(repo.summarize(any(), any())).thenReturn(List.of(
                new Object[]{VatEntry.Direction.OUTPUT, VatEntry.SourceType.ORDER, bd("10000"), bd("1800")},
                new Object[]{VatEntry.Direction.OUTPUT, VatEntry.SourceType.CREDIT_NOTE, bd("-1000"), bd("-180")},
                new Object[]{VatEntry.Direction.INPUT, VatEntry.SourceType.PURCHASE_RECEIPT, bd("5000"), bd("900")},
                new Object[]{VatEntry.Direction.INPUT, VatEntry.SourceType.PURCHASE_RETURN, bd("-500"), bd("-90")}));

        var report = new VatService(repo).report(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(report.outputTax()).isEqualByComparingTo("1620");
        assertThat(report.inputTax()).isEqualByComparingTo("810");
        assertThat(report.vatPayable()).isEqualByComparingTo("810");
    }

    @Test
    void zeroTax_isNotPosted() {
        VatEntryRepository repo = mock(VatEntryRepository.class);
        new VatService(repo).post(VatEntry.Direction.OUTPUT, VatEntry.SourceType.ORDER, UUID.randomUUID(), "X",
                bd("100"), BigDecimal.ZERO);
        verify(repo, never()).save(any());
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
