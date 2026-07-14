package com.enterprise.approval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "approval_tasks")
public class ApprovalTask {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "document_id", nullable = false)
  @JsonIgnore
  private ApprovalDocument document;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_id")
  @JsonIgnore
  private ApprovalWorkflow workflow;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_step_id")
  @JsonIgnore
  private ApprovalWorkflowStep workflowStep;

  @Column(nullable = false)
  private Integer stepOrder = 1;

  @Column(nullable = false)
  private String approverRole;

  @Column(nullable = false)
  private String status = "PENDING";

  @Column
  private String decidedBy;

  @Column(length = 2000)
  private String decisionNote;

  @Column
  private Instant dueAt;

  @Column
  private Instant escalatedAt;

  @Column
  private Instant decidedAt;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public ApprovalDocument getDocument() {
    return document;
  }

  public void setDocument(ApprovalDocument document) {
    this.document = document;
  }

  public ApprovalWorkflow getWorkflow() {
    return workflow;
  }

  public void setWorkflow(ApprovalWorkflow workflow) {
    this.workflow = workflow;
  }

  public ApprovalWorkflowStep getWorkflowStep() {
    return workflowStep;
  }

  public void setWorkflowStep(ApprovalWorkflowStep workflowStep) {
    this.workflowStep = workflowStep;
  }

  public Integer getStepOrder() {
    return stepOrder;
  }

  public void setStepOrder(Integer stepOrder) {
    this.stepOrder = stepOrder;
  }

  public String getApproverRole() {
    return approverRole;
  }

  public void setApproverRole(String approverRole) {
    this.approverRole = approverRole;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getDecidedBy() {
    return decidedBy;
  }

  public void setDecidedBy(String decidedBy) {
    this.decidedBy = decidedBy;
  }

  public String getDecisionNote() {
    return decisionNote;
  }

  public void setDecisionNote(String decisionNote) {
    this.decisionNote = decisionNote;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public void setDueAt(Instant dueAt) {
    this.dueAt = dueAt;
  }

  public Instant getEscalatedAt() {
    return escalatedAt;
  }

  public void setEscalatedAt(Instant escalatedAt) {
    this.escalatedAt = escalatedAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
