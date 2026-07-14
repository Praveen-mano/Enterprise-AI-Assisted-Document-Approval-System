package com.enterprise.approval.repository;

import com.enterprise.approval.model.DocumentFolder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, Long> {
  Optional<DocumentFolder> findByNameIgnoreCaseAndParentId(String name, Long parentId);

  List<DocumentFolder> findAllByOrderByNameAsc();
}
