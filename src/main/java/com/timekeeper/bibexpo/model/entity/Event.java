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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_event_name_org", columnNames = {"event_name", "organization_id"})
        },
        indexes = {
                @Index(name = "idx_event_name", columnList = "event_name"),
                @Index(name = "idx_event_status", columnList = "status"),
                @Index(name = "idx_event_enabled", columnList = "enabled"),
                @Index(name = "idx_event_start_date", columnList = "event_start_date"),
                @Index(name = "idx_event_organization", columnList = "organization_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventName;

    @Column(columnDefinition = "TEXT")
    private String eventDescription;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String logoUrl;

    @Column(length = 50)
    private String timezone;

    @Column(nullable = false)
    private LocalDateTime eventStartDate;

    @Column(nullable = false)
    private LocalDateTime eventEndDate;
    
    @Column(nullable = false)
    private String venueName;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String addressLine1;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String addressLine2;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String city;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String stateProvince;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String postalCode;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String country;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false, foreignKey = @ForeignKey(name = "fk_event_organization"))
    private Organization organization;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Race> races = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @Builder.Default
    private List<SmsTemplate> smsTemplates = new ArrayList<>();

    @Column(columnDefinition = "JSON")
    private String eventGoodies;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

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
