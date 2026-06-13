package com.timekeeper.bibexpo.messaging.provider.model.entity;

import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.provider.converter.ProviderParamListConverter;
import com.timekeeper.bibexpo.messaging.provider.converter.SecretAttributeConverter;
import com.timekeeper.bibexpo.messaging.provider.model.ProviderParam;
import com.timekeeper.bibexpo.messaging.provider.model.enums.AuthType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.HttpMethodType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.MessageContentType;
import com.timekeeper.bibexpo.messaging.provider.model.enums.TemplateMode;
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
import java.util.List;

/**
 * How the system talks to one outbound provider (one row per channel — SMS, WhatsApp). Holds the
 * endpoint, the auth pair, the message-rendering mode, and a JSON request-parameter mapping that
 * lets a new provider be wired in by editing data rather than code. Root-managed.
 *
 * <p>Deliberately independent of the participant campaign SMS/WhatsApp code — no shared logic.
 */
@Entity
@Table(name = "messaging_providers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_messaging_provider_channel", columnNames = "channel")
        })
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessagingProvider implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, unique = true)
    private MessageChannel channel;

    @Column(name = "base_url", length = 512)
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false, length = 8)
    @Builder.Default
    private HttpMethodType httpMethod = HttpMethodType.POST;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    @Builder.Default
    private AuthType authType = AuthType.TOKEN;

    // Encrypted at rest; used when authType = TOKEN
    @Column(name = "auth_token", columnDefinition = "TEXT")
    @Convert(converter = SecretAttributeConverter.class)
    private String authToken;

    // Used when authType = USERNAME_PASSWORD
    @Column(length = 128)
    private String username;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = SecretAttributeConverter.class)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_mode", nullable = false, length = 20)
    @Builder.Default
    private TemplateMode templateMode = TemplateMode.CLIENT_RENDERED;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 8)
    @Builder.Default
    private MessageContentType contentType = MessageContentType.JSON;

    // Headers and query string. Each value may contain {{TOKEN}} placeholders resolved at send time.
    @Column(name = "request_params", columnDefinition = "TEXT")
    @Convert(converter = ProviderParamListConverter.class)
    private List<ProviderParam> requestParams;

    // POST body template; {{TOKEN}} placeholders are substituted and escaped per contentType.
    @Column(name = "body_template", columnDefinition = "TEXT")
    private String bodyTemplate;

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
