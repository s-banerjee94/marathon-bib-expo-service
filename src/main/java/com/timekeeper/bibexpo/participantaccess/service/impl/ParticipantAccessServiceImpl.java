package com.timekeeper.bibexpo.participantaccess.service.impl;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.InvalidQrCodeException;
import com.timekeeper.bibexpo.model.dto.audit.AuditEvent;
import com.timekeeper.bibexpo.notification.model.dto.NotifyRequest;
import com.timekeeper.bibexpo.model.dto.response.ParticipantDistributionResponse;
import com.timekeeper.bibexpo.notification.model.enums.NotificationAudience;
import com.timekeeper.bibexpo.notification.model.enums.NotificationType;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.enums.AuditAction;
import com.timekeeper.bibexpo.model.enums.AuditEntityType;
import com.timekeeper.bibexpo.service.audit.AuditPublisher;
import com.timekeeper.bibexpo.participantaccess.model.dto.response.ParticipantVerificationResponse;
import com.timekeeper.bibexpo.participantaccess.model.dynamodb.ShortUrlDDB;
import com.timekeeper.bibexpo.participantaccess.repository.ShortUrlDDBRepository;
import com.timekeeper.bibexpo.participantaccess.service.ParticipantAccessService;
import com.timekeeper.bibexpo.participantaccess.util.QrImageGenerator;
import com.timekeeper.bibexpo.participantaccess.util.QrTokenCodec;
import com.timekeeper.bibexpo.participantaccess.util.ShortCodeGenerator;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.dynamodb.ParticipantDDBRepository;
import com.timekeeper.bibexpo.service.EventService;
import com.timekeeper.bibexpo.notification.service.NotificationService;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver;
import com.timekeeper.bibexpo.service.util.RaceCategoryNameResolver.EventNames;
import com.timekeeper.bibexpo.service.validator.EventAccessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParticipantAccessServiceImpl implements ParticipantAccessService {

    private final EventRepository eventRepository;
    private final EventService eventService;
    private final EventAccessValidator eventAccessValidator;
    private final ParticipantDDBRepository participantRepository;
    private final ShortUrlDDBRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final QrTokenCodec qrTokenCodec;
    private final QrImageGenerator qrImageGenerator;
    private final NotificationService notificationService;
    private final AuditPublisher auditPublisher;
    private final RaceCategoryNameResolver nameResolver;

    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final int SHORT_URL_TTL_DAYS_AFTER_EVENT_END = 3;

    @Override
    @Async("participantAccessTaskExecutor")
    public void generateShortUrls(Long eventId, User currentUser) {
        Event event = findEventOrThrow(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        int total = 0;
        int generated = 0;
        int skipped = 0;
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        Long expiresAt = resolveExpiry(event);

        for (Page<ParticipantDDB> page : participantRepository.findPagesByEventId(eventId, 100)) {
            for (ParticipantDDB participant : page.items()) {
                total++;
                String existingCode = participant.getVerifyShortCode();
                // A stored code can outlive its short URL row, which TTL removes after the event ends; regenerate when the row is gone.
                if (existingCode != null && !existingCode.isBlank()
                        && shortUrlRepository.findByCode(existingCode) != null) {
                    skipped++;
                    continue;
                }
                String code = allocateShortCode(eventId, participant.getBibNumber(), now, expiresAt);
                participantRepository.updateVerifyShortCode(eventId, participant.getBibNumber(), code);
                generated++;
            }
        }

        log.info("Short URL generation for event {}: total={}, generated={}, skipped={}", eventId, total, generated, skipped);

        publishGenerationAudit(event, currentUser);

        notificationService.notify(NotifyRequest.builder()
                .audience(NotificationAudience.USER)
                .targetUserId(currentUser.getId())
                .type(NotificationType.SHORT_URLS_COMPLETED)
                .title("Verification Links Ready")
                .message(String.format("Verification links generated for \"%s\": %d new, %d already had one (%d total).",
                        event.getEventName(), generated, skipped, total))
                .entityType("EVENT")
                .entityId(String.valueOf(event.getId()))
                .build());
    }

    /**
     * Published via a direct call rather than {@code @Auditable}: this method is {@code @Async}
     * and returns void, so the aspect would fire on dispatch (before the work succeeds) with no result.
     */
    private void publishGenerationAudit(Event event, User actor) {
        Long actorOrgId = actor.getOrganization() != null ? actor.getOrganization().getId() : 0L;
        Long orgId = event.getOrganization() != null ? event.getOrganization().getId() : actorOrgId;
        auditPublisher.publish(AuditEvent.builder()
                .organizationId(orgId)
                .actorUserId(actor.getId())
                .actorName(actor.getUsername())
                .action(AuditAction.GENERATE)
                .entityType(AuditEntityType.VERIFICATION_LINK)
                .entityId(String.valueOf(event.getId()))
                .entityLabel(event.getEventName())
                .description("Verification links generated for \"" + event.getEventName() + "\"")
                .occurredAt(Instant.now())
                .build());
    }

    private Long resolveExpiry(Event event) {
        if (event.getEventEndDate() == null) {
            return null;
        }
        long fromEventEnd = event.getEventEndDate().plus(SHORT_URL_TTL_DAYS_AFTER_EVENT_END, ChronoUnit.DAYS).getEpochSecond();
        long floor = Instant.now().plus(SHORT_URL_TTL_DAYS_AFTER_EVENT_END, ChronoUnit.DAYS).getEpochSecond();
        return Math.max(fromEventEnd, floor);
    }

    @Override
    public ParticipantVerificationResponse resolveShortUrl(String shortCode) {
        ShortUrlDDB shortUrl = shortUrlRepository.findByCodeOrThrow(shortCode);
        Long eventId = Long.parseLong(shortUrl.getEventId());
        Event event = findEventOrThrow(eventId);
        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, shortUrl.getBibNumber());
        String token = qrTokenCodec.encode(shortUrl.getEventId(), participant.getBibNumber());
        return toVerificationResponse(event, participant, qrImageGenerator.toCompactDataUri(token));
    }

    @Override
    public byte[] getParticipantQr(Long eventId, String bibNumber, User currentUser) {
        Event event = findEventOrThrow(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        participantRepository.findByEventAndBibOrThrow(eventId, bibNumber);

        String token = qrTokenCodec.encode(String.valueOf(eventId), bibNumber);
        return qrImageGenerator.toPng(token);
    }

    @Override
    public ParticipantDistributionResponse scanQr(Long eventId, String token, User currentUser) {
        Event event = findEventOrThrow(eventId);
        eventAccessValidator.validateUserAuthorizationForEvent(currentUser, event);
        eventService.validateEventEnabled(event, currentUser);

        String[] parts = qrTokenCodec.decode(token);
        if (!String.valueOf(eventId).equals(parts[0])) {
            throw new InvalidQrCodeException();
        }

        ParticipantDDB participant = participantRepository.findByEventAndBibOrThrow(eventId, parts[1]);
        return toDistributionResponse(participant);
    }

    private ParticipantDistributionResponse toDistributionResponse(ParticipantDDB p) {
        EventNames names = nameResolver.forEvent(Long.parseLong(p.getEventId()));
        return ParticipantDistributionResponse.from(p,
                names.raceName(p.getRaceId()), names.categoryName(p.getCategoryId()));
    }

    private ParticipantVerificationResponse toVerificationResponse(Event event, ParticipantDDB p, String qrCodeDataUri) {
        EventNames names = nameResolver.forEvent(event.getId());
        return ParticipantVerificationResponse.builder()
                .eventName(event.getEventName())
                .eventVanue(event.getVenueName())
                .eventTimezone(event.getTimezone())
                .eventStartDate(event.getEventStartDate())
                .eventEndDate(event.getEventEndDate())
                .bibNumber(p.getBibNumber())
                .chipNumber(p.getChipNumber())
                .fullName(p.getFullName())
                .email(p.getEmail())
                .phoneNumber(p.getPhoneNumber())
                .raceName(names.raceName(p.getRaceId()))
                .categoryName(names.categoryName(p.getCategoryId()))
                .age(p.getAge())
                .gender(p.getGender())
                .city(p.getCity())
                .country(p.getCountry())
                .emergencyContactName(p.getEmergencyContactName())
                .emergencyContactPhone(p.getEmergencyContactPhone())
                .bibCollectedAt(p.getBibCollectedAt())
                .bibCollectedByName(p.getBibCollectedByName())
                .bibCollectedByPhone(p.getBibCollectedByPhone())
                .goodiesDistribution(p.getGoodiesDistribution())
                .qrCodeDataUri(qrCodeDataUri)
                .build();
    }

    private String allocateShortCode(Long eventId, String bibNumber, String now, Long expiresAt) {
        String eventIdStr = String.valueOf(eventId);
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = shortCodeGenerator.generate(eventIdStr, bibNumber, attempt);
            ShortUrlDDB row = ShortUrlDDB.builder()
                    .shortCode(code)
                    .eventId(eventIdStr)
                    .bibNumber(bibNumber)
                    .createdAt(now)
                    .expirationTime(expiresAt)
                    .build();
            if (shortUrlRepository.saveIfAbsent(row)) {
                return code;
            }
            ShortUrlDDB existing = shortUrlRepository.findByCode(code);
            if (existing != null
                    && eventIdStr.equals(existing.getEventId())
                    && bibNumber.equals(existing.getBibNumber())) {
                row.setCreatedAt(existing.getCreatedAt());
                shortUrlRepository.save(row);
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique verification link, please try again.");
    }

    private Event findEventOrThrow(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(EventNotFoundException::new);
    }
}
