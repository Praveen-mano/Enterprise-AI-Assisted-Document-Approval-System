package com.enterprise.approval.controller;

import com.enterprise.approval.model.ApprovalDocument;
import com.enterprise.approval.model.DocumentCategory;
import com.enterprise.approval.model.DocumentFolder;
import com.enterprise.approval.model.DocumentTag;
import com.enterprise.approval.repository.ApprovalDocumentRepository;
import com.enterprise.approval.repository.DocumentCategoryRepository;
import com.enterprise.approval.repository.DocumentFolderRepository;
import com.enterprise.approval.repository.DocumentTagRepository;
import com.enterprise.approval.service.WorkflowAutomationService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/library")
@CrossOrigin
public class DocumentLibraryController {
  private static final Set<String> APPROVERS = Set.of("HR", "Manager", "CFO");

  private final ApprovalDocumentRepository documents;
  private final DocumentCategoryRepository categories;
  private final DocumentFolderRepository folders;
  private final DocumentTagRepository tags;
  private final WorkflowAutomationService workflowAutomationService;

  public DocumentLibraryController(
    ApprovalDocumentRepository documents,
    DocumentCategoryRepository categories,
    DocumentFolderRepository folders,
    DocumentTagRepository tags,
    WorkflowAutomationService workflowAutomationService
  ) {
    this.documents = documents;
    this.categories = categories;
    this.folders = folders;
    this.tags = tags;
    this.workflowAutomationService = workflowAutomationService;
  }

  @GetMapping("/metadata")
  public Map<String, Object> metadata() {
    return Map.of(
      "categories", categories.findAllByOrderByNameAsc(),
      "folders", folders.findAllByOrderByNameAsc(),
      "tags", tags.findAllByOrderByNameAsc()
    );
  }

  @GetMapping("/documents")
  public List<ApprovalDocument> libraryDocuments(
    @RequestParam(defaultValue = "") String search,
    @RequestParam(required = false) Long categoryId,
    @RequestParam(required = false) Long folderId,
    @RequestParam(defaultValue = "") String tag,
    @RequestParam(defaultValue = "") String status,
    @RequestParam(defaultValue = "") String owner,
    @RequestParam(defaultValue = "uploadedAt") String sortBy,
    @RequestParam(defaultValue = "desc") String direction,
    Authentication authentication
  ) {
    Stream<ApprovalDocument> stream = visibleDocuments(authentication).stream();
    String normalizedSearch = normalize(search).toLowerCase(Locale.ROOT);
    String normalizedTag = normalize(tag).toLowerCase(Locale.ROOT);
    String normalizedStatus = normalize(status).toUpperCase(Locale.ROOT);
    String normalizedOwner = normalize(owner).toLowerCase(Locale.ROOT);

    if (!normalizedSearch.isBlank()) {
      stream = stream.filter(document -> searchableText(document).contains(normalizedSearch));
    }
    if (categoryId != null) {
      stream = stream.filter(document -> document.getLibraryCategory() != null
        && categoryId.equals(document.getLibraryCategory().getId()));
    }
    if (folderId != null) {
      stream = stream.filter(document -> document.getFolder() != null
        && folderId.equals(document.getFolder().getId()));
    }
    if (!normalizedTag.isBlank()) {
      stream = stream.filter(document -> document.getTags().stream()
        .anyMatch(item -> item.getName().equalsIgnoreCase(normalizedTag)));
    }
    if (!normalizedStatus.isBlank()) {
      stream = stream.filter(document -> Objects.toString(document.getStatus(), "")
        .equalsIgnoreCase(normalizedStatus));
    }
    if (!normalizedOwner.isBlank()) {
      stream = stream.filter(document -> Objects.toString(document.getOwnerEmail(), "")
        .toLowerCase(Locale.ROOT)
        .contains(normalizedOwner));
    }

    Comparator<ApprovalDocument> comparator = comparator(sortBy);
    if ("desc".equalsIgnoreCase(direction)) {
      comparator = comparator.reversed();
    }
    return stream.sorted(comparator).toList();
  }

  @PostMapping("/categories")
  public DocumentCategory createCategory(@RequestBody Map<String, String> body, Authentication authentication) {
    requireAdmin(authentication);
    String name = requireName(body.get("name"));
    return categories.findByNameIgnoreCase(name).orElseGet(() -> {
      DocumentCategory category = new DocumentCategory();
      category.setName(name);
      category.setDescription(normalize(body.get("description")));
      return categories.save(category);
    });
  }

  @PostMapping("/folders")
  public DocumentFolder createFolder(@RequestBody Map<String, String> body, Authentication authentication) {
    requireSubmitterOrAdmin(authentication);
    String name = requireName(body.get("name"));
    Long parentId = parseLong(body.get("parentId"));
    DocumentFolder parent = parentId == null ? null : folders.findById(parentId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parent folder not found"));
    DocumentFolder folder = new DocumentFolder();
    folder.setName(name);
    folder.setParent(parent);
    return folders.save(folder);
  }

  @PostMapping("/tags")
  public DocumentTag createTag(@RequestBody Map<String, String> body, Authentication authentication) {
    requireSubmitterOrAdmin(authentication);
    String name = requireName(body.get("name"));
    return tags.findByNameIgnoreCase(name).orElseGet(() -> {
      DocumentTag tag = new DocumentTag();
      tag.setName(name);
      return tags.save(tag);
    });
  }

  private List<ApprovalDocument> visibleDocuments(Authentication authentication) {
    String actorRole = role(authentication);
    if ("Admin".equals(actorRole)) {
      return documents.findAllByOrderByCreatedAtDesc();
    }
    if ("Employee".equals(actorRole) || "General".equals(actorRole)) {
      return documents.findByOwnerEmailOrderByCreatedAtDesc(authentication.getName());
    }
    if (APPROVERS.contains(actorRole)) {
      return Stream.concat(
          documents.findByApprovalChainOrderByCreatedAtDesc(actorRole).stream(),
          workflowAutomationService.documentsAssignedToRole(actorRole).stream()
        )
        .distinct()
        .toList();
    }
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role cannot access library");
  }

  private Comparator<ApprovalDocument> comparator(String sortBy) {
    return switch (normalize(sortBy)) {
      case "name" -> Comparator.comparing(document -> normalize(document.getFilename()), String.CASE_INSENSITIVE_ORDER);
      case "category" -> Comparator.comparing(document -> document.getLibraryCategory() == null ? "" : document.getLibraryCategory().getName(), String.CASE_INSENSITIVE_ORDER);
      case "folder" -> Comparator.comparing(document -> document.getFolder() == null ? "" : document.getFolder().getPath(), String.CASE_INSENSITIVE_ORDER);
      case "status" -> Comparator.comparing(document -> normalize(document.getStatus()), String.CASE_INSENSITIVE_ORDER);
      case "owner" -> Comparator.comparing(document -> normalize(document.getOwnerEmail()), String.CASE_INSENSITIVE_ORDER);
      default -> Comparator.comparing(document -> document.getCreatedAt() == null ? Instant.EPOCH : document.getCreatedAt());
    };
  }

  private String searchableText(ApprovalDocument document) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("name", document.getFilename());
    values.put("category", document.getLibraryCategory() == null ? "" : document.getLibraryCategory().getName());
    values.put("folder", document.getFolder() == null ? "" : document.getFolder().getPath());
    values.put("status", document.getStatus());
    values.put("owner", document.getOwnerEmail());
    values.put("approvalCategory", document.getDocumentCategory());
    values.put("type", document.getDocumentType());
    String tagText = document.getTags().stream().map(DocumentTag::getName).reduce("", (left, right) -> left + " " + right);
    values.put("tags", tagText);
    return String.join(" ", values.values()).toLowerCase(Locale.ROOT);
  }

  private void requireAdmin(Authentication authentication) {
    if (!"Admin".equals(role(authentication))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin can manage document categories");
    }
  }

  private void requireSubmitterOrAdmin(Authentication authentication) {
    if (!Set.of("Employee", "General", "Admin").contains(role(authentication))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only submitters or Admin can manage library metadata");
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

  private String normalizeRole(String value) {
    if (value == null) {
      return "";
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "EMPLOYEE" -> "Employee";
      case "GENERAL" -> "General";
      case "HR" -> "HR";
      case "MANAGER" -> "Manager";
      case "CFO" -> "CFO";
      case "ADMIN" -> "Admin";
      default -> value.trim();
    };
  }

  private String requireName(String value) {
    String name = normalize(value);
    if (name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
    }
    return name;
  }

  private Long parseLong(String value) {
    try {
      String normalized = normalize(value);
      return normalized.isBlank() ? null : Long.parseLong(normalized);
    } catch (NumberFormatException ignored) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid id");
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ");
  }
}
