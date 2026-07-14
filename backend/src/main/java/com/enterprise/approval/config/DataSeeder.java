package com.enterprise.approval.config;

import com.enterprise.approval.model.AppUser;
import com.enterprise.approval.model.DocumentCategory;
import com.enterprise.approval.model.DocumentFolder;
import com.enterprise.approval.model.DocumentTag;
import com.enterprise.approval.repository.AppUserRepository;
import com.enterprise.approval.repository.DocumentCategoryRepository;
import com.enterprise.approval.repository.DocumentFolderRepository;
import com.enterprise.approval.repository.DocumentTagRepository;
import java.util.List;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {
  @Bean("demoUserDataSeeder")
  CommandLineRunner seedUsers(AppUserRepository users, PasswordEncoder passwordEncoder) {
    return args -> {
      Set<String> supportedRoles = Set.of("Employee", "General", "HR", "Manager", "CFO", "Admin");
      List<String[]> demoUsers = List.of(
        new String[] {"employee@enterprise.ai", "Employee User", "Employee", "Operations"},
        new String[] {"general@enterprise.ai", "General User", "General", "Operations"},
        new String[] {"hr@enterprise.ai", "HR User", "HR", "HR"},
        new String[] {"manager@enterprise.ai", "Manager User", "Manager", "Operations"},
        new String[] {"cfo@enterprise.ai", "CFO User", "CFO", "Finance"},
        new String[] {"admin@enterprise.ai", "Admin User", "Admin", "Platform"}
      );

      for (String[] demoUser : demoUsers) {
        AppUser user = users.findByEmail(demoUser[0]).orElseGet(AppUser::new);
        user.setEmail(demoUser[0]);
        user.setDisplayName(demoUser[1]);
        user.setRoleName(demoUser[2]);
        user.setDepartment(demoUser[3]);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode("enterprise-ai"));
        users.save(user);
      }

      users.findAll().stream()
        .filter(user -> !supportedRoles.contains(user.getRoleName()))
        .forEach(user -> {
          user.setActive(false);
          users.save(user);
        });
    };
  }

  @Bean("documentLibraryDataSeeder")
  CommandLineRunner seedDocumentLibrary(
    DocumentCategoryRepository categories,
    DocumentFolderRepository folders,
    DocumentTagRepository tags
  ) {
    return args -> {
      List<String[]> categorySeeds = List.of(
        new String[] {"Invoice", "Bills, vendor invoices, and payment requests"},
        new String[] {"Contract", "Contracts, agreements, and legal documents"},
        new String[] {"Purchase Order", "Purchase orders and procurement requests"},
        new String[] {"Leave Request", "Employee leave and HR requests"},
        new String[] {"Financial Report", "Reports, statements, and financial summaries"},
        new String[] {"General Document", "Documents that do not fit a specialized category"}
      );
      for (String[] seed : categorySeeds) {
        categories.findByNameIgnoreCase(seed[0]).orElseGet(() -> {
          DocumentCategory category = new DocumentCategory();
          category.setName(seed[0]);
          category.setDescription(seed[1]);
          return categories.save(category);
        });
      }

      DocumentFolder finance = folder(folders, "Finance", null);
      folder(folders, "Invoices", finance);
      folder(folders, "Reports", finance);
      DocumentFolder operations = folder(folders, "Operations", null);
      folder(folders, "Procurement", operations);
      folder(folders, "Contracts", operations);
      DocumentFolder hr = folder(folders, "HR", null);
      folder(folders, "Leave Requests", hr);

      for (String tagName : List.of("urgent", "vendor", "compliance", "high-value", "internal", "external")) {
        tags.findByNameIgnoreCase(tagName).orElseGet(() -> {
          DocumentTag tag = new DocumentTag();
          tag.setName(tagName);
          return tags.save(tag);
        });
      }
    };
  }

  private DocumentFolder folder(DocumentFolderRepository folders, String name, DocumentFolder parent) {
    return folders.findAll().stream()
      .filter(folder -> folder.getName().equalsIgnoreCase(name))
      .filter(folder -> {
        Long parentId = parent == null ? null : parent.getId();
        Long currentParentId = folder.getParent() == null ? null : folder.getParent().getId();
        return java.util.Objects.equals(parentId, currentParentId);
      })
      .findFirst()
      .orElseGet(() -> {
        DocumentFolder folder = new DocumentFolder();
        folder.setName(name);
        folder.setParent(parent);
        return folders.save(folder);
      });
  }
}
