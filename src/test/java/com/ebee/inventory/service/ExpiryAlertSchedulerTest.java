package com.ebee.inventory.service;

import com.ebee.inventory.dto.ExpiringStockResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryAlertSchedulerTest {

    private static ExpiringStockResponse batch(long daysLeft, String value) {
        return new ExpiringStockResponse(UUID.randomUUID(), UUID.randomUUID(), "P", "P-1", null, "Main", "L",
                LocalDate.now().plusDays(daysLeft), daysLeft, 5, BigDecimal.TEN, new BigDecimal(value));
    }

    @Test
    void nothingToReport_noAlert() {
        assertThat(ExpiryAlertScheduler.buildMessage(List.of())).isNull();
    }

    @Test
    void summarisesExpiredValueAndSoonToExpire() {
        String msg = ExpiryAlertScheduler.buildMessage(List.of(batch(-3, "250.50"), batch(-1, "100"), batch(12, "999")));
        assertThat(msg).contains("2 batch(es) have expired (LKR 350.50").contains("1 batch(es) expire within 30 days");
    }
}
