package com.ebee.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.print")
public class PrintProperties {

    private final String companyName;
    private final String companyAddress;
    private final String companyPhone;
    private final String companyEmail;
    private final String companyRegistration;
    private final String logoUrl;

    @ConstructorBinding
    public PrintProperties(String companyName, String companyAddress, String companyPhone,
                           String companyEmail, String companyRegistration, String logoUrl) {
        this.companyName = companyName != null ? companyName : "Ebee (Pvt) Ltd";
        this.companyAddress = companyAddress != null ? companyAddress : "";
        this.companyPhone = companyPhone != null ? companyPhone : "";
        this.companyEmail = companyEmail != null ? companyEmail : "";
        this.companyRegistration = companyRegistration != null ? companyRegistration : "";
        this.logoUrl = logoUrl != null ? logoUrl : "";
    }

    public String getCompanyName() { return companyName; }
    public String getCompanyAddress() { return companyAddress; }
    public String getCompanyPhone() { return companyPhone; }
    public String getCompanyEmail() { return companyEmail; }
    public String getCompanyRegistration() { return companyRegistration; }
    public String getLogoUrl() { return logoUrl; }
}
