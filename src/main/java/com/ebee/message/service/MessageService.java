package com.ebee.message.service;

import com.ebee.message.dto.MessageRequest;
import com.ebee.message.dto.MessageResponse;
import com.ebee.message.dto.MessageRecipientResponse;
import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.enums.MessageTargetType;
import com.ebee.message.repository.MessageRecipientRepository;
import com.ebee.message.repository.MessageRepository;
import com.ebee.common.exception.ResourceNotFoundException;
import com.ebee.user.entity.User;
import com.ebee.user.enums.RoleType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageRecipientRepository recipientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final List<RecipientResolver> resolvers;

    private Map<MessageTargetType, RecipientResolver> resolverMap;

    @PostConstruct
    void initResolverMap() {
        resolverMap = resolvers.stream()
                .collect(Collectors.toMap(RecipientResolver::targetType, Function.identity()));
    }

    @Transactional
    public MessageResponse sendMessage(User admin, MessageRequest request) {
        Message message = Message.builder()
                .sentBy(admin)
                .subject(request.getSubject())
                .body(request.getBody())
                .targetType(request.getTargetType())
                .totalRecipients(0)
                .build();

        RecipientResolver resolver = resolverMap.get(request.getTargetType());
        if (resolver == null) {
            throw new IllegalArgumentException("Unsupported target type: " + request.getTargetType());
        }

        List<MessageRecipient> recipients = resolver.resolve(message, RecipientResolutionContext.from(request));

        message.setTotalRecipients(recipients.size());
        message = messageRepository.save(message);

        recipientRepository.saveAll(recipients);

        eventPublisher.publishEvent(message);

        return MessageResponse.builder()
                .id(message.getId())
                .subject(message.getSubject())
                .targetType(message.getTargetType())
                .totalRecipients(recipients.size())
                .sentAt(message.getSentAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getSentMessages(User admin, Pageable pageable) {
        Page<Message> page = isSuperAdmin(admin)
                ? messageRepository.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "sentAt")))
                : messageRepository.findBySentByIdOrderBySentAtDesc(admin.getId(), pageable);

        return page.map(msg -> MessageResponse.builder()
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
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));

        if (!isSuperAdmin(admin) && !message.getSentBy().getId().equals(admin.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
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

    private boolean isSuperAdmin(User user) {
        return user.getRole() == RoleType.SUPER_ADMIN;
    }
}
