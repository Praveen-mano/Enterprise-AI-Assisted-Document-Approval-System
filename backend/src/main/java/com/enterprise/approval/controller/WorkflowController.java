package com.enterprise.approval.controller;

import com.enterprise.approval.model.ApprovalTask;
import com.enterprise.approval.model.ApprovalWorkflow;
import com.enterprise.approval.model.ApprovalWorkflowStep;
import com.enterprise.approval.repository.ApprovalTaskRepository;
import com.enterprise.approval.repository.ApprovalWorkflowRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin
public class WorkflowController {
  private static final Set<String> APPROVERS = Set.of("HR", "Manager", "CFO");

  private final ApprovalWorkflowRepository workflows;
  private final ApprovalTaskRepository tasks;

  public WorkflowController(ApprovalWorkflowRepository workflows, ApprovalTaskRepository tasks) {
    this.workflows = workflows;
    this.tasks = tasks;
  }

  @GetMapping
  public List<ApprovalWorkflow> workflows(Authentication authentication) {
    requireAdmin(authentication);
    return workflows.findAllByOrderByPriorityAscNameAsc();
  }

  @PostMapping
  public ApprovalWorkflow saveWorkflow(@RequestBody ApprovalWorkflow workflow, Authentication authentication) {
    requireAdmin(authentication);
    if (normalize(workflow.getName()).isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow name is required");
    }
    if (workflow.getSteps() == null || workflow.getSteps().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one workflow step is required");
    }

    ApprovalWorkflow target = workflow.getId() == null
      ? new ApprovalWorkflow()
      : workflows.findById(workflow.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
    target.setName(normalize(workflow.getName()));
    target.setEnabled(!Boolean.FALSE.equals(workflow.getEnabled()));
    target.setDocumentType(blankToNull(workflow.getDocumentType()));
    target.setDocumentCategory(blankToNull(workflow.getDocumentCategory()));
    target.setDepartment(blankToNull(workflow.getDepartment()));
    target.setPriority(workflow.getPriority() == null ? 100 : workflow.getPriority());
    target.setUpdatedAt(Instant.now());
    target.getSteps().clear();

    int order = 1;
    for (ApprovalWorkflowStep incoming : workflow.getSteps()) {
      List<String> roles = roles(incoming.getApproverRoles());
      if (roles.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each step must include HR, Manager, or CFO");
      }
      ApprovalWorkflowStep step = new ApprovalWorkflowStep();
      step.setWorkflow(target);
      step.setStepOrder(incoming.getStepOrder() == null ? order : incoming.getStepOrder());
      step.setApprovalMode("PARALLEL".equalsIgnoreCase(incoming.getApprovalMode()) ? "PARALLEL" : "SEQUENTIAL");
      step.setApproverRoles(String.join(",", roles));
      step.setDueHours(incoming.getDueHours() == null ? 24 : Math.max(1, incoming.getDueHours()));
      step.setEscalationAction("REASSIGN".equalsIgnoreCase(incoming.getEscalationAction()) ? "REASSIGN" : "NOTIFY");
      step.setEscalationRole(normalizeRole(incoming.getEscalationRole()));
      target.getSteps().add(step);
      order++;
    }

    return workflows.save(target);
  }

  @DeleteMapping("/{id}")
  public void deleteWorkflow(@PathVariable Long id, Authentication authentication) {
    requireAdmin(authentication);
    workflows.deleteById(id);
  }

  @GetMapping("/documents/{documentId}/tasks")
  public List<ApprovalTask> documentTasks(@PathVariable Long documentId, Authentication authentication) {
    String role = role(authentication);
    if (!Set.of("Admin", "HR", "Manager", "CFO").contains(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approvers or admins can view workflow tasks");
    }
    return tasks.findByDocumentIdOrderByStepOrderAscIdAsc(documentId);
  }

  @PostMapping("/route")
  public Map<String, Object> route(@RequestBody Map<String, Object> request) {
    double amount = parseAmount(request.get("amount"));
    String approver = amount >= 1_000_000 ? "CFO" : amount >= 100_000 ? "Manager" : "HR";
    int slaHours = "CFO".equals(approver) ? 4 : "Manager".equals(approver) ? 12 : 24;
    return Map.of(
      "approvalChain", List.of(approver),
      "mode", "HUMAN_APPROVAL_REQUIRED",
      "slaDeadlineHours", slaHours,
      "escalationRule", "Notify the assigned approver when 60% of the SLA has elapsed"
    );
  }

  private double parseAmount(Object value) {
    String cleaned = String.valueOf(value == null ? "" : value).replaceAll("[^0-9.]", "");
    try {
      return cleaned.isBlank() ? 0 : Double.parseDouble(cleaned);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private void requireAdmin(Authentication authentication) {
    if (!"Admin".equals(role(authentication))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin can manage workflows");
    }
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

  private List<String> roles(String approverRoles) {
    return List.of(normalize(approverRoles).split(",")).stream()
      .map(this::normalizeRole)
      .filter(APPROVERS::contains)
      .distinct()
      .toList();
  }

  private String normalizeRole(String value) {
    return switch (normalize(value).toUpperCase(Locale.ROOT)) {
      case "EMPLOYEE" -> "Employee";
      case "GENERAL" -> "General";
      case "HR" -> "HR";
      case "MANAGER" -> "Manager";
      case "CFO" -> "CFO";
      case "ADMIN" -> "Admin";
      default -> normalize(value);
    };
  }

  private String blankToNull(String value) {
    String normalized = normalize(value);
    return normalized.isBlank() ? null : normalized;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }
}
