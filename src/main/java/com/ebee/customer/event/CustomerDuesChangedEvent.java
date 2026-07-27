package com.ebee.customer.event;

import com.ebee.customer.entity.Customer;
import com.ebee.customer.enums.DuesChangeType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class CustomerDuesChangedEvent extends ApplicationEvent {

    private final UUID customerId;
    private final String customerName;
    private final BigDecimal previousOutstanding;
    private final BigDecimal newOutstanding;
    private final BigDecimal previousAdvance;
    private final BigDecimal newAdvance;
    private final DuesChangeType changeType;
    private final LocalDateTime occurredAt;

    public CustomerDuesChangedEvent(Object source, Customer customer,
                                   BigDecimal previousOutstanding, BigDecimal newOutstanding,
                                   BigDecimal previousAdvance, BigDecimal newAdvance,
                                   DuesChangeType changeType) {
        super(source);
        this.customerId = customer.getId();
        this.customerName = customer.getFirstName() + " " + customer.getLastName();
        this.previousOutstanding = previousOutstanding;
        this.newOutstanding = newOutstanding;
        this.previousAdvance = previousAdvance;
        this.newAdvance = newAdvance;
        this.changeType = changeType;
        this.occurredAt = LocalDateTime.now();
    }
}
