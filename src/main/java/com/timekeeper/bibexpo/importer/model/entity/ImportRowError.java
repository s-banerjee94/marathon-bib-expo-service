package com.timekeeper.bibexpo.importer.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Entity
@Table(name = "import_errors", indexes = {
        @Index(name = "idx_import_errors_import", columnList = "import_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The owning import. Declared as a real association with ON DELETE CASCADE so an error row
     * cannot outlive its job: previously errors lived in DynamoDB keyed by the Spring Batch
     * execution id, which repeats whenever the MySQL batch tables are reset, and stale rows then
     * surfaced under a later import.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_import_errors_job"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ImportJob importJob;

    /** Physical CSV line, as seen in a spreadsheet. Not {@code row_number} — that is reserved in MySQL 8. */
    @Column(name = "line_number")
    private Integer rowNumber;

    @Column(name = "error_type", length = 32)
    private String errorType;

    @Column(name = "field", length = 64)
    private String field;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}