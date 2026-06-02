package com.timekeeper.bibexpo.model.entity;

import com.timekeeper.bibexpo.model.enums.ImportMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "import_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJob {

    @Id
    @Column(name = "import_id", nullable = false, length = 36)
    private String importId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    // Spring Batch job execution id. Null until asyncJobLauncher.run() returns and we patch it onto
    // the placeholder row. The sweeper / stop endpoint use this to target the underlying batch execution.
    @Column(name = "job_execution_id")
    private Long jobExecutionId;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @Column(name = "success_count", nullable = false)
    private Integer successCount;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "goodies_detected", columnDefinition = "TEXT")
    private String goodiesDetected;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status;

    // Nullable so DDL-update adds the column cleanly to existing rows; a null mode is treated as IMPORT.
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20)
    private ImportMode mode;

    @Column(name = "imported_by", nullable = false)
    private Long importedBy;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    public enum ImportStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
