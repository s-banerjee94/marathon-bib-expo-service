package com.timekeeper.bibexpo.model.entity;

import com.timekeeper.bibexpo.config.EmptyStringToNullConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users_archive",
        indexes = {
                @Index(name = "idx_user_archive_username", columnList = "username"),
                @Index(name = "idx_user_archive_email", columnList = "email"),
                @Index(name = "idx_user_archive_phone", columnList = "phoneNumber"),
                @Index(name = "idx_user_archive_org", columnList = "organization_id"),
                @Index(name = "idx_user_archive_role", columnList = "role")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserArchive {

    @Id
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String email;

    private String fullName;

    @Convert(converter = EmptyStringToNullConverter.class)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", foreignKey = @ForeignKey(name = "fk_user_archive_organization"))
    private Organization organization;

    @Column(nullable = false)
    private Boolean accountNonLocked;

    @Column(nullable = false)
    private Boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;

    private String createdBy;

    private String lastModifiedBy;

    @Column(nullable = false)
    private Instant archivedAt;

    @Column(nullable = false)
    private String archivedBy;
}
