package com.enterprise.approval.controller;

import com.enterprise.approval.model.AuditLog;
import com.enterprise.approval.model.ApprovalDocument;
import com.enterprise.approval.repository.ApprovalDocumentRepository;
import com.enterprise.approval.repository.AuditLogRepository;
import com.enterprise.approval.service.WorkflowAutomationService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/audit")
@CrossOrigin
public class AuditController {
  private static final Set<String> APPROVERS = Set.of("HR", "Manager", "CFO");

  private final AuditLogRepository auditLogs;
  private final ApprovalDocumentRepository documents;
  private final WorkflowAutomationService workflowAutomationService;

  public AuditController(
    AuditLogRepository auditLogs,
    ApprovalDocumentRepository documents,
    WorkflowAutomationService workflowAutomationService
  ) {
    this.auditLogs = auditLogs;
    this.documents = documents;
    this.workflowAutomationService = workflowAutomationService;
  }

  @GetMapping
  public List<AuditLog> logs(@RequestParam(required = false) String role, Authentication authentication) {
    requireAdminOrCfo(authentication);
    if (role != null && !role.isBlank()) {
      return auditLogs.findByActorRole(role);
    }
    return auditLogs.findAllByOrderByCreatedAtDesc();
  }

  @GetMapping("/documents/{documentId}")
  public List<AuditLog> documentHistory(@PathVariable Long documentId, Authentication authentication) {
    ApprovalDocument document = documents.findById(documentId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    if (!canViewDocumentHistory(document, authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to view this document history");
    }
    return auditLogs.findByDocumentIdOrderByCreatedAtAsc(documentId);
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

  private boolean canViewDocumentHistory(ApprovalDocument document, Authentication authentication) {
    String actorRole = role(authentication);
    if ("Admin".equals(actorRole) || "CFO".equals(actorRole)) {
      return true;
    }
    if (authentication.getName().equalsIgnoreCase(Objects.toString(document.getOwnerEmail(), ""))) {
      return true;
    }
    if (actorRole.equals(document.getOwnerRole())) {
      return true;
    }
    if (actorRole.equals(document.getCurrentApproverRole())) {
      return true;
    }
    return APPROVERS.contains(actorRole) && (
      Objects.toString(document.getApprovalChain(), "").contains(actorRole)
        || workflowAutomationService.canAct(document.getId(), actorRole)
    );
  }

  private String role(Authentication authentication) {
    if (authentication.getDetails() instanceof String role) {
      return normalizeRole(role);
    }
    return authentication.getAuthorities().stream()
      .findFirst()
      .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
      .map(this::normalizeRole)
      .orElse("");
  }

  private String normalizeRole(String value) {
    if (value == null) {
      return "";
    }
    return switch (value.trim().toUpperCase()) {
      case "EMPLOYEE" -> "Employee";
      case "GENERAL" -> "General";
      case "HR" -> "HR";
      case "MANAGER" -> "Manager";
      case "CFO" -> "CFO";
      case "ADMIN" -> "Admin";
      default -> value.trim();
    };
  }
}
