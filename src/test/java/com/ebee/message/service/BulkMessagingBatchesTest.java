package com.ebee.message.service;

import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.repository.MessageRecipientRepository;
import com.ebee.message.service.resolver.AllCustomersRecipientResolver;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.sender.SmsSender;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Bulk messages read, store and send recipients a page at a time. */
class BulkMessagingBatchesTest {

    private static final int B = MessageService.BATCH_SIZE;

    private static List<Customer> customers(int n) {
        return IntStream.range(0, n).mapToObj(i -> Customer.builder().id(UUID.randomUUID())
                .firstName("C").lastName("" + i).phoneNumber("077" + String.format("%07d", i)).build()).toList();
    }

    @Test
    void allCustomers_isReadPageByPage_inIdOrder() {
        CustomerRepository repo = mock(CustomerRepository.class);
        List<Customer> all = customers(B * 2 + 3);
        when(repo.findAll(any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(0);
            int from = (int) p.getOffset();
            return new PageImpl<>(all.subList(Math.min(from, all.size()), Math.min(from + p.getPageSize(), all.size())), p, all.size());
        });
        var resolver = new AllCustomersRecipientResolver(repo);
        List<Integer> batchSizes = new ArrayList<>();

        resolver.resolveInBatches(new Message(), null, B, batch -> batchSizes.add(batch.size()));

        assertThat(batchSizes).containsExactly(B, B, 3);
        verify(repo).findAll(PageRequest.of(0, B, org.springframework.data.domain.Sort.by("id")));
        verify(repo, times(3)).findAll(any(Pageable.class));
        verify(repo, times(0)).findAll();
    }

    @Test
    void exactMultipleOfTheBatchSize_stopsAfterOneEmptyPage() {
        CustomerRepository repo = mock(CustomerRepository.class);
        List<Customer> all = customers(B);
        when(repo.findAll(any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(0);
            return new PageImpl<>(p.getPageNumber() == 0 ? all : List.of(), p, all.size());
        });
        List<Integer> batchSizes = new ArrayList<>();

        new AllCustomersRecipientResolver(repo).resolveInBatches(new Message(), null, B, b -> batchSizes.add(b.size()));

        assertThat(batchSizes).containsExactly(B);
    }

    @Test
    void defaultResolver_splitsABoundedListIntoBatches() {
        RecipientResolver manual = new RecipientResolver() {
            public com.ebee.message.enums.MessageTargetType targetType() { return com.ebee.message.enums.MessageTargetType.MANUAL_ENTRY; }
            public List<MessageRecipient> resolve(Message m, RecipientResolutionContext c) {
                return IntStream.range(0, 7).mapToObj(i -> MessageRecipient.builder().build()).toList();
            }
        };
        List<Integer> sizes = new ArrayList<>();
        manual.resolveInBatches(new Message(), null, 3, b -> sizes.add(b.size()));
        assertThat(sizes).containsExactly(3, 3, 1);
    }

    @Test
    void dispatcher_sendsAPageAtATime_andClearsBetweenPages() {
        MessageRecipientRepository repo = mock(MessageRecipientRepository.class);
        SmsSender sms = mock(SmsSender.class);
        EntityManager em = mock(EntityManager.class);
        Message message = Message.builder().id(UUID.randomUUID()).subject("Hi").body("Sale today").build();
        List<MessageRecipient> all = IntStream.range(0, B + 10).mapToObj(i -> MessageRecipient.builder()
                .id(UUID.randomUUID()).message(message).phoneNumber("077" + String.format("%07d", i)).build()).toList();
        when(repo.findByMessageId(eq(message.getId()), any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(1);
            int from = (int) p.getOffset();
            return new PageImpl<>(all.subList(Math.min(from, all.size()), Math.min(from + p.getPageSize(), all.size())), p, all.size());
        });

        new MessageDispatcher(repo, sms, mock(EmailSender.class), em).dispatch(message);

        verify(sms, times(B + 10)).send(any());
        verify(repo, times(2)).findByMessageId(eq(message.getId()), any(Pageable.class));
        verify(em, times(2)).clear();
        assertThat(all).allMatch(MessageRecipient::getSentSms);
    }
}
