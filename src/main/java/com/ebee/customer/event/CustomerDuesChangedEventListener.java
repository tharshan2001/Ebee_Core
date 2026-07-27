package com.ebee.customer.event;

import com.ebee.customer.dto.CustomerDuesChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerDuesChangedEventListener {

    public static final String TOPIC = "/topic/customer-dues";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerDuesChanged(CustomerDuesChangedEvent event) {
        CustomerDuesChangedMessage message = new CustomerDuesChangedMessage(
                event.getCustomerId(),
                event.getCustomerName(),
                event.getPreviousOutstanding(),
                event.getNewOutstanding(),
                event.getPreviousAdvance(),
                event.getNewAdvance(),
                event.getNewOutstanding().subtract(event.getPreviousOutstanding()),
                event.getNewAdvance().subtract(event.getPreviousAdvance()),
                event.getChangeType(),
                event.getOccurredAt()
        );
        try {
            messagingTemplate.convertAndSend(TOPIC, message);
            log.info("Broadcast customer dues change for {}: outstanding {} → {}, advance {} → {}",
                    event.getCustomerName(),
                    event.getPreviousOutstanding(), event.getNewOutstanding(),
                    event.getPreviousAdvance(), event.getNewAdvance());
        } catch (Exception e) {
            log.error("Failed to broadcast customer dues change for {}: {}",
                    event.getCustomerName(), e.getMessage(), e);
        }
    }
}
