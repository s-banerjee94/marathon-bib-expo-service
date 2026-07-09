package com.timekeeper.bibexpo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Per-event resource limits. One row per event, shares its primary key with {@link Event}.
 * Limits are enforced via live count queries at write time — no usage counters are maintained here.
 */
@Entity
@Table(name = "event_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLimit implements Serializable {

    @Id
    private Long eventId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "max_participants", nullable = false)
    @Builder.Default
    private Integer maxParticipants = 50_000;

    @Column(name = "max_races", nullable = false)
    @Builder.Default
    private Integer maxRaces = 20;

    @Column(name = "max_categories_per_race", nullable = false)
    @Builder.Default
    private Integer maxCategoriesPerRace = 50;

    @Column(name = "max_goodies", nullable = false)
    @Builder.Default
    private Integer maxGoodies = 15;

    @Column(name = "max_sms_templates", nullable = false)
    @Builder.Default
    private Integer maxSmsTemplates = 20;

    @Column(name = "max_sms_campaigns", nullable = false)
    @Builder.Default
    private Integer maxSmsCampaigns = 20;

    @Column(name = "max_imports", nullable = false)
    @Builder.Default
    private Integer maxImports = 10;

    @Column(name = "max_add_ons", nullable = false)
    @Builder.Default
    private Integer maxAddOns = 5;
}
