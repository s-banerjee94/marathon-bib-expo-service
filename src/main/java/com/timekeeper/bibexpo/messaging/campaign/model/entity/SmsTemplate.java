package com.timekeeper.bibexpo.messaging.campaign.model.entity;
import com.timekeeper.bibexpo.model.entity.Event;

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
@Table(name = "sms_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sms_template_id_event", columnNames = {"sms_template_id", "event_id"})
        },
        indexes = {
                @Index(name = "idx_sms_template_id", columnList = "sms_template_id"),
                @Index(name = "idx_sms_event", columnList = "event_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsTemplate implements TemplateEntity, Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String smsTemplateId;

    // Registered DLT header / sender id
    @Column(name = "sender_id", length = 32)
    private String senderId;

    // Full message text with #{...} placeholders — for client-rendered providers (sent as {{MESSAGE}})
    @Column(columnDefinition = "TEXT")
    private String template;

    // Ordered #{...} expressions, newline-joined — for provider-rendered providers (sent as {{VAR:n}}/{{VARIABLES_JSON}})
    @Column(name = "body_variables", columnDefinition = "TEXT")
    private String bodyVariables;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EmptyStringToNullConverter.class)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sms_template_event"))
    private Event event;

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
