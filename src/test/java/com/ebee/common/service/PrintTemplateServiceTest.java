package com.ebee.common.service;

import com.ebee.common.config.PrintProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintTemplateServiceTest {

    @Mock
    private PrintProperties printProperties;

    @InjectMocks
    private PrintTemplateService printTemplateService;

    @Test
    void renderToPdf_shouldReturnNonEmptyByteArray() {
        when(printProperties.getCompanyName()).thenReturn("Test Company");
        when(printProperties.getCompanyAddress()).thenReturn("123 Test St");
        when(printProperties.getCompanyPhone()).thenReturn("+94 11 234 5678");
        when(printProperties.getCompanyEmail()).thenReturn("test@company.com");
        when(printProperties.getCompanyRegistration()).thenReturn("REG-001");
        when(printProperties.getLogoUrl()).thenReturn("");

        Map<String, Object> variables = Map.of(
                "title", "TEST INVOICE",
                "orderNumber", "ORD-001",
                "customerName", "John Doe",
                "items", java.util.List.of(
                        new com.ebee.order.dto.InvoiceData.LineItem("Product A", "PA-001", 2, new java.math.BigDecimal("100.00"), new java.math.BigDecimal("200.00"), null)
                ),
                "subtotal", new java.math.BigDecimal("200.00"),
                "totalAmount", new java.math.BigDecimal("200.00")
        );

        byte[] pdf = printTemplateService.renderToPdf("invoice", variables);

        assertThat(pdf).isNotEmpty();
        // PDF files start with %PDF
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.UTF_8)).startsWith("%PDF");
    }
}
