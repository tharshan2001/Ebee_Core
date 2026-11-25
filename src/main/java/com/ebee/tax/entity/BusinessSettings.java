package com.ebee.tax.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Single-row table (id = 1) holding business-wide tax settings. */
@Entity
@Table(name = "business_settings")
public class BusinessSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    /** Master switch: when false, no VAT is calculated anywhere. */
    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered;

    @Column(name = "vat_registration_number", length = 50)
    private String vatRegistrationNumber;

    /** True: catalog prices already include VAT. False: VAT is added on top. */
    @Column(name = "prices_include_tax", nullable = false)
    private boolean pricesIncludeTax = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public BusinessSettings() {
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Short getId() { return id; }
    public boolean isVatRegistered() { return vatRegistered; }
    public void setVatRegistered(boolean vatRegistered) { this.vatRegistered = vatRegistered; }
    public String getVatRegistrationNumber() { return vatRegistrationNumber; }
    public void setVatRegistrationNumber(String vatRegistrationNumber) { this.vatRegistrationNumber = vatRegistrationNumber; }
    public boolean isPricesIncludeTax() { return pricesIncludeTax; }
    public void setPricesIncludeTax(boolean pricesIncludeTax) { this.pricesIncludeTax = pricesIncludeTax; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
