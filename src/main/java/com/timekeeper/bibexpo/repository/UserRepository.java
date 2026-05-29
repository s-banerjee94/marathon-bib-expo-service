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

    List<User> findByRole(UserRole role);

    List<User> findByOrganizationId(Long organizationId);

    List<User> findByRoleAndOrganizationId(UserRole role, Long organizationId);

    List<User> findByOrganizationIdAndEnabledTrue(Long organizationId);

    /**
     * Count users by organization and role.
     * Used for enforcing organization user limits.
     *
     * @param organizationId the organization ID
     * @param role the user role
     * @return count of users with the specified role in the organization
     */
    long countByOrganizationIdAndRole(Long organizationId, UserRole role);

    /**
     * Count active (enabled) users of a given role within an organization.
     * Used to enforce organization user and distributor limits on update.
     *
     * @param organizationId the organization ID
     * @param role the user role to count
     * @return count of enabled users with the specified role in the organization
     */
    long countByOrganizationIdAndRoleAndEnabledTrue(Long organizationId, UserRole role);

    @Query("SELECT u.role, COUNT(u) FROM User u WHERE u.organization.id = :orgId GROUP BY u.role")
    List<Object[]> countGroupByRoleForOrg(@Param("orgId") Long orgId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization WHERE u.username = :username")
    Optional<User> findByUsernameWithOrganization(@Param("username") String username);

    // --- Statistics count queries ---

    long countByEnabledTrue();

    long countByEnabledFalse();

    long countByRole(UserRole role);

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndEnabledTrue(Long organizationId);

    long countByOrganizationIdAndEnabledFalse(Long organizationId);
}
