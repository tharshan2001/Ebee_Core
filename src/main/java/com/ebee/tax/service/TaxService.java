package com.ebee.tax.service;

import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.tax.dto.BusinessSettingsRequest;
import com.ebee.tax.dto.TaxRateRequest;
import com.ebee.tax.entity.BusinessSettings;
import com.ebee.tax.entity.TaxRate;
import com.ebee.tax.repository.BusinessSettingsRepository;
import com.ebee.tax.repository.TaxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Tax rates and the business-wide VAT switch. */
@Service
public class TaxService {

    private final TaxRateRepository taxRateRepository;
    private final BusinessSettingsRepository settingsRepository;

    public TaxService(TaxRateRepository taxRateRepository, BusinessSettingsRepository settingsRepository) {
        this.taxRateRepository = taxRateRepository;
        this.settingsRepository = settingsRepository;
    }

    // ── settings ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BusinessSettings getSettings() {
        return settingsRepository.findById(BusinessSettings.SINGLETON_ID).orElseGet(BusinessSettings::new);
    }

    @Transactional
    public BusinessSettings updateSettings(BusinessSettingsRequest request) {
        BusinessSettings s = settingsRepository.findById(BusinessSettings.SINGLETON_ID).orElseGet(BusinessSettings::new);
        if (request.vatRegistered() != null) {
            s.setVatRegistered(request.vatRegistered());
        }
        if (request.vatRegistrationNumber() != null) {
            String v = request.vatRegistrationNumber().trim();
            s.setVatRegistrationNumber(v.isEmpty() ? null : v);
        }
        if (request.pricesIncludeTax() != null) {
            s.setPricesIncludeTax(request.pricesIncludeTax());
        }
        if (s.isVatRegistered() && (s.getVatRegistrationNumber() == null)) {
            throw new IllegalArgumentException("A VAT registration number is required to turn VAT on");
        }
        return settingsRepository.save(s);
    }

    /** Effective VAT rate for a product's tax rate id, or null when VAT is off / product is exempt. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> effectiveRates(Collection<UUID> taxRateIds) {
        Map<UUID, BigDecimal> rates = new HashMap<>();
        if (!getSettings().isVatRegistered()) {
            return rates;
        }
        List<UUID> ids = taxRateIds.stream().filter(Objects::nonNull).distinct().toList();
        taxRateRepository.findAllById(ids).forEach(r -> {
            if (r.isActive() && r.getRate().signum() > 0) {
                rates.put(r.getId(), r.getRate());
            }
        });
        return rates;
    }

    // ── rates ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TaxRate> listRates() {
        return taxRateRepository.findAllByOrderByRateDesc();
    }

    @Transactional(readOnly = true)
    public TaxRate requireRate(UUID id) {
        return taxRateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TaxRate", "id", id));
    }

    @Transactional
    public TaxRate createRate(TaxRateRequest request) {
        if (taxRateRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalArgumentException("A tax rate named '" + request.name() + "' already exists");
        }
        return taxRateRepository.save(new TaxRate(request.name().trim(), request.rate()));
    }

    @Transactional
    public TaxRate updateRate(UUID id, TaxRateRequest request) {
        TaxRate rate = requireRate(id);
        rate.setName(request.name().trim());
        rate.setRate(request.rate());
        if (request.active() != null) {
            rate.setActive(request.active());
        }
        return taxRateRepository.save(rate);
    }
}
