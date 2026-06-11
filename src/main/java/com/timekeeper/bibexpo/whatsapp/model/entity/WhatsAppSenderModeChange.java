package com.timekeeper.bibexpo.whatsapp.model.entity;

import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Append-only audit of an organization switching between the default and its own
 * WhatsApp sender. Written on every config change; read endpoint comes with the
 * future analytics phase.
 */
@Entity
@Table(name = "whatsapp_sender_mode_changes",
        indexes = {
                @Index(name = "idx_whatsapp_mode_change_org", columnList = "organization_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppSenderModeChange implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain ID, no FK — the whatsapp slice is decoupled from core tables (billing pattern)
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppSenderMode fromMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WhatsAppSenderMode toMode;

    @CreatedBy
    @Column(updatable = false)
    private String changedBy;

    @CreatedDate
    @Column(updatable = false)
    private Instant changedAt;
}
