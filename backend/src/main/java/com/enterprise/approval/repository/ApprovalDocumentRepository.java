package com.enterprise.approval.repository;

import com.enterprise.approval.model.ApprovalDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDocumentRepository extends JpaRepository<ApprovalDocument, Long> {
  List<ApprovalDocument> findAllByOrderByCreatedAtDesc();

  List<ApprovalDocument> findByDepartment(String department);

  List<ApprovalDocument> findByPriority(String priority);

  List<ApprovalDocument> findByDocumentCategoryOrderByCreatedAtDesc(String documentCategory);

  List<ApprovalDocument> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

  List<ApprovalDocument> findByCurrentApproverRoleOrderByCreatedAtDesc(String currentApproverRole);

  List<ApprovalDocument> findByApprovalChainOrderByCreatedAtDesc(String approvalChain);
}
