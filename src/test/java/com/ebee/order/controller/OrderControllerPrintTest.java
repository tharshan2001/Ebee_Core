package com.ebee.order.controller;

import com.ebee.auth.config.AuthPropertiesConfig;
import com.ebee.auth.security.RateLimitingService;
import com.ebee.auth.security.SecurityConfig;
import com.ebee.auth.service.IpWhitelistService;
import com.ebee.auth.service.JwtService;
import com.ebee.auth.service.UserDetailsServiceImpl;
import com.ebee.common.config.CommonPropertiesConfig;
import com.ebee.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, AuthPropertiesConfig.class, CommonPropertiesConfig.class})
class OrderControllerPrintTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @MockBean
    private IpWhitelistService ipWhitelistService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void downloadInvoice_shouldReturnPdf() throws Exception {
        UUID orderId = UUID.randomUUID();
        byte[] pdfBytes = ("%PDF-1.4 test pdf content").getBytes();
        when(orderService.generateInvoicePdf(orderId)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/orders/{id}/invoice", orderId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice-" + orderId + ".pdf"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void downloadReceipt_shouldReturnPdf() throws Exception {
        UUID orderId = UUID.randomUUID();
        byte[] pdfBytes = ("%PDF-1.4 test pdf content").getBytes();
        when(orderService.generateReceiptPdf(orderId)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/orders/{id}/receipt", orderId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=receipt-" + orderId + ".pdf"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void downloadShippingLabel_shouldReturnPdf() throws Exception {
        UUID orderId = UUID.randomUUID();
        byte[] pdfBytes = ("%PDF-1.4 test pdf content").getBytes();
        when(orderService.generateShippingLabelPdf(orderId)).thenReturn(pdfBytes);

        mockMvc.perform(get("/api/orders/{id}/shipping-label", orderId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", "attachment; filename=shipping-label-" + orderId + ".pdf"));
    }

    @Test
    void downloadInvoice_shouldRequireAuth() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/invoice", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
