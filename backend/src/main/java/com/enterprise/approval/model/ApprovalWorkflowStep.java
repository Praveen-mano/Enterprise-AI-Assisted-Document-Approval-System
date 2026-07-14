package com.enterprise.approval.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "approval_workflow_steps")
public class ApprovalWorkflowStep {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_id", nullable = false)
  @JsonIgnore
  private ApprovalWorkflow workflow;

  @Column(nullable = false)
  private Integer stepOrder = 1;

  @Column(nullable = false)
  private String approvalMode = "SEQUENTIAL";

  @Column(nullable = false, length = 1000)
  private String approverRoles;

  @Column(nullable = false)
  private Integer dueHours = 24;

  @Column(nullable = false)
  private String escalationAction = "NOTIFY";

  @Column
  private String escalationRole;

  @Column(nullable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public ApprovalWorkflow getWorkflow() {
    return workflow;
  }

  public void setWorkflow(ApprovalWorkflow workflow) {
    this.workflow = workflow;
  }

  public Integer getStepOrder() {
    return stepOrder;
  }

  public void setStepOrder(Integer stepOrder) {
    this.stepOrder = stepOrder;
  }

  public String getApprovalMode() {
    return approvalMode;
  }

  public void setApprovalMode(String approvalMode) {
    this.approvalMode = approvalMode;
  }

  public String getApproverRoles() {
    return approverRoles;
  }

  public void setApproverRoles(String approverRoles) {
    this.approverRoles = approverRoles;
  }

  public Integer getDueHours() {
    return dueHours;
  }

  public void setDueHours(Integer dueHours) {
    this.dueHours = dueHours;
  }

  public String getEscalationAction() {
    return escalationAction;
  }

  public void setEscalationAction(String escalationAction) {
    this.escalationAction = escalationAction;
  }

  public String getEscalationRole() {
    return escalationRole;
  }

  public void setEscalationRole(String escalationRole) {
    this.escalationRole = escalationRole;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
