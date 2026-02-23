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

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

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

    Optional<User> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    boolean existsByPhoneNumberAndDeletedFalse(String phoneNumber);

    List<User> findByRoleAndDeletedFalse(UserRole role);

    List<User> findByOrganizationIdAndDeletedFalse(Long organizationId);

    List<User> findByRoleAndOrganizationIdAndDeletedFalse(UserRole role, Long organizationId);

    List<User> findByOrganizationIdAndEnabledTrueAndDeletedFalse(Long organizationId);

    /**
     * Count users by organization, role, and excluding deleted users.
     * Used for enforcing organization user limits.
     *
     * @param organizationId the organization ID
     * @param role the user role
     * @return count of non-deleted users with the specified role in the organization
     */
    long countByOrganizationIdAndRoleAndDeletedFalse(Long organizationId, UserRole role);

    // Queries for deleted users
    List<User> findByDeletedTrue();

    List<User> findByRoleAndDeletedTrue(UserRole role);

    List<User> findByOrganizationIdAndDeletedTrue(Long organizationId);

    List<User> findByRoleAndOrganizationIdAndDeletedTrue(UserRole role, Long organizationId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization WHERE u.username = :username AND u.deleted = false")
    Optional<User> findByUsernameWithOrganization(@Param("username") String username);

    // --- Statistics count queries ---

    long countByDeletedFalse();

    long countByEnabledTrueAndDeletedFalse();

    long countByEnabledFalseAndDeletedFalse();

    long countByRoleAndDeletedFalse(UserRole role);

    long countByOrganizationIdAndDeletedFalse(Long organizationId);

    long countByOrganizationIdAndEnabledTrueAndDeletedFalse(Long organizationId);

    long countByOrganizationIdAndEnabledFalseAndDeletedFalse(Long organizationId);
}
