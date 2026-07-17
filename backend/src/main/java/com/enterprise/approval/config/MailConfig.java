package com.enterprise.approval.config;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class MailConfig {
  @Bean
  @Conditional(MailEnabledCondition.class)
  JavaMailSender javaMailSender(
    @Value("${MAIL_HOST:smtp.gmail.com}") String host,
    @Value("${MAIL_PORT:587}") int port,
    @Value("${MAIL_USERNAME:}") String username,
    @Value("${MAIL_PASSWORD:}") String password,
    @Value("${MAIL_FROM:}") String from,
    @Value("${MAIL_STARTTLS:true}") boolean starttls
  ) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    String cleanHost = host == null ? "" : host.trim();
    String cleanUsername = username == null ? "" : username.trim();
    String cleanPassword = password == null ? "" : password.trim();
    if (cleanHost.toLowerCase().contains("gmail")) {
      cleanPassword = cleanPassword.replaceAll("\\s+", "");
    }

    sender.setHost(cleanHost);
    sender.setPort(port);
    sender.setUsername(cleanUsername);
    sender.setPassword(cleanPassword);
    sender.setDefaultEncoding("UTF-8");

    Properties properties = sender.getJavaMailProperties();
    properties.put("mail.smtp.auth", String.valueOf(!cleanUsername.isBlank()));
    properties.put("mail.smtp.starttls.enable", String.valueOf(starttls));
    properties.put("mail.smtp.starttls.required", String.valueOf(starttls));
    properties.put("mail.smtp.from", from == null || from.isBlank() ? cleanUsername : from);
    properties.put("mail.smtp.ssl.trust", "smtp.gmail.com");
    properties.put("mail.smtp.connectiontimeout", "10000");
    properties.put("mail.smtp.timeout", "10000");
    properties.put("mail.smtp.writetimeout", "10000");
    return sender;
  }
}
