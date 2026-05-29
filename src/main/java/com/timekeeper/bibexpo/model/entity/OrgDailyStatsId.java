package com.timekeeper.bibexpo.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrgDailyStatsId implements Serializable {
    private Long organizationId;
    private LocalDate snapshotDate;
}
