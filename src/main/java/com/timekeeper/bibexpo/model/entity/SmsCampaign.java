package com.timekeeper.bibexpo.model.entity;

import com.timekeeper.bibexpo.model.enums.SmsCampaignStatus;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTargetFilter;
import com.timekeeper.bibexpo.model.enums.SmsCampaignTriggerType;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "sms_campaigns",
        indexes = {
                @Index(name = "idx_sms_campaign_event", columnList = "event_id"),
                @Index(name = "idx_sms_campaign_trigger_type", columnList = "trigger_type"),
                @Index(name = "idx_sms_campaign_status", columnList = "status"),
                @Index(name = "idx_sms_campaign_scheduled_at", columnList = "scheduled_at")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sms_campaign_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sms_template_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sms_campaign_template"))
    private SmsTemplate smsTemplate;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private SmsCampaignTriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_filter", nullable = false, length = 30)
    private SmsCampaignTargetFilter targetFilter;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SmsCampaignStatus status = SmsCampaignStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Integer sentCount = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String lastModifiedBy;
}
