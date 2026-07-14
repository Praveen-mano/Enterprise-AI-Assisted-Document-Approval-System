package com.enterprise.approval.repository;

import com.enterprise.approval.model.DocumentTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {
  Optional<DocumentTag> findByNameIgnoreCase(String name);

  List<DocumentTag> findAllByOrderByNameAsc();
}
