package com.enterprise.approval.controller;

import com.enterprise.approval.model.AuditLog;
import com.enterprise.approval.repository.AuditLogRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin
public class AuditController {
  private final AuditLogRepository auditLogs;

  public AuditController(AuditLogRepository auditLogs) {
    this.auditLogs = auditLogs;
  }

  @GetMapping
  public List<AuditLog> logs(@RequestParam(required = false) String role, Authentication authentication) {
    requireAdminOrCfo(authentication);
    if (role != null && !role.isBlank()) {
      return auditLogs.findByActorRole(role);
    }
    return auditLogs.findAllByOrderByCreatedAtDesc();
  }

  @PostMapping
  public AuditLog create(@RequestBody AuditLog auditLog, Authentication authentication) {
    requireAdminOrCfo(authentication);
    return auditLogs.save(auditLog);
  }

  private void requireAdminOrCfo(Authentication authentication) {
    boolean allowed = authentication.getAuthorities().stream()
      .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()) || "ROLE_CFO".equals(authority.getAuthority()));
    if (!allowed) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin or CFO access required");
    }
  }
}
