package com.timekeeper.bibexpo.organization.repository;

import com.timekeeper.bibexpo.organization.model.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

    boolean existsByEmail(String email);

    boolean existsByTaxId(String taxId);

    boolean existsByOrganizerName(String organizerName);

    boolean existsByPhoneNumber(String phoneNumber);

}
