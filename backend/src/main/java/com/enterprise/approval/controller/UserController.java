package com.enterprise.approval.controller;

import com.enterprise.approval.model.AppUser;
import com.enterprise.approval.model.NotificationRecord;
import com.enterprise.approval.repository.AppUserRepository;
import com.enterprise.approval.repository.NotificationRecordRepository;
import com.enterprise.approval.service.EmailService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {
  private final AppUserRepository users;
  private final NotificationRecordRepository notifications;
  private final EmailService emailService;

  public UserController(AppUserRepository users, NotificationRecordRepository notifications, EmailService emailService) {
    this.users = users;
    this.notifications = notifications;
    this.emailService = emailService;
  }

  @GetMapping
  public List<AppUser> allUsers(@RequestParam(required = false) String role, Authentication authentication) {
    requireCfo(authentication);
    if (role != null && !role.isBlank()) {
      return users.findByRoleName(role);
    }
    return users.findAll();
  }

  @PostMapping
  public AppUser saveUser(@RequestBody AppUser user, Authentication authentication) {
    requireCfo(authentication);
    return users.save(user);
  }

  @PostMapping("/{id}/approve")
  public AppUser approveUser(@PathVariable Long id, Authentication authentication) {
    requireCfo(authentication);
    AppUser user = users.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
    user.setActive(true);
    users.save(user);

    NotificationRecord nr = new NotificationRecord();
    nr.setRecipientRole("All");
    nr.setRecipientEmail(user.getEmail());
    nr.setChannel("email");
    nr.setTitle("Your account has been approved");
    nr.setMessage("Hello " + user.getDisplayName() + ", your account has been approved and is now active.");
    notifications.save(nr);
    if (nr.getRecipientEmail() != null && !nr.getRecipientEmail().isBlank()) {
      emailService.sendSimple(nr.getRecipientEmail(), nr.getTitle(), nr.getMessage());
    }

    return user;
  }

  private void requireCfo(Authentication authentication) {
    boolean cfo = authentication.getAuthorities().stream()
      .anyMatch(authority -> "ROLE_CFO".equals(authority.getAuthority()));
    if (!cfo) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CFO access required");
    }
  }
}
