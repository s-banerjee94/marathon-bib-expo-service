package com.timekeeper.bibexpo.whatsapp.repository;

import com.timekeeper.bibexpo.whatsapp.model.entity.OrganizationWhatsAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationWhatsAppConfigRepository extends JpaRepository<OrganizationWhatsAppConfig, Long> {

    Optional<OrganizationWhatsAppConfig> findByOrganizationId(Long organizationId);
}
