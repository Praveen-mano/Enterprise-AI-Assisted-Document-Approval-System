package com.enterprise.approval.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "approval_documents")
public class ApprovalDocument {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String filename;

  private String documentType;
  private String documentCategory;
  private String routingMode;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "library_category_id")
  private DocumentCategory libraryCategory;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "folder_id")
  private DocumentFolder folder;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
    name = "approval_document_tags",
    joinColumns = @JoinColumn(name = "document_id"),
    inverseJoinColumns = @JoinColumn(name = "tag_id")
  )
  private Set<DocumentTag> tags = new LinkedHashSet<>();

  @Transient
  private Long libraryCategoryId;

  @Transient
  private Long folderId;

  @Transient
  private List<String> tagNames;

  private String department;
  private String priority;
  private String status;
  private String amountDetected;
  private Integer confidenceScore;
  private Integer riskScore;
  private Integer sensitivityScore;
  private Integer complianceScore;

  @Column(length = 4000)
  private String summary;

  @Column(length = 8000)
  private String extractedText;

  @Column(length = 2000)
  private String agenticDecision;

  @Column(length = 2000)
  private String approvalChain;

  @Column
  private String currentApproverRole;

  @Column(length = 2000)
  private String clarificationNote;

  @Column
  private String lastActionBy;

  @Column
  private String lastActionRole;

  @Column(length = 2000)
  private String lastActionComment;

  @Column
  private Instant lastActionAt;

  @Column
  private String ownerEmail;

  @Column
  private String ownerRole;

  @Column
  private String notificationEmail;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  @Column(nullable = false)
  private Instant updatedAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public String getDocumentCategory() {
    return documentCategory;
  }

  public void setDocumentCategory(String documentCategory) {
    this.documentCategory = documentCategory;
  }

  public String getRoutingMode() {
    return routingMode;
  }

  public void setRoutingMode(String routingMode) {
    this.routingMode = routingMode;
  }

  public DocumentCategory getLibraryCategory() {
    return libraryCategory;
  }

  public void setLibraryCategory(DocumentCategory libraryCategory) {
    this.libraryCategory = libraryCategory;
  }

  public DocumentFolder getFolder() {
    return folder;
  }

  public void setFolder(DocumentFolder folder) {
    this.folder = folder;
  }

  public Set<DocumentTag> getTags() {
    return tags;
  }

  public void setTags(Set<DocumentTag> tags) {
    this.tags = tags;
  }

  public Long getLibraryCategoryId() {
    return libraryCategoryId;
  }

  public void setLibraryCategoryId(Long libraryCategoryId) {
    this.libraryCategoryId = libraryCategoryId;
  }

  public Long getFolderId() {
    return folderId;
  }

  public void setFolderId(Long folderId) {
    this.folderId = folderId;
  }

  public List<String> getTagNames() {
    return tagNames;
  }

  public void setTagNames(List<String> tagNames) {
    this.tagNames = tagNames;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getAmountDetected() {
    return amountDetected;
  }

  public void setAmountDetected(String amountDetected) {
    this.amountDetected = amountDetected;
  }

  public Integer getConfidenceScore() {
    return confidenceScore;
  }

  public void setConfidenceScore(Integer confidenceScore) {
    this.confidenceScore = confidenceScore;
  }

  public Integer getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(Integer riskScore) {
    this.riskScore = riskScore;
  }

  public Integer getSensitivityScore() {
    return sensitivityScore;
  }

  public void setSensitivityScore(Integer sensitivityScore) {
    this.sensitivityScore = sensitivityScore;
  }

  public Integer getComplianceScore() {
    return complianceScore;
  }

  public void setComplianceScore(Integer complianceScore) {
    this.complianceScore = complianceScore;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getExtractedText() {
    return extractedText;
  }

  public void setExtractedText(String extractedText) {
    this.extractedText = extractedText;
  }

  public String getAgenticDecision() {
    return agenticDecision;
  }

  public void setAgenticDecision(String agenticDecision) {
    this.agenticDecision = agenticDecision;
  }

  public String getApprovalChain() {
    return approvalChain;
  }

  public void setApprovalChain(String approvalChain) {
    this.approvalChain = approvalChain;
  }

  public String getCurrentApproverRole() {
    return currentApproverRole;
  }

  public void setCurrentApproverRole(String currentApproverRole) {
    this.currentApproverRole = currentApproverRole;
  }

  public String getClarificationNote() {
    return clarificationNote;
  }

  public void setClarificationNote(String clarificationNote) {
    this.clarificationNote = clarificationNote;
  }

  public String getLastActionBy() {
    return lastActionBy;
  }

  public void setLastActionBy(String lastActionBy) {
    this.lastActionBy = lastActionBy;
  }

  public String getLastActionRole() {
    return lastActionRole;
  }

  public void setLastActionRole(String lastActionRole) {
    this.lastActionRole = lastActionRole;
  }

  public String getLastActionComment() {
    return lastActionComment;
  }

  public void setLastActionComment(String lastActionComment) {
    this.lastActionComment = lastActionComment;
  }

  public Instant getLastActionAt() {
    return lastActionAt;
  }

  public void setLastActionAt(Instant lastActionAt) {
    this.lastActionAt = lastActionAt;
  }

  public String getOwnerEmail() {
    return ownerEmail;
  }

  public void setOwnerEmail(String ownerEmail) {
    this.ownerEmail = ownerEmail;
  }

  public String getOwnerRole() {
    return ownerRole;
  }

  public void setOwnerRole(String ownerRole) {
    this.ownerRole = ownerRole;
  }

  public String getNotificationEmail() {
    return notificationEmail;
  }

  public void setNotificationEmail(String notificationEmail) {
    this.notificationEmail = notificationEmail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
