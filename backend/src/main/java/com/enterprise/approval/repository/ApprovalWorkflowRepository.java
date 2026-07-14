package com.enterprise.approval.repository;

import com.enterprise.approval.model.ApprovalWorkflow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Long> {
  List<ApprovalWorkflow> findAllByOrderByPriorityAscNameAsc();

  List<ApprovalWorkflow> findByEnabledTrueOrderByPriorityAscNameAsc();
}
