package com.ebee.notification.sender.email;

import com.ebee.notification.model.EmailRequest;
import com.ebee.notification.model.NotificationResult;
import com.ebee.notification.sender.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpringEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Override
    public NotificationResult send(EmailRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());

            if (request.isHtml()) {
                helper.setText(request.getBody(), true);
            } else {
                helper.setText(request.getBody(), false);
            }

            if (request.getCc() != null) {
                helper.setCc(request.getCc().toArray(new String[0]));
            }
            if (request.getBcc() != null) {
                helper.setBcc(request.getBcc().toArray(new String[0]));
            }

            mailSender.send(message);

            log.info("Email sent successfully to: {}", request.getTo());
            return NotificationResult.builder()
                    .success(true)
                    .message("Email sent successfully")
                    .channel("EMAIL")
                    .build();

        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to: {}", request.getTo(), e);
            return NotificationResult.builder()
                    .success(false)
                    .message("Failed to send email: " + e.getMessage())
                    .channel("EMAIL")
                    .build();
        }
    }
}
