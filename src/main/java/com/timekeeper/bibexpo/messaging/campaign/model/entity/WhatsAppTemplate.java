package com.timekeeper.bibexpo.messaging.campaign.model.entity;

import com.timekeeper.bibexpo.config.EmptyStringToNullConverter;
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
@Table(name = "whatsapp_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_whatsapp_template_sid_event", columnNames = {"content_sid", "event_id"})
        },
        indexes = {
                @Index(name = "idx_whatsapp_template_event", columnList = "event_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppTemplate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "content_sid", nullable = false, length = 64)
    private String contentSid;

    // Approved Twilio body text with positional {{n}} markers; stored locally so the message is readable without a Twilio call
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // Ordered placeholder expressions, newline-joined; entry n fills Twilio template variable {{n}}
    @Column(columnDefinition = "TEXT")
    private String bodyVariables;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EmptyStringToNullConverter.class)
    private String note;

    // Plain IDs, no FK — the whatsapp slice is decoupled from core tables (billing pattern)
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

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
