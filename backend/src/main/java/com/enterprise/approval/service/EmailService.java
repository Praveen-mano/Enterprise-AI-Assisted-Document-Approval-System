package com.enterprise.approval.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {
  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private final JavaMailSender mailSender;
  private final String from;

  public EmailService(
    ObjectProvider<JavaMailSender> mailSenderProvider,
    @Value("${MAIL_FROM:no-reply@approvalos.local}") String from
  ) {
    this.mailSender = mailSenderProvider.getIfAvailable();
    this.from = from == null || from.isBlank() ? "no-reply@approvalos.local" : from.trim();
  }

  public boolean isConfigured() {
    return mailSender != null;
  }

  public boolean sendSimple(String to, String subject, String text) {
    if (to == null || to.isBlank()) {
      log.warn("Email skipped because recipient is empty. Subject: {}", subject);
      return false;
    }
    if (mailSender == null) {
      log.warn("Email skipped for {} because SMTP is not configured. Set backend/.env MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, and MAIL_FROM.", to);
      return false;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(text);
      mailSender.send(message);
      log.info("Email sent to {} with subject '{}'", to, subject);
      return true;
    } catch (Exception e) {
      log.error("Failed to send email to {} with subject '{}': {}", to, subject, e.getMessage());
      return false;
    }
  }
}
