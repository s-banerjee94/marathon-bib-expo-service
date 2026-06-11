package com.timekeeper.bibexpo.whatsapp.model.entity;

import com.timekeeper.bibexpo.whatsapp.config.EncryptedStringConverter;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "organization_whatsapp_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_whatsapp_config_org", columnNames = "organization_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationWhatsAppConfig implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Plain ID, no FK — the whatsapp slice is decoupled from core tables (billing pattern)
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(nullable = false, length = 64)
    private String accountSid;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String authToken;

    @Column(nullable = false, length = 32)
    private String fromNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WhatsAppSenderMode senderMode = WhatsAppSenderMode.CUSTOM;

    @Column(nullable = false)
    private boolean verified;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;
}
