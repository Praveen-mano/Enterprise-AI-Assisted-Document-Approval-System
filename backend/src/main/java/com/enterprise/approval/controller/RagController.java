package com.enterprise.approval.controller;

import com.enterprise.approval.model.ApprovalDocument;
import com.enterprise.approval.model.AuditLog;
import com.enterprise.approval.repository.ApprovalDocumentRepository;
import com.enterprise.approval.repository.AuditLogRepository;
import com.enterprise.approval.service.WorkflowAutomationService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin
public class RagController {
  private static final Set<String> SUBMITTER_ROLES = Set.of("Employee", "General", "Admin");

  private final ApprovalDocumentRepository documents;
  private final AuditLogRepository auditLogs;
  private final WorkflowAutomationService workflowAutomationService;
  private final RestTemplate aiClient;
  private final String aiServiceUrl;

  public RagController(
    ApprovalDocumentRepository documents,
    AuditLogRepository auditLogs,
    WorkflowAutomationService workflowAutomationService,
    @Value("${ai-service.url:http://localhost:8000}") String aiServiceUrl
  ) {
    this.documents = documents;
    this.auditLogs = auditLogs;
    this.workflowAutomationService = workflowAutomationService;
    this.aiServiceUrl = aiServiceUrl.replaceAll("/+$", "");
    this.aiClient = new RestTemplate();
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> indexDocument(
    @RequestPart("file") MultipartFile file,
    @RequestParam(required = false) Long approvalDocumentId,
    Authentication authentication
  ) throws IOException {
    String actorRole = role(authentication);
    if (!SUBMITTER_ROLES.contains(actorRole)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only document submitters or admins can index documents");
    }

    ApprovalDocument document = approvalDocumentId == null ? null : requireVisibleDocument(approvalDocumentId, authentication);
    Map<String, Object> response = aiClient.postForObject(
      aiServiceUrl + "/rag/index",
      multipartRequest(ragMultipart(file, document, authentication, approvalDocumentId)),
      Map.class
    );

    recordAudit(
      authentication,
      document,
      "RAG_INDEXED",
      "Document indexed for RAG",
      "Indexed " + file.getOriginalFilename() + " into the vector store"
    );
    return response;
  }

  @PostMapping("/query")
  public Map<String, Object> queryDocuments(
    @RequestBody RagQueryRequest request,
    Authentication authentication
  ) {
    List<Long> requestedIds = request.documentIds() == null ? List.of() : request.documentIds();
    List<String> authorizedDocumentIds = requestedIds.isEmpty()
      ? visibleDocumentIds(authentication)
      : requestedIds.stream()
        .map(id -> requireVisibleDocument(id, authentication).getId())
        .map(String::valueOf)
        .toList();

    Map<String, Object> response = aiClient.postForObject(
      aiServiceUrl + "/rag/query",
      jsonRequest(Map.of(
        "question", Objects.toString(request.question(), ""),
        "actor_email", authentication.getName(),
        "actor_role", role(authentication),
        "document_ids", authorizedDocumentIds,
        "top_k", request.topK() == null ? 5 : request.topK()
      )),
      Map.class
    );

    recordAudit(
      authentication,
      null,
      "RAG_QUERY",
      "RAG query: " + abbreviate(request.question(), 220),
      "Queried " + authorizedDocumentIds.size() + " authorized document indexes"
    );
    return response;
  }

  @DeleteMapping("/documents/{documentId}")
  public Map<String, Object> deleteIndexedDocument(
    @PathVariable Long documentId,
    Authentication authentication
  ) {
    ApprovalDocument document = requireVisibleDocument(documentId, authentication);
    ResponseEntity<Map> aiResponse = aiClient.exchange(
      aiServiceUrl + "/rag/documents/" + documentId,
      HttpMethod.DELETE,
      null,
      Map.class
    );
    Map<String, Object> response = aiResponse.getBody();

    recordAudit(
      authentication,
      document,
      "RAG_DELETED",
      "Deleted RAG index",
      "Removed vector chunks for " + document.getFilename()
    );
    return response;
  }

  @PostMapping(value = "/documents/{documentId}/reindex", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> reindexDocument(
    @PathVariable Long documentId,
    @RequestPart("file") MultipartFile file,
    Authentication authentication
  ) throws IOException {
    ApprovalDocument document = requireVisibleDocument(documentId, authentication);
    Map<String, Object> response = aiClient.postForObject(
      aiServiceUrl + "/rag/documents/" + documentId + "/reindex",
      multipartRequest(ragMultipart(file, document, authentication, documentId)),
      Map.class
    );

    recordAudit(
      authentication,
      document,
      "RAG_REINDEXED",
      "Re-indexed document for RAG",
      "Rebuilt vector chunks for " + document.getFilename()
    );
    return response;
  }

  private MultiValueMap<String, Object> ragMultipart(
    MultipartFile file,
    ApprovalDocument document,
    Authentication authentication,
    Long documentId
  ) throws IOException {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(file.getContentType() == null
      ? MediaType.APPLICATION_OCTET_STREAM
      : MediaType.parseMediaType(file.getContentType()));
    body.add("file", new HttpEntity<>(new MultipartFileResource(file), fileHeaders));
    if (documentId != null) {
      body.add("document_id", String.valueOf(documentId));
    }
    body.add("source_document", document == null ? file.getOriginalFilename() : document.getFilename());
    body.add("owner_email", document == null ? authentication.getName() : document.getOwnerEmail());
    body.add("owner_role", document == null ? role(authentication) : document.getOwnerRole());
    body.add("allowed_roles", String.join(",", allowedRoles(document)));
    return body;
  }

  private HttpEntity<MultiValueMap<String, Object>> multipartRequest(MultiValueMap<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return new HttpEntity<>(body, headers);
  }

  private HttpEntity<Map<String, Object>> jsonRequest(Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private ApprovalDocument requireVisibleDocument(Long id, Authentication authentication) {
    ApprovalDocument document = documents.findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    if (!canAccess(document, authentication)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to use this document in RAG");
    }
    return document;
  }

  private List<String> visibleDocumentIds(Authentication authentication) {
    String actorRole = role(authentication);
    if ("Admin".equals(actorRole)) {
      return documents.findAllByOrderByCreatedAtDesc().stream()
        .map(ApprovalDocument::getId)
        .map(String::valueOf)
        .toList();
    }
    if ("Employee".equals(actorRole) || "General".equals(actorRole)) {
      return documents.findByOwnerEmailOrderByCreatedAtDesc(authentication.getName()).stream()
        .map(ApprovalDocument::getId)
        .map(String::valueOf)
        .toList();
    }
    return documents.findByCurrentApproverRoleOrderByCreatedAtDesc(actorRole).stream()
      .map(ApprovalDocument::getId)
      .map(String::valueOf)
      .toList();
  }

  private boolean canAccess(ApprovalDocument document, Authentication authentication) {
    String actorRole = role(authentication);
    if ("Admin".equals(actorRole)) {
      return true;
    }
    if (authentication.getName().equalsIgnoreCase(Objects.toString(document.getOwnerEmail(), ""))) {
      return true;
    }
    if (actorRole.equals(document.getOwnerRole())) {
      return true;
    }
    return actorRole.equals(document.getCurrentApproverRole())
      || workflowAutomationService.canAct(document.getId(), actorRole);
  }

  private List<String> allowedRoles(ApprovalDocument document) {
    if (document == null) {
      return List.of();
    }
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    if (document.getOwnerRole() != null && !document.getOwnerRole().isBlank()) {
      roles.add(document.getOwnerRole());
    }
    if (document.getCurrentApproverRole() != null && !document.getCurrentApproverRole().isBlank()) {
      roles.add(document.getCurrentApproverRole());
    }
    if (document.getApprovalChain() != null && !document.getApprovalChain().isBlank()) {
      roles.add(document.getApprovalChain());
    }
    roles.add("Admin");
    return new ArrayList<>(roles);
  }

  private void recordAudit(Authentication authentication, ApprovalDocument document, String action, String comment, String details) {
    AuditLog log = new AuditLog();
    log.setActorEmail(authentication.getName());
    log.setActorRole(role(authentication));
    log.setAction(action);
    if (document != null) {
      log.setDocumentId(document.getId());
      log.setDocumentName(document.getFilename());
    }
    log.setComment(comment);
    log.setDetails(details);
    log.setCreatedAt(Instant.now());
    auditLogs.save(log);
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

  private String abbreviate(String value, int maxLength) {
    if (value == null) {
      return "";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
  }

  private record RagQueryRequest(String question, List<Long> documentIds, Integer topK) {}

  private static class MultipartFileResource extends ByteArrayResource {
    private final String filename;

    MultipartFileResource(MultipartFile file) throws IOException {
      super(file.getBytes());
      this.filename = Objects.toString(file.getOriginalFilename(), "document");
    }

    @Override
    public String getFilename() {
      return filename;
    }

    @Override
    public long contentLength() {
      return getByteArray().length;
    }
  }

}
