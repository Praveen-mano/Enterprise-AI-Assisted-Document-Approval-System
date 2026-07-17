package com.enterprise.approval.repository;

import com.enterprise.approval.model.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
  List<AuditLog> findByActorRole(String actorRole);

  List<AuditLog> findAllByOrderByCreatedAtDesc();

  List<AuditLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}
