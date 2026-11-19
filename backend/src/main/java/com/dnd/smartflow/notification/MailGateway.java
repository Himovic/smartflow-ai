package com.dnd.smartflow.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Sends e-mails through Spring Mail when SMTP is configured; otherwise logs that delivery was skipped. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailGateway {
    private final ObjectProvider<JavaMailSender> sender;

    @Value("${smartflow.mail.enabled:false}")
    private boolean enabled;

    @Value("${smartflow.mail.from:no-reply@smartflow.local}")
    private String from;

    @Async
    public void send(String to, String subject, String body) {
        JavaMailSender s = sender.getIfAvailable();
        if (!enabled || s == null) {
            log.info("Mail delivery disabled — message '{}' to {} not sent", subject, to);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            s.send(msg);
        } catch (Exception e) {
            log.warn("Mail delivery to {} failed: {}", to, e.getMessage());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
