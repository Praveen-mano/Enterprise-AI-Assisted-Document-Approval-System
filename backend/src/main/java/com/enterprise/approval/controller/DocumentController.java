package com.enterprise.approval.controller;

import com.enterprise.approval.model.ApprovalDocument;
import com.enterprise.approval.model.AuditLog;
import com.enterprise.approval.model.NotificationRecord;
import com.enterprise.approval.repository.ApprovalDocumentRepository;
import com.enterprise.approval.repository.AuditLogRepository;
import com.enterprise.approval.repository.DocumentCategoryRepository;
import com.enterprise.approval.repository.DocumentFolderRepository;
import com.enterprise.approval.repository.DocumentTagRepository;
import com.enterprise.approval.repository.NotificationRecordRepository;
import com.enterprise.approval.service.EmailService;
import com.enterprise.approval.service.WorkflowAutomationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin
public class DocumentController {
  private static final Set<String> APPROVERS = Set.of("HR", "Manager", "CFO");
  private static final String INVOICE_CATEGORY = "Invoice Documents";
  private static final String GENERAL_CATEGORY = "General Documents";

  private final ApprovalDocumentRepository documents;
  private final NotificationRecordRepository notifications;
  private final AuditLogRepository auditLogs;
  private final DocumentCategoryRepository libraryCategories;
  private final DocumentFolderRepository folders;
  private final DocumentTagRepository tags;
  private final EmailService emailService;
  private final WorkflowAutomationService workflowAutomationService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path statsPath = Paths.get(System.getProperty("user.dir"), "data", "approval_stats.json");

  public DocumentController(
    ApprovalDocumentRepository documents,
    NotificationRecordRepository notifications,
    AuditLogRepository auditLogs,
    DocumentCategoryRepository libraryCategories,
    DocumentFolderRepository folders,
    DocumentTagRepository tags,
    EmailService emailService,
    WorkflowAutomationService workflowAutomationService
  ) {
    this.documents = documents;
    this.notifications = notifications;
    this.auditLogs = auditLogs;
    this.libraryCategories = libraryCategories;
    this.folders = folders;
    this.tags = tags;
    this.emailService = emailService;
    this.workflowAutomationService = workflowAutomationService;
    ensureStatsFile();
  }

  @GetMapping
  public List<ApprovalDocument> documents(Authentication authentication) {
    String role = role(authentication);
    if ("Employee".equals(role) || "General".equals(role)) {
      return documents.findByOwnerEmailOrderByCreatedAtDesc(authentication.getName());
    }
    if ("Admin".equals(role)) {
      return documents.findAllByOrderByCreatedAtDesc();
    }
    if (APPROVERS.contains(role)) {
      return Stream.concat(
          documents.findByCurrentApproverRoleOrderByCreatedAtDesc(role).stream(),
          workflowAutomationService.documentsAssignedToRole(role).stream()
        )
        .distinct()
        .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
        .toList();
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role cannot access documents");
  }

  @PostMapping
  public ApprovalDocument create(
    @RequestBody ApprovalDocument document,
    Authentication authentication
  ) {
    String senderRole = role(authentication);
    if (!Set.of("Employee", "General").contains(senderRole)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Employee or General can submit documents");
    }

    String category = normalizeCategory(document);
    RoutingDecision routing = INVOICE_CATEGORY.equals(category)
      ? routeInvoice(document)
      : routeGeneral(document.getApprovalChain());

    document.setId(null);
    document.setOwnerEmail(sanitizeEmail(authentication.getName()));
    document.setOwnerRole(senderRole);
    document.setNotificationEmail(sanitizeEmail(document.getNotificationEmail()));
    applyLibraryMetadata(document);
    document.setDocumentCategory(category);
    document.setStatus("PENDING");
    document.setClarificationNote(null);
    document.setLastActionBy(authentication.getName());
    document.setLastActionRole(senderRole);
    document.setLastActionComment("Document submitted");
    document.setLastActionAt(Instant.now());
    document.setCreatedAt(Instant.now());
    document.setUpdatedAt(Instant.now());

    ApprovalDocument saved = documents.save(document);
    saved = workflowAutomationService.startWorkflowOrFallback(saved, routing.approver(), routing.mode(), routing.explanation());
    updateApprovalStats("SUBMITTED", saved.getCurrentApproverRole());
    recordAudit(saved, authentication.getName(), senderRole, "SUBMITTED", "Document submitted", saved.getAgenticDecision());
    if (!workflowAutomationService.hasWorkflowTasks(saved.getId())) {
      notifyRole(
        routing.approver(),
        "Document awaiting approval",
        saved.getFilename() + " was assigned to " + routing.approver() + "."
      );
    }
    return saved;
  }

  @PutMapping("/{id}/decision")
  public ApprovalDocument decide(
    @PathVariable Long id,
    @RequestBody Map<String, String> body,
    Authentication authentication
  ) {
    ApprovalDocument document = documents.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    String actorRole = role(authentication);
    boolean workflowBacked = workflowAutomationService.hasWorkflowTasks(document.getId());
    boolean canAct = workflowBacked
      ? workflowAutomationService.canAct(document.getId(), actorRole)
      : actorRole.equals(document.getCurrentApproverRole());
    if (!canAct) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document is not assigned to this role");
    }

    String action = body.getOrDefault("action", "").trim().toUpperCase(Locale.ROOT);
    String note = body.getOrDefault("note", "").trim();
    if (!Set.of("APPROVED", "REJECTED", "CLARIFICATION").contains(action)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported decision");
    }
    if ("CLARIFICATION".equals(action) && note.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clarification instructions are required");
    }

    String assignedRole = actorRole;
    document.setLastActionBy(authentication.getName());
    document.setLastActionRole(actorRole);
    document.setLastActionComment(note.isBlank() ? actionLabel(action) : note);
    document.setLastActionAt(Instant.now());
    document.setUpdatedAt(Instant.now());
    ApprovalDocument saved = workflowBacked
      ? workflowAutomationService.decide(document, authentication.getName(), actorRole, action, note)
      : decideLegacy(document, action, note);

    recordAudit(saved, authentication.getName(), actorRole, action, note, actorRole + " marked the document as " + actionLabel(action));
    if (!"PENDING".equals(saved.getStatus())) {
      updateApprovalStats(saved.getStatus(), assignedRole);
      notifySender(saved, saved.getStatus(), actorRole, note);
    }
    return saved;
  }

  @GetMapping("/stats")
  public Map<String, Object> stats(Authentication authentication) {
    String role = role(authentication);
    if (!Set.of("Admin", "CFO", "Manager", "HR").contains(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only approvers or admins can view workflow stats");
    }
    return loadApprovalStats();
  }

  @GetMapping("/dashboard-stats")
  public Map<String, Object> dashboardStats(Authentication authentication) {
    String actorRole = role(authentication);
    List<ApprovalDocument> scopedDocuments;
    if ("Employee".equals(actorRole) || "General".equals(actorRole)) {
      scopedDocuments = documents.findByOwnerEmailOrderByCreatedAtDesc(authentication.getName());
    } else if ("Admin".equals(actorRole)) {
      scopedDocuments = documents.findAllByOrderByCreatedAtDesc();
    } else if (APPROVERS.contains(actorRole)) {
      scopedDocuments = Stream.concat(
          documents.findByApprovalChainOrderByCreatedAtDesc(actorRole).stream(),
          workflowAutomationService.documentsAssignedToRole(actorRole).stream()
        )
        .distinct()
        .toList();
    } else {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role cannot access dashboard stats");
    }

    long pending = scopedDocuments.stream().filter(document -> "PENDING".equals(document.getStatus())).count();
    long approved = scopedDocuments.stream().filter(document -> "APPROVED".equals(document.getStatus())).count();
    long rejected = scopedDocuments.stream().filter(document -> "REJECTED".equals(document.getStatus())).count();
    long clarification = scopedDocuments.stream().filter(document -> "CLARIFICATION".equals(document.getStatus())).count();
    long invoices = scopedDocuments.stream().filter(document -> INVOICE_CATEGORY.equals(document.getDocumentCategory())).count();
    long general = scopedDocuments.stream().filter(document -> GENERAL_CATEGORY.equals(document.getDocumentCategory())).count();

    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("role", actorRole);
    stats.put("total", scopedDocuments.size());
    stats.put("pending", pending);
    stats.put("approved", approved);
    stats.put("rejected", rejected);
    stats.put("clarification", clarification);
    stats.put("invoices", invoices);
    stats.put("general", general);
    stats.put("completed", approved + rejected + clarification);
    stats.put("updatedAt", Instant.now().toString());
    return stats;
  }

  private RoutingDecision routeInvoice(ApprovalDocument document) {
    double amount = parseAmount(document.getAmountDetected());
    int risk = valueOrZero(document.getRiskScore());
    int compliance = valueOrZero(document.getComplianceScore());

    if (amount >= 1_000_000 || risk >= 75 || compliance >= 80) {
      return new RoutingDecision(
        "CFO",
        "LLM_RAG_INVOICE",
        "Invoice routed to CFO by modular invoice policy. Signals: amount INR " + Math.round(amount)
          + ", risk " + risk + "/100, compliance " + compliance + "/100."
      );
    }
    if (amount >= 100_000 || risk >= 45 || compliance >= 50) {
      return new RoutingDecision(
        "Manager",
        "LLM_RAG_INVOICE",
        "Invoice routed to Manager by modular invoice policy. Signals: amount INR " + Math.round(amount)
          + ", risk " + risk + "/100, compliance " + compliance + "/100."
      );
    }
    return new RoutingDecision(
      "HR",
      "LLM_RAG_INVOICE",
      "Invoice routed to HR by modular invoice policy. Signals: amount INR " + Math.round(amount)
        + ", risk " + risk + "/100, compliance " + compliance + "/100."
    );
  }

  private ApprovalDocument decideLegacy(ApprovalDocument document, String action, String note) {
    document.setStatus(action);
    document.setClarificationNote("CLARIFICATION".equals(action) ? note : null);
    document.setCurrentApproverRole(null);
    document.setUpdatedAt(Instant.now());
    return documents.save(document);
  }

  private void applyLibraryMetadata(ApprovalDocument document) {
    if (document.getLibraryCategoryId() != null) {
      document.setLibraryCategory(libraryCategories.findById(document.getLibraryCategoryId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document category not found")));
    } else if (document.getLibraryCategory() != null && document.getLibraryCategory().getId() != null) {
      document.setLibraryCategory(libraryCategories.findById(document.getLibraryCategory().getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document category not found")));
    } else {
      String detectedType = document.getDocumentType() == null ? "" : document.getDocumentType();
      document.setLibraryCategory(libraryCategories.findByNameIgnoreCase(detectedType)
        .or(() -> libraryCategories.findByNameIgnoreCase("General Document"))
        .orElse(null));
    }

    if (document.getFolderId() != null) {
      document.setFolder(folders.findById(document.getFolderId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder not found")));
    } else if (document.getFolder() != null && document.getFolder().getId() != null) {
      document.setFolder(folders.findById(document.getFolder().getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Folder not found")));
    }

    LinkedHashSet<com.enterprise.approval.model.DocumentTag> resolvedTags = new LinkedHashSet<>();
    List<String> requestedTags = document.getTagNames() == null ? List.of() : document.getTagNames();
    for (String rawTag : requestedTags) {
      String name = normalizeLibraryName(rawTag);
      if (name.isBlank()) {
        continue;
      }
      resolvedTags.add(tags.findByNameIgnoreCase(name).orElseGet(() -> {
        com.enterprise.approval.model.DocumentTag tag = new com.enterprise.approval.model.DocumentTag();
        tag.setName(name);
        return tags.save(tag);
      }));
    }
    if (!resolvedTags.isEmpty()) {
      document.setTags(resolvedTags);
    }
  }

  private String normalizeLibraryName(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }

  private RoutingDecision routeGeneral(String requestedRole) {
    String approver = validateDirectApprover(requestedRole);
    return new RoutingDecision(
      approver,
      "MANUAL_GENERAL",
      "General document routed manually to " + approver + " by the submitter."
    );
  }

  private String validateDirectApprover(String requestedRole) {
    String normalized = requestedRole == null ? "" : requestedRole.trim();
    if (!APPROVERS.contains(normalized)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "General must select HR, Manager, or CFO"
      );
    }
    return normalized;
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

  private int valueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  private String normalizeCategory(ApprovalDocument document) {
    String category = document.getDocumentCategory() == null ? "" : document.getDocumentCategory().trim();
    if (INVOICE_CATEGORY.equalsIgnoreCase(category) || "Invoice".equalsIgnoreCase(category)) {
      return INVOICE_CATEGORY;
    }
    if (GENERAL_CATEGORY.equalsIgnoreCase(category) || "General".equalsIgnoreCase(category)) {
      return GENERAL_CATEGORY;
    }
    String type = document.getDocumentType() == null ? "" : document.getDocumentType().toLowerCase(Locale.ROOT);
    return type.contains("invoice") ? INVOICE_CATEGORY : GENERAL_CATEGORY;
  }

  private void notifyRole(String role, String title, String message) {
    NotificationRecord notification = new NotificationRecord();
    notification.setRecipientRole(role);
    notification.setChannel("in-app");
    notification.setTitle(title);
    notification.setMessage(message);
    notifications.save(notification);
  }

  private void notifySender(ApprovalDocument document, String action, String actorRole, String note) {
    String statusLabel = switch (action) {
      case "APPROVED" -> "approved";
      case "REJECTED" -> "rejected";
      default -> "needs clarification";
    };
    String title = "Document " + statusLabel + ": " + document.getFilename();
    String summary = document.getSummary() == null || document.getSummary().isBlank()
      ? "No AI summary is available for this document."
      : document.getSummary();
    String clarification = "CLARIFICATION".equals(action)
      ? "\n\nClarification required:\n" + note
      : "";
    String message = "Hello,\n\nYour uploaded document has been " + statusLabel + ".\n\n"
      + "Document: " + document.getFilename() + "\n"
      + "Reviewed by: " + actorRole + "\n"
      + "Approval route: " + document.getApprovalChain() + "\n\n"
      + "AI summary:\n" + summary
      + clarification
      + "\n\nThank you for using ApprovalOS.";

    notifySender(document.getOwnerEmail(), document.getOwnerRole(), title, message);
    if (document.getNotificationEmail() != null
      && !document.getNotificationEmail().isBlank()
      && !document.getNotificationEmail().equalsIgnoreCase(document.getOwnerEmail())) {
      notifySender(document.getNotificationEmail(), document.getOwnerRole(), title, message);
    }
  }

  private void notifySender(String email, String ownerRole, String title, String message) {
    NotificationRecord notification = new NotificationRecord();
    notification.setRecipientRole(ownerRole == null || ownerRole.isBlank() ? "Employee" : ownerRole);
    notification.setRecipientEmail(email);
    notification.setChannel("in-app");
    notification.setTitle(title);
    notification.setMessage(message);
    notifications.save(notification);
    emailService.sendSimple(email, title, message);
  }

  private void recordAudit(ApprovalDocument document, String actorEmail, String actorRole, String action, String comment, String details) {
    AuditLog log = new AuditLog();
    log.setActorEmail(actorEmail);
    log.setActorRole(actorRole);
    log.setAction(action);
    log.setDocumentId(document.getId());
    log.setDocumentName(document.getFilename());
    log.setComment(comment == null || comment.isBlank() ? actionLabel(action) : comment);
    log.setDetails(details);
    log.setCreatedAt(Instant.now());
    auditLogs.save(log);
  }

  private String actionLabel(String action) {
    return switch (action) {
      case "APPROVED" -> "Approved";
      case "REJECTED" -> "Rejected";
      case "CLARIFICATION" -> "Clarification requested";
      case "SUBMITTED" -> "Submitted";
      default -> action;
    };
  }

  private String role(Authentication authentication) {
    if (authentication.getDetails() instanceof String role) {
      return role;
    }
    return authentication.getAuthorities().stream()
      .findFirst()
      .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
      .map(value -> switch (value) {
        case "EMPLOYEE" -> "Employee";
        case "GENERAL" -> "General";
        case "MANAGER" -> "Manager";
        case "ADMIN" -> "Admin";
        default -> value;
      })
      .orElse("");
  }

  private String sanitizeEmail(String email) {
    if (email == null || email.isBlank()) {
      return null;
    }
    String cleaned = email.trim().toLowerCase(Locale.ROOT);
    if (!cleaned.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid notification email");
    }
    return cleaned;
  }

  private synchronized void ensureStatsFile() {
    try {
      Files.createDirectories(statsPath.getParent());
      if (!Files.exists(statsPath)) {
        saveApprovalStats(defaultStats());
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to initialize approval stats file", exception);
    }
  }

  private synchronized void updateApprovalStats(String action, String approverRole) {
    Map<String, Object> stats = loadApprovalStats();
    Map<String, Object> totals = getNestedMap(stats, "totals");
    Map<String, Object> byRole = getNestedMap(stats, "byRole");
    String roleKey = approverRole == null || approverRole.isBlank() ? "Unassigned" : approverRole;

    incrementCounter(totals, "submitted", "SUBMITTED".equals(action));
    incrementCounter(totals, "pending", "SUBMITTED".equals(action));
    incrementCounter(totals, "pending", "APPROVED".equals(action) ? -1 : 0);
    incrementCounter(totals, "pending", "REJECTED".equals(action) ? -1 : 0);
    incrementCounter(totals, "pending", "CLARIFICATION".equals(action) ? -1 : 0);
    incrementCounter(totals, "approved", "APPROVED".equals(action));
    incrementCounter(totals, "rejected", "REJECTED".equals(action));
    incrementCounter(totals, "clarification", "CLARIFICATION".equals(action));

    Map<String, Object> roleStats = getNestedMap(byRole, roleKey);
    incrementCounter(roleStats, "assigned", "SUBMITTED".equals(action));
    incrementCounter(roleStats, "pending", "SUBMITTED".equals(action));
    incrementCounter(roleStats, "pending", "APPROVED".equals(action) ? -1 : 0);
    incrementCounter(roleStats, "pending", "REJECTED".equals(action) ? -1 : 0);
    incrementCounter(roleStats, "pending", "CLARIFICATION".equals(action) ? -1 : 0);
    incrementCounter(roleStats, "approved", "APPROVED".equals(action));
    incrementCounter(roleStats, "rejected", "REJECTED".equals(action));
    incrementCounter(roleStats, "clarification", "CLARIFICATION".equals(action));

    stats.put("updatedAt", Instant.now().toString());
    saveApprovalStats(stats);
  }

  private Map<String, Object> loadApprovalStats() {
    try {
      if (!Files.exists(statsPath)) {
        return defaultStats();
      }
      String content = Files.readString(statsPath, StandardCharsets.UTF_8);
      if (content == null || content.isBlank()) {
        return defaultStats();
      }
      return objectMapper.readValue(content, new TypeReference<>() {});
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read approval stats file", exception);
    }
  }

  private void saveApprovalStats(Map<String, Object> stats) {
    try {
      Files.createDirectories(statsPath.getParent());
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(statsPath.toFile(), stats);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to write approval stats file", exception);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getNestedMap(Map<String, Object> root, String key) {
    Object value = root.computeIfAbsent(key, ignored -> new LinkedHashMap<String, Object>());
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    Map<String, Object> replacement = new LinkedHashMap<>();
    root.put(key, replacement);
    return replacement;
  }

  private void incrementCounter(Map<String, Object> container, String key, boolean increment) {
    incrementCounter(container, key, increment ? 1 : 0);
  }

  private void incrementCounter(Map<String, Object> container, String key, int delta) {
    Number current = (Number) container.getOrDefault(key, 0);
    container.put(key, current.intValue() + delta);
  }

  private Map<String, Object> defaultStats() {
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("updatedAt", Instant.now().toString());
    stats.put("totals", Map.of(
      "submitted", 0,
      "pending", 0,
      "approved", 0,
      "rejected", 0,
      "clarification", 0
    ));
    stats.put("byRole", new LinkedHashMap<String, Object>());
    return stats;
  }

  private record RoutingDecision(String approver, String mode, String explanation) {}
}
