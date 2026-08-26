package com.timekeeper.bibexpo.user.repository;

import com.timekeeper.bibexpo.shared.security.UserRole;
import com.timekeeper.bibexpo.user.model.entity.User;
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

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByRole(UserRole role);

    List<User> findByOrganizationId(Long organizationId);

    List<User> findByRoleAndOrganizationId(UserRole role, Long organizationId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization LEFT JOIN FETCH u.event WHERE u.username = :username")
    Optional<User> findByUsernameWithOrganizationAndEvent(@Param("username") String username);

}
