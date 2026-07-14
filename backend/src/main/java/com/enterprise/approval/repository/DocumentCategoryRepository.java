package com.enterprise.approval.repository;

import com.enterprise.approval.model.DocumentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {
  Optional<DocumentCategory> findByNameIgnoreCase(String name);

  List<DocumentCategory> findAllByOrderByNameAsc();
}
