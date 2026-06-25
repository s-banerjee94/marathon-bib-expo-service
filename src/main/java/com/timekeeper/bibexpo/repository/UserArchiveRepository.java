package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.UserArchive;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserArchiveRepository
        extends JpaRepository<UserArchive, Long>, JpaSpecificationExecutor<UserArchive> {

    Optional<UserArchive> findByUsername(String username);

    List<UserArchive> findByRole(UserRole role);

    List<UserArchive> findByOrganizationId(Long organizationId);

    List<UserArchive> findByRoleAndOrganizationId(UserRole role, Long organizationId);

    @Modifying
    @Query("DELETE FROM UserArchive ua WHERE ua.organization.id = :organizationId")
    int deleteByOrganizationId(@Param("organizationId") Long organizationId);
}
