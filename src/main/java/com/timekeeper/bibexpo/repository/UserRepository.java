package com.timekeeper.bibexpo.repository;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndRole(String username, UserRole role);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u.role FROM User u WHERE u.id = :id")
    Optional<UserRole> findRoleById(@Param("id") Long id);

    // Query methods for getAllUsers filtering
    List<User> findByRole(UserRole role);

    List<User> findByOrganizationId(Long organizationId);

    List<User> findByRoleAndOrganizationId(UserRole role, Long organizationId);

    // Query method for cascade deactivation
    List<User> findByOrganizationIdAndEnabledTrue(Long organizationId);

    // Soft delete query methods - exclude deleted users
    Optional<User> findByUsernameAndDeletedFalse(String username);

    boolean existsByUsernameAndDeletedFalse(String username);

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmailAndDeletedFalse(String email);

    List<User> findByRoleAndDeletedFalse(UserRole role);

    List<User> findByOrganizationIdAndDeletedFalse(Long organizationId);

    List<User> findByRoleAndOrganizationIdAndDeletedFalse(UserRole role, Long organizationId);

    List<User> findByOrganizationIdAndEnabledTrueAndDeletedFalse(Long organizationId);

    // Queries for deleted users
    List<User> findByDeletedTrue();

    List<User> findByRoleAndDeletedTrue(UserRole role);

    List<User> findByOrganizationIdAndDeletedTrue(Long organizationId);

    List<User> findByRoleAndOrganizationIdAndDeletedTrue(UserRole role, Long organizationId);
}
