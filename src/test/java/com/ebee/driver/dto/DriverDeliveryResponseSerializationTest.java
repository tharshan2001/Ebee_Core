package com.ebee.driver.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DriverDeliveryResponseSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void orderItemInfoIncludesImageUrl() throws Exception {
        var item = new DriverDeliveryResponse.OrderItemInfo();
        item.setProductId(UUID.randomUUID());
        item.setProductName("Test Product");
        item.setImageUrl("https://example.com/image.jpg");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setSubtotal(new BigDecimal("200.00"));

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(item);
        System.out.println(json);

        assertThat(json).contains("imageUrl");
        assertThat(json).contains("https://example.com/image.jpg");
    }

    @Test
    void orderItemInfoImageUrlIsNullWhenNotSet() throws Exception {
        var item = new DriverDeliveryResponse.OrderItemInfo();
        item.setProductId(UUID.randomUUID());
        item.setProductName("Test Product");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setSubtotal(new BigDecimal("50.00"));

        String json = mapper.writeValueAsString(item);
        System.out.println(json);

        assertThat(json).contains("\"imageUrl\":null");
    }
}
