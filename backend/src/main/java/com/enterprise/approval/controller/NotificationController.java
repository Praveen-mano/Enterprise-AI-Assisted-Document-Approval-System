package com.enterprise.approval.controller;

import com.enterprise.approval.model.NotificationRecord;
import com.enterprise.approval.repository.NotificationRecordRepository;
import com.enterprise.approval.service.EmailService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin
public class NotificationController {
  private final NotificationRecordRepository notifications;
  private final EmailService emailService;

  public NotificationController(NotificationRecordRepository notifications, EmailService emailService) {
    this.notifications = notifications;
    this.emailService = emailService;
  }

  @GetMapping
  public List<NotificationRecord> notifications(Authentication authentication) {
    String role = authentication.getDetails() instanceof String value
      ? value
      : authentication.getAuthorities().stream()
        .findFirst()
        .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
        .orElse("");
    return notifications.findByRecipientEmailOrRecipientRoleOrderByCreatedAtDesc(
      authentication.getName(),
      role
    );
  }

  @PostMapping
  public NotificationRecord create(@RequestBody NotificationRecord notification, Authentication authentication) {
    boolean cfo = authentication.getAuthorities().stream()
      .anyMatch(authority -> "ROLE_CFO".equals(authority.getAuthority()));
    if (!cfo) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CFO access required");
    }
    NotificationRecord nr = notifications.save(notification);
    if (nr.getRecipientEmail() != null && !nr.getRecipientEmail().isBlank()) {
      emailService.sendSimple(nr.getRecipientEmail(), nr.getTitle(), nr.getMessage());
    }
    return nr;
  }

  @PostMapping("/test-email")
  public Map<String, Object> testEmail(@RequestBody Map<String, String> request, Authentication authentication) {
    String email = request.getOrDefault("email", authentication.getName()).trim().toLowerCase(Locale.ROOT);
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
    }

    String message = "This is a test email from ApprovalOS.\n\n"
      + "If you received this, SMTP is configured correctly. Document validation emails will be sent automatically after HR, Manager, or CFO approves, rejects, or requests clarification.";
    boolean sent = emailService.sendSimple(email, "ApprovalOS SMTP test", message);
    return Map.of(
      "smtpConfigured", emailService.isConfigured(),
      "sent", sent,
      "email", email
    );
  }
}
