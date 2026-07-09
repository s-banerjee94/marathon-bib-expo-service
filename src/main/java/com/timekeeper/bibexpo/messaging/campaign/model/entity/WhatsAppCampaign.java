package com.timekeeper.bibexpo.messaging.campaign.model.entity;

import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignStatus;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTargetFilter;
import com.timekeeper.bibexpo.messaging.campaign.model.enums.CampaignTriggerType;
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

import java.time.Instant;

@Entity
@Table(name = "whatsapp_campaigns",
        indexes = {
                @Index(name = "idx_whatsapp_campaign_event", columnList = "event_id"),
                @Index(name = "idx_whatsapp_campaign_trigger_type", columnList = "trigger_type"),
                @Index(name = "idx_whatsapp_campaign_status", columnList = "status"),
                @Index(name = "idx_whatsapp_campaign_scheduled_at", columnList = "scheduled_at")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // Plain IDs, no FK — the whatsapp slice is decoupled from core tables (billing pattern)
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // Slice-internal relation — stays a real FK
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "whatsapp_template_id", nullable = false, foreignKey = @ForeignKey(name = "fk_whatsapp_campaign_template"))
    private WhatsAppTemplate whatsAppTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 30)
    private CampaignTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_filter", length = 30)
    private CampaignTargetFilter targetFilter;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

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
