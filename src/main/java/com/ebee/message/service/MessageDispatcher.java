package com.ebee.message.service;

import com.ebee.message.entity.Message;
import com.ebee.message.entity.MessageRecipient;
import com.ebee.message.repository.MessageRecipientRepository;
import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.SmsRequest;
import com.ebee.notification.sender.EmailSender;
import com.ebee.notification.sender.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatcher {

    private final MessageRecipientRepository recipientRepository;
    private final SmsSender smsSender;
    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Message message) {
        List<MessageRecipient> recipients = recipientRepository.findByMessageId(message.getId(),
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).getContent();

        for (MessageRecipient recipient : recipients) {
            boolean sentSms = false;
            boolean sentEmail = false;

            if (recipient.getPhoneNumber() != null) {
                try {
                    smsSender.send(SmsRequest.builder()
                            .phoneNumbers(List.of(recipient.getPhoneNumber()))
                            .message(message.getBody())
                            .build());
                    sentSms = true;
                } catch (Exception e) {
                    log.warn("Failed to send SMS to {}: {}", recipient.getPhoneNumber(), e.getMessage());
                }
            }

            if (recipient.getEmail() != null) {
                try {
                    emailSender.send(EmailRequest.builder()
                            .to(recipient.getEmail())
                            .subject(message.getSubject())
                            .body(message.getBody())
                            .html(false)
                            .build());
                    sentEmail = true;
                } catch (Exception e) {
                    log.warn("Failed to send email to {}: {}", recipient.getEmail(), e.getMessage());
                }
            }

            recipient.setSentSms(sentSms);
            recipient.setSentEmail(sentEmail);
            recipientRepository.save(recipient);
        }

        log.info("Dispatched message {} to {} recipients", message.getId(), recipients.size());
    }
}
