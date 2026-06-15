package com.timekeeper.bibexpo.messaging.system.model.entity;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
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

/**
 * What a system message says, per purpose × channel (e.g. INVITE over SMS). Holds the body and/or
 * ordered variables plus the registered template and sender ids that delivery requires — the DLT
 * template id is mandatory per message-type for SMS. Root-managed; the connection details live
 * separately on the provider row.
 */
@Entity
@Table(name = "system_message_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_template_purpose_channel", columnNames = {"purpose", "channel"})
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemMessageTemplate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SystemTemplatePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageChannel channel;

    // Message text with #{...} placeholders, for client-rendered channels (SMS)
    @Column(columnDefinition = "TEXT")
    private String body;

    // Newline-separated #{...} expressions mapped to positional {{1}}..{{n}}, for provider-rendered channels (WhatsApp)
    @Column(columnDefinition = "TEXT")
    private String variables;

    // Registered template id: DLT template id (SMS) or provider Content SID (WhatsApp)
    @Column(name = "dlt_template_id", length = 64)
    private String dltTemplateId;

    // Registered DLT header / sender id
    @Column(name = "sender_id", length = 32)
    private String senderId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

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
