package com.enterprise.approval.controller;

import com.enterprise.approval.model.AppUser;
import com.enterprise.approval.model.NotificationRecord;
import com.enterprise.approval.repository.AppUserRepository;
import com.enterprise.approval.repository.NotificationRecordRepository;
import com.enterprise.approval.security.JwtService;
import com.enterprise.approval.service.EmailService;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
  private static final Set<String> SELF_REGISTER_ROLES = Set.of("Employee", "General");

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final NotificationRecordRepository notifications;
  private final EmailService emailService;
  private final JwtService jwtService;

  public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, NotificationRecordRepository notifications, EmailService emailService, JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.notifications = notifications;
    this.emailService = emailService;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public Map<String, Object> login(@RequestBody Map<String, String> request) {
    String email = request.getOrDefault("email", "").trim().toLowerCase();
    String password = request.getOrDefault("password", "");

    AppUser user = users.findByEmail(email)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

    if (!Boolean.TRUE.equals(user.getActive())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is inactive");
    }

    if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
      user.setPasswordHash(passwordEncoder.encode("enterprise-ai"));
      users.save(user);
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    return Map.of(
      "token", jwtService.createToken(user),
      "expiresIn", 3600,
      "user", Map.of(
        "id", user.getId(),
        "name", user.getDisplayName(),
        "email", user.getEmail(),
        "role", user.getRoleName(),
        "department", user.getDepartment()
      )
    );
  }

  @PostMapping("/register")
  public Map<String, Object> register(@RequestBody Map<String, String> request) {
    String email = request.getOrDefault("email", "").trim().toLowerCase();
    String displayName = request.getOrDefault("displayName", email);
    String password = request.getOrDefault("password", "");
    String role = request.getOrDefault("role", "Employee").trim();
    String department = request.getOrDefault("department", "Operations");

    if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email, and password are required");
    }

    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
    }

    if (password.length() < 8) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters");
    }

    if (!SELF_REGISTER_ROLES.contains(role)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only Employee and General accounts can be created here");
    }

    if (users.findByEmail(email).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists");
    }

    AppUser user = new AppUser();
    user.setEmail(email);
    user.setDisplayName(displayName);
    user.setRoleName(role);
    user.setDepartment(department);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setActive(true);
    users.save(user);

    NotificationRecord nr = new NotificationRecord();
    nr.setRecipientRole(role);
    nr.setChannel("in-app");
    nr.setTitle("Account created");
    nr.setMessage("Welcome " + displayName + ". Your " + role + " workspace is ready.");
    nr.setRecipientEmail(email);
    notifications.save(nr);
    emailService.sendSimple(email, "ApprovalOS account created", nr.getMessage());

    return Map.of(
      "token", jwtService.createToken(user),
      "expiresIn", 3600,
      "user", Map.of(
        "id", user.getId(),
        "name", user.getDisplayName(),
        "email", user.getEmail(),
        "role", user.getRoleName(),
        "department", user.getDepartment()
      )
    );
  }
}
