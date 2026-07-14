package com.enterprise.approval.repository;

import com.enterprise.approval.model.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByEmail(String email);

  List<AppUser> findByRoleName(String roleName);
}
