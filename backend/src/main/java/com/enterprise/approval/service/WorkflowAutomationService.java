package com.enterprise.approval.service;

import com.enterprise.approval.model.ApprovalDocument;
import com.enterprise.approval.model.ApprovalTask;
import com.enterprise.approval.model.ApprovalWorkflow;
import com.enterprise.approval.model.ApprovalWorkflowStep;
import com.enterprise.approval.model.AuditLog;
import com.enterprise.approval.model.NotificationRecord;
import com.enterprise.approval.repository.ApprovalDocumentRepository;
import com.enterprise.approval.repository.ApprovalTaskRepository;
import com.enterprise.approval.repository.ApprovalWorkflowRepository;
import com.enterprise.approval.repository.AuditLogRepository;
import com.enterprise.approval.repository.NotificationRecordRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowAutomationService {
  private static final Set<String> APPROVERS = Set.of("HR", "Manager", "CFO");

  private final ApprovalWorkflowRepository workflows;
  private final ApprovalTaskRepository tasks;
  private final ApprovalDocumentRepository documents;
  private final NotificationRecordRepository notifications;
  private final AuditLogRepository auditLogs;

  public WorkflowAutomationService(
    ApprovalWorkflowRepository workflows,
    ApprovalTaskRepository tasks,
    ApprovalDocumentRepository documents,
    NotificationRecordRepository notifications,
    AuditLogRepository auditLogs
  ) {
    this.workflows = workflows;
    this.tasks = tasks;
    this.documents = documents;
    this.notifications = notifications;
    this.auditLogs = auditLogs;
  }

  @Transactional
  public ApprovalDocument startWorkflowOrFallback(ApprovalDocument document, String fallbackApprover, String fallbackMode, String fallbackExplanation) {
    Optional<ApprovalWorkflow> workflow = findMatchingWorkflow(document);
    if (workflow.isPresent() && !workflow.get().getSteps().isEmpty()) {
      ApprovalWorkflow selected = workflow.get();
      document.setRoutingMode("CONFIGURABLE_WORKFLOW");
      document.setApprovalChain(describeWorkflow(selected));
      document.setAgenticDecision("Configurable workflow selected: " + selected.getName() + ". " + describeWorkflow(selected));
      openStep(document, selected, firstStep(selected));
      return documents.save(document);
    }

    document.setRoutingMode(fallbackMode);
    document.setApprovalChain(fallbackApprover);
    document.setCurrentApproverRole(fallbackApprover);
    document.setAgenticDecision(fallbackExplanation);
    return documents.save(document);
  }

  public boolean hasWorkflowTasks(Long documentId) {
    return !tasks.findByDocumentIdOrderByStepOrderAscIdAsc(documentId).isEmpty();
  }

  public boolean canAct(Long documentId, String actorRole) {
    return documents.findById(documentId)
      .filter(document -> currentApproverIncludes(document, actorRole))
      .flatMap(document -> tasks.findFirstByDocumentIdAndApproverRoleAndStatusOrderByStepOrderAscIdAsc(documentId, actorRole, "PENDING"))
      .isPresent();
  }

  @Transactional
  public ApprovalDocument decide(ApprovalDocument document, String actorEmail, String actorRole, String action, String note) {
    ApprovalTask task = tasks.findFirstByDocumentIdAndApproverRoleAndStatusOrderByStepOrderAscIdAsc(document.getId(), actorRole, "PENDING")
      .orElseThrow();

    task.setStatus(action);
    task.setDecidedBy(actorEmail);
    task.setDecisionNote(note);
    task.setDecidedAt(Instant.now());
    tasks.save(task);

    if ("REJECTED".equals(action) || "CLARIFICATION".equals(action)) {
      cancelPendingTasks(document.getId(), action);
      document.setStatus(action);
      document.setClarificationNote("CLARIFICATION".equals(action) ? note : null);
      document.setCurrentApproverRole(null);
      document.setUpdatedAt(Instant.now());
      return documents.save(document);
    }

    if (activeStepCompleted(document.getId(), task.getStepOrder())) {
      ApprovalWorkflow workflow = task.getWorkflow();
      ApprovalWorkflowStep nextStep = nextStep(workflow, task.getStepOrder());
      if (nextStep == null) {
        document.setStatus("APPROVED");
        document.setCurrentApproverRole(null);
      } else {
        openStep(document, workflow, nextStep);
      }
    }

    document.setUpdatedAt(Instant.now());
    return documents.save(document);
  }

  public List<ApprovalDocument> documentsAssignedToRole(String role) {
    List<Long> ids = tasks.findByApproverRoleAndStatusOrderByCreatedAtDesc(role, "PENDING").stream()
      .map(task -> task.getDocument().getId())
      .distinct()
      .toList();
    return ids.stream()
      .map(documents::findById)
      .flatMap(Optional::stream)
      .filter(document -> currentApproverIncludes(document, role))
      .sorted(Comparator.comparing(ApprovalDocument::getCreatedAt).reversed())
      .toList();
  }

  private boolean currentApproverIncludes(ApprovalDocument document, String role) {
    String current = normalize(document.getCurrentApproverRole()).toLowerCase(Locale.ROOT);
    String expected = normalize(role).toLowerCase(Locale.ROOT);
    return !expected.isBlank() && List.of(current.split(",")).stream()
      .map(String::trim)
      .anyMatch(expected::equals);
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void escalateOverdueTasks() {
    Instant now = Instant.now();
    for (ApprovalTask task : tasks.findByStatusAndDueAtBeforeAndEscalatedAtIsNull("PENDING", now)) {
      ApprovalWorkflowStep step = task.getWorkflowStep();
      String action = step == null ? "NOTIFY" : normalize(step.getEscalationAction()).toUpperCase(Locale.ROOT);
      String originalRole = task.getApproverRole();
      if ("REASSIGN".equals(action) && step != null && APPROVERS.contains(normalizeRole(step.getEscalationRole()))) {
        task.setApproverRole(normalizeRole(step.getEscalationRole()));
        task.setDueAt(now.plus(Math.max(1, step.getDueHours()), ChronoUnit.HOURS));
        task.setEscalatedAt(now);
        tasks.save(task);
        refreshCurrentApprovers(task.getDocument());
        notifyRole(task.getApproverRole(), "Overdue approval reassigned", task.getDocument().getFilename() + " was reassigned from " + originalRole + ".");
        recordAudit(task.getDocument(), "system", "System", "ESCALATED", "Approval reassigned", "Overdue task reassigned from " + originalRole + " to " + task.getApproverRole());
      } else {
        task.setEscalatedAt(now);
        tasks.save(task);
        notifyRole(originalRole, "Approval request overdue", task.getDocument().getFilename() + " is overdue. Please review it.");
        recordAudit(task.getDocument(), "system", "System", "ESCALATED", "Overdue approval reminder", "Overdue reminder sent to " + originalRole);
      }
    }
  }

  private Optional<ApprovalWorkflow> findMatchingWorkflow(ApprovalDocument document) {
    double amount = parseAmount(document.getAmountDetected());
    return workflows.findByEnabledTrueOrderByPriorityAscNameAsc().stream()
      .filter(this::hasAnyCriterion)
      .filter(workflow -> matches(workflow.getDocumentType(), document.getDocumentType()))
      .filter(workflow -> matches(workflow.getDocumentCategory(), document.getDocumentCategory()))
      .filter(workflow -> matches(workflow.getDepartment(), document.getDepartment()))
      .filter(workflow -> matchesAmount(workflow, amount))
      .findFirst();
  }

  private boolean hasAnyCriterion(ApprovalWorkflow workflow) {
    return !normalize(workflow.getDocumentType()).isBlank()
      || !normalize(workflow.getDocumentCategory()).isBlank()
      || !normalize(workflow.getDepartment()).isBlank()
      || workflow.getMinAmount() != null
      || workflow.getMaxAmount() != null;
  }

  private boolean matchesAmount(ApprovalWorkflow workflow, double amount) {
    if (workflow.getMinAmount() != null && amount < workflow.getMinAmount()) {
      return false;
    }
    if (workflow.getMaxAmount() != null && amount > workflow.getMaxAmount()) {
      return false;
    }
    return true;
  }

  private double parseAmount(String value) {
    if (value == null) {
      return 0;
    }
    String cleaned = value.replaceAll("[^0-9.]", "");
    try {
      return cleaned.isBlank() ? 0 : Double.parseDouble(cleaned);
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private boolean matches(String expected, String actual) {
    String normalizedExpected = normalize(expected);
    if (normalizedExpected.isBlank()) {
      return true;
    }
    return normalizedExpected.equalsIgnoreCase(normalize(actual));
  }

  private ApprovalWorkflowStep firstStep(ApprovalWorkflow workflow) {
    return workflow.getSteps().stream()
      .min(Comparator.comparing(ApprovalWorkflowStep::getStepOrder))
      .orElseThrow();
  }

  private ApprovalWorkflowStep nextStep(ApprovalWorkflow workflow, int currentOrder) {
    return workflow.getSteps().stream()
      .filter(step -> step.getStepOrder() > currentOrder)
      .min(Comparator.comparing(ApprovalWorkflowStep::getStepOrder))
      .orElse(null);
  }

  private void openStep(ApprovalDocument document, ApprovalWorkflow workflow, ApprovalWorkflowStep step) {
    for (String role : rolesForStep(step)) {
      ApprovalTask task = new ApprovalTask();
      task.setDocument(document);
      task.setWorkflow(workflow);
      task.setWorkflowStep(step);
      task.setStepOrder(step.getStepOrder());
      task.setApproverRole(role);
      task.setStatus("PENDING");
      task.setDueAt(Instant.now().plus(Math.max(1, step.getDueHours()), ChronoUnit.HOURS));
      tasks.save(task);
      notifyRole(role, "Document awaiting approval", document.getFilename() + " is ready for your workflow step.");
      recordAudit(document, "system", "System", "WORKFLOW_STEP_ASSIGNED", "Workflow approval task assigned", "Step " + step.getStepOrder() + " assigned to " + role);
    }
    refreshCurrentApprovers(document);
  }

  private List<String> rolesForStep(ApprovalWorkflowStep step) {
    return List.of(step.getApproverRoles().split(",")).stream()
      .map(this::normalizeRole)
      .filter(APPROVERS::contains)
      .distinct()
      .toList();
  }

  private boolean activeStepCompleted(Long documentId, int stepOrder) {
    return tasks.findByDocumentIdAndStatusOrderByStepOrderAscIdAsc(documentId, "PENDING").stream()
      .noneMatch(task -> task.getStepOrder() == stepOrder);
  }

  private void cancelPendingTasks(Long documentId, String reason) {
    for (ApprovalTask pending : tasks.findByDocumentIdAndStatusOrderByStepOrderAscIdAsc(documentId, "PENDING")) {
      pending.setStatus("CANCELLED_" + reason);
      pending.setDecidedAt(Instant.now());
      tasks.save(pending);
    }
  }

  private void refreshCurrentApprovers(ApprovalDocument document) {
    String activeRoles = tasks.findByDocumentIdAndStatusOrderByStepOrderAscIdAsc(document.getId(), "PENDING").stream()
      .map(ApprovalTask::getApproverRole)
      .collect(Collectors.toCollection(LinkedHashSet::new))
      .stream()
      .collect(Collectors.joining(", "));
    document.setCurrentApproverRole(activeRoles.isBlank() ? null : activeRoles);
    documents.save(document);
  }

  private String describeWorkflow(ApprovalWorkflow workflow) {
    return workflow.getSteps().stream()
      .map(step -> {
        String separator = "PARALLEL".equalsIgnoreCase(step.getApprovalMode()) ? " + " : " -> ";
        return rolesForStep(step).stream().collect(Collectors.joining(separator));
      })
      .collect(Collectors.joining(" -> "));
  }

  private void notifyRole(String role, String title, String message) {
    NotificationRecord notification = new NotificationRecord();
    notification.setRecipientRole(role);
    notification.setChannel("in-app");
    notification.setTitle(title);
    notification.setMessage(message);
    notifications.save(notification);
  }

  private void recordAudit(ApprovalDocument document, String actorEmail, String actorRole, String action, String comment, String details) {
    AuditLog log = new AuditLog();
    log.setActorEmail(actorEmail);
    log.setActorRole(actorRole);
    log.setAction(action);
    log.setDocumentId(document.getId());
    log.setDocumentName(document.getFilename());
    log.setComment(comment);
    log.setDetails(details);
    log.setCreatedAt(Instant.now());
    auditLogs.save(log);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }

  private String normalizeRole(String value) {
    return switch (normalize(value).toUpperCase(Locale.ROOT)) {
      case "HR" -> "HR";
      case "MANAGER" -> "Manager";
      case "CFO" -> "CFO";
      default -> normalize(value);
    };
  }
}
