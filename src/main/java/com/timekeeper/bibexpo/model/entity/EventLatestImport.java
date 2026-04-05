package com.timekeeper.bibexpo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_latest_import")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventLatestImport {

    @Id
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "import_id", nullable = false, length = 36)
    private String importId;
}
