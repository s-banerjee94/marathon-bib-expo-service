package com.timekeeper.bibexpo.model.entity;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "sms_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sms_template_id_event", columnNames = {"sms_template_id", "event_id"})
        },
        indexes = {
                @Index(name = "idx_sms_template_id", columnList = "sms_template_id"),
                @Index(name = "idx_sms_event", columnList = "event_id"),
                @Index(name = "idx_sms_enabled", columnList = "enabled")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsTemplate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String smsTemplateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String template;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EmptyStringToNullConverter.class)
    private String note;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sms_template_event"))
    private Event event;

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
