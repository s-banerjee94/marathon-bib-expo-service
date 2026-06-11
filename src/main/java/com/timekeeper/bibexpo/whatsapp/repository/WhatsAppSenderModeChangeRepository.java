package com.timekeeper.bibexpo.whatsapp.repository;

import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppSenderModeChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WhatsAppSenderModeChangeRepository extends JpaRepository<WhatsAppSenderModeChange, Long> {
}
