package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    Optional<Organization> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Organization> findByTaxId(String taxId);

    boolean existsByTaxId(String taxId);

    // Soft delete query methods - exclude deleted organizations
    Optional<Organization> findByIdAndDeletedFalse(Long id);

    Optional<Organization> findByEmailAndDeletedFalse(String email);

    boolean existsByEmailAndDeletedFalse(String email);

    Optional<Organization> findByTaxIdAndDeletedFalse(String taxId);

    boolean existsByTaxIdAndDeletedFalse(String taxId);

    List<Organization> findByDeletedFalse();

    List<Organization> findByEnabledTrueAndDeletedFalse();

    // Queries for deleted organizations
    List<Organization> findByDeletedTrue();
}
