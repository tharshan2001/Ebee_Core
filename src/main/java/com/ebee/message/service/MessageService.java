package com.ebee.message.service;

import com.ebee.agent.entity.Agent;
import com.ebee.agent.repository.AgentRepository;
import com.ebee.customer.entity.Customer;
import com.ebee.customer.repository.CustomerRepository;
import com.ebee.message.dto.ManualRecipientEntry;
import com.ebee.message.dto.MessageRequest;
import com.ebee.message.dto.MessageResponse;
import com.ebee.message.dto.MessageRecipientResponse;
import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;
import com.ebee.message.enums.RecipientType;
import com.ebee.message.repository.MessageRecipientRepository;
import com.ebee.message.repository.MessageRepository;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import com.ebee.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final AgentRepository agentRepository;
    private final CustomerRepository customerRepository;
    private final ApplicationEventPublisher eventPublisher;

    record RecipientInfo(String name, String phoneNumber, String email) {}

    @Transactional
    public MessageResponse sendMessage(User admin, MessageRequest request) {
        List<MessageRecipient> recipients = new ArrayList<>();
        int totalRecipients = 0;

        Message message = Message.builder()
                .sentBy(admin)
                .subject(request.getSubject())
                .body(request.getBody())
                .targetType(request.getTargetType())
                .totalRecipients(0)
                .build();

        message = messageRepository.save(message);

        switch (request.getTargetType()) {
            case ALL_AGENTS -> {
                List<Agent> agents = agentRepository.findAllWithUser();
                for (Agent agent : agents) {
                    recipients.add(buildRecipient(message, RecipientType.AGENT, agent.getId(),
                            agent.getUser().getFirstName() + " " + agent.getUser().getLastName(),
                            agent.getPhoneNumber(), agent.getUser().getEmail()));
                }
            }
            case ALL_CUSTOMERS -> {
                List<Customer> customers = customerRepository.findAll();
                for (Customer customer : customers) {
                    recipients.add(buildRecipient(message, RecipientType.CUSTOMER, customer.getId(),
                            customer.getFirstName() + " " + customer.getLastName(),
                            customer.getPhoneNumber(), customer.getEmail()));
                }
            }
            case SELECTED_AGENTS -> {
                if (request.getRecipientIds() == null || request.getRecipientIds().isEmpty()) {
                    throw new IllegalArgumentException("recipientIds required for SELECTED_AGENTS");
                }
                List<Agent> agents = agentRepository.findAllByIdWithUser(request.getRecipientIds());
                for (Agent agent : agents) {
                    recipients.add(buildRecipient(message, RecipientType.AGENT, agent.getId(),
                            agent.getUser().getFirstName() + " " + agent.getUser().getLastName(),
                            agent.getPhoneNumber(), agent.getUser().getEmail()));
                }
            }
            case SELECTED_CUSTOMERS -> {
                if (request.getRecipientIds() == null || request.getRecipientIds().isEmpty()) {
                    throw new IllegalArgumentException("recipientIds required for SELECTED_CUSTOMERS");
                }
                List<Customer> customers = customerRepository.findAllById(request.getRecipientIds());
                for (Customer customer : customers) {
                    recipients.add(buildRecipient(message, RecipientType.CUSTOMER, customer.getId(),
                            customer.getFirstName() + " " + customer.getLastName(),
                            customer.getPhoneNumber(), customer.getEmail()));
                }
            }
            case MANUAL_ENTRY -> {
                if (request.getManualRecipients() == null || request.getManualRecipients().isEmpty()) {
                    throw new IllegalArgumentException("manualRecipients required for MANUAL_ENTRY");
                }
                for (ManualRecipientEntry entry : request.getManualRecipients()) {
                    recipients.add(buildRecipient(message, RecipientType.MANUAL, UUID.randomUUID(),
                            entry.getName(), entry.getPhoneNumber(), entry.getEmail()));
                }
            }
        }

        recipientRepository.saveAll(recipients);
        totalRecipients = recipients.size();

        message.setTotalRecipients(totalRecipients);
        messageRepository.save(message);

        eventPublisher.publishEvent(message);

        return MessageResponse.builder()
                .id(message.getId())
                .subject(message.getSubject())
                .targetType(message.getTargetType())
                .totalRecipients(totalRecipients)
                .sentAt(message.getSentAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getSentMessages(User admin, Pageable pageable) {
        return messageRepository.findBySentByIdOrderBySentAtDesc(admin.getId(), pageable)
                .map(msg -> MessageResponse.builder()
                        .id(msg.getId())
                        .subject(msg.getSubject())
                        .targetType(msg.getTargetType())
                        .totalRecipients(msg.getTotalRecipients())
                        .sentAt(msg.getSentAt())
                        .build());
    }

    @Transactional(readOnly = true)
    public MessageResponse getMessageWithRecipients(UUID messageId, User admin, Pageable pageable) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        if (!message.getSentBy().getId().equals(admin.getId())) {
            throw new RuntimeException("Access denied");
        }

        Page<MessageRecipient> recipientPage = recipientRepository.findByMessageId(messageId, pageable);

        return MessageResponse.builder()
                .id(message.getId())
                .subject(message.getSubject())
                .body(message.getBody())
                .targetType(message.getTargetType())
                .totalRecipients(message.getTotalRecipients())
                .sentAt(message.getSentAt())
                .recipients(recipientPage.map(r -> MessageRecipientResponse.builder()
                        .id(r.getId())
                        .recipientType(r.getRecipientType())
                        .recipientId(r.getRecipientId())
                        .name(r.getName())
                        .phoneNumber(r.getPhoneNumber())
                        .email(r.getEmail())
                        .sentSms(r.getSentSms())
                        .sentEmail(r.getSentEmail())
                        .build()).toList())
                .build();
    }

    private MessageRecipient buildRecipient(Message message, RecipientType type, UUID recipientId,
                                             String name, String phoneNumber, String email) {
        return MessageRecipient.builder()
                .message(message)
                .recipientType(type)
                .recipientId(recipientId)
                .name(name)
                .phoneNumber(phoneNumber)
                .email(email)
                .build();
    }
}
