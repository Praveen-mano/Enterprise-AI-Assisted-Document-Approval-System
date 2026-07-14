package com.enterprise.approval.repository;

import com.enterprise.approval.model.ApprovalTask;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {
  List<ApprovalTask> findByDocumentIdOrderByStepOrderAscIdAsc(Long documentId);

  List<ApprovalTask> findByDocumentIdAndStatusOrderByStepOrderAscIdAsc(Long documentId, String status);

  Optional<ApprovalTask> findFirstByDocumentIdAndApproverRoleAndStatusOrderByStepOrderAscIdAsc(Long documentId, String approverRole, String status);

  List<ApprovalTask> findByApproverRoleAndStatusOrderByCreatedAtDesc(String approverRole, String status);

  List<ApprovalTask> findByStatusAndDueAtBeforeAndEscalatedAtIsNull(String status, Instant dueAt);
}
