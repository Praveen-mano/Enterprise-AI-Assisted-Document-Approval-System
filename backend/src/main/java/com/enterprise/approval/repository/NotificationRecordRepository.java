package com.enterprise.approval.repository;

import com.enterprise.approval.model.NotificationRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {
  List<NotificationRecord> findByRecipientRoleOrRecipientRole(String roleName, String allRole);

  List<NotificationRecord> findByRecipientEmailOrRecipientRoleOrderByCreatedAtDesc(
    String recipientEmail,
    String recipientRole
  );
}
