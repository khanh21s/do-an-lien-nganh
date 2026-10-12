package com.parroto.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
                         @Value("${mail.from}") String fromAddress,
                         @Value("${spring.mail.host:}") String mailHost) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = mailHost != null && !mailHost.isBlank();
    }

    public void sendPaymentConfirmation(String toEmail, String planName, String endDate) {
        send(toEmail, "Thanh toán Premium thành công",
                "Cảm ơn bạn đã nâng cấp lên gói %s. Gói của bạn có hiệu lực đến %s.".formatted(planName, endDate));
    }

    public void sendExpiringSoonReminder(String toEmail, String planName, String endDate) {
        send(toEmail, "Gói Premium sắp hết hạn",
                "Gói %s của bạn sẽ hết hạn vào %s. Gia hạn ngay để không bị gián đoạn.".formatted(planName, endDate));
    }

    private void send(String toEmail, String subject, String body) {
        if (!enabled || toEmail == null || toEmail.isBlank()) {
            log.info("Email notifications disabled or no recipient; skipping '{}' to {}", subject, toEmail);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Failed to send email '{}' to {}: {}", subject, toEmail, e.getMessage());
        }
    }
}
