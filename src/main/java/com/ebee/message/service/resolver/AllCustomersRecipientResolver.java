package com.ebee.message.service.resolver;

import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;
import com.ebee.message.enums.RecipientType;
import com.ebee.message.service.RecipientResolutionContext;
import com.ebee.message.service.RecipientResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AllCustomersRecipientResolver implements RecipientResolver {

    private final CustomerRepository customerRepository;

    @Override
    public MessageTargetType targetType() {
        return MessageTargetType.ALL_CUSTOMERS;
    }

    @Override
    public List<MessageRecipient> resolve(Message message, RecipientResolutionContext context) {
        return customerRepository.findAll().stream()
                .map(customer -> buildRecipient(message, customer))
                .toList();
    }

    private MessageRecipient buildRecipient(Message message, Customer customer) {
        return MessageRecipient.builder()
                .message(message)
                .recipientType(RecipientType.CUSTOMER)
                .recipientId(customer.getId())
                .name(customer.getFirstName() + " " + customer.getLastName())
                .phoneNumber(customer.getPhoneNumber())
                .email(customer.getEmail())
                .build();
    }
}
