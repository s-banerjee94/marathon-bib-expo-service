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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "races",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_race_name_event", columnNames = {"race_name", "event_id"})
        },
        indexes = {
                @Index(name = "idx_race_name", columnList = "race_name"),
                @Index(name = "idx_race_enabled", columnList = "enabled"),
                @Index(name = "idx_race_deleted", columnList = "deleted"),
                @Index(name = "idx_race_event", columnList = "event_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Race implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String raceName;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = EmptyStringToNullConverter.class)
    private String raceDescription;

    private Double distanceKm;

    private LocalTime startTime;

    private LocalTime cutOffTime;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_race_event"))
    private Event event;

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Category> categories = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

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
