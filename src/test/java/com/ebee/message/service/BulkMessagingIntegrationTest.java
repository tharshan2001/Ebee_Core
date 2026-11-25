package com.ebee.message.service;

import com.ebee.message.dto.MessageRequest;
import com.ebee.message.enums.MessageTargetType;
import com.ebee.message.repository.MessageRecipientRepository;
import com.ebee.support.RealDatabaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Sending to all customers stores one recipient per customer across several batches (real JPA). */
@Transactional
class BulkMessagingIntegrationTest extends RealDatabaseTest {

    @Autowired private MessageService messages;
    @Autowired private MessageRecipientRepository recipients;

    @Test
    void allCustomers_storesEveryRecipient_acrossBatches() {
        IntStream.range(0, MessageService.BATCH_SIZE + 25).forEach(i -> customer());
        long customers = customerRepository.count();

        var sent = messages.sendMessage(admin, MessageRequest.builder()
                .subject("Notice").body("We are open on Sunday").targetType(MessageTargetType.ALL_CUSTOMERS).build());

        assertThat(sent.getTotalRecipients()).isEqualTo((int) customers);
        assertThat(recipients.countByMessageId(sent.getId())).isEqualTo(customers);
    }
}
