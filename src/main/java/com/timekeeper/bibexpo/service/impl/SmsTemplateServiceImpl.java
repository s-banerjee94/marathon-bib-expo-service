package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.SmsTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.exception.SmsTemplateNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.SmsTemplateRepository;
import com.timekeeper.bibexpo.service.SmsTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsTemplateServiceImpl implements SmsTemplateService {

    private final SmsTemplateRepository smsTemplateRepository;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public SmsTemplateResponse createSmsTemplate(Long eventId, CreateSmsTemplateRequest request, User currentUser) {
        log.info("Creating SMS template for event ID: {} by user: {}", eventId, currentUser.getUsername());

        Event event = validateEventAccess(eventId, currentUser);

        if (smsTemplateRepository.existsBySmsTemplateIdAndEventId(request.getSmsTemplateId(), eventId)) {
            throw new SmsTemplateAlreadyExistsException(
                    "SMS template with ID '" + request.getSmsTemplateId() + "' already exists for this event");
        }

        SmsTemplate smsTemplate = SmsTemplate.builder()
                .name(request.getName().toLowerCase().trim())
                .smsTemplateId(request.getSmsTemplateId())
                .template(request.getTemplate())
                .note(request.getNote())
                .scheduledDateTime(request.getScheduledDateTime())
                .enabled(true)
                .event(event)
                .build();

        SmsTemplate savedTemplate = smsTemplateRepository.save(smsTemplate);
        log.info("Successfully created SMS template with ID: {} for event ID: {} by user: {}",
                savedTemplate.getId(), eventId, currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(savedTemplate);
    }

    @Override
    @Transactional
    public SmsTemplateResponse updateSmsTemplate(Long eventId, Long templateId, UpdateSmsTemplateRequest request, User currentUser) {
        log.info("Updating SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = smsTemplateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(() -> new SmsTemplateNotFoundException(
                        "SMS template not found with ID: " + templateId + " for event: " + eventId));

        if (request.getName() != null && !request.getName().isBlank()) {
            smsTemplate.setName(request.getName().toLowerCase().trim());
        }

        if (request.getSmsTemplateId() != null && !request.getSmsTemplateId().isBlank() &&
                !request.getSmsTemplateId().equals(smsTemplate.getSmsTemplateId())) {
            if (smsTemplateRepository.existsBySmsTemplateIdAndEventIdAndIdNot(request.getSmsTemplateId(), eventId, templateId)) {
                throw new SmsTemplateAlreadyExistsException(
                        "SMS template with ID '" + request.getSmsTemplateId() + "' already exists for this event");
            }
            smsTemplate.setSmsTemplateId(request.getSmsTemplateId());
        }

        if (request.getTemplate() != null && !request.getTemplate().isBlank()) {
            smsTemplate.setTemplate(request.getTemplate());
        }

        if (request.getNote() != null) {
            smsTemplate.setNote(request.getNote());
        }

        if (request.getScheduledDateTime() != null) {
            smsTemplate.setScheduledDateTime(request.getScheduledDateTime());
        }

        SmsTemplate updatedTemplate = smsTemplateRepository.save(smsTemplate);
        log.info("Successfully updated SMS template ID: {} for event ID: {} by user: {}",
                updatedTemplate.getId(), eventId, currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(updatedTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SmsTemplateResponse> getSmsTemplatesByEvent(Long eventId, String search, Boolean enabled,
                                                            LocalDate fromDate, LocalDate toDate,
                                                            Pageable pageable, User currentUser) {
        log.info("Fetching SMS templates for event ID: {} search: {} enabled: {} fromDate: {} toDate: {} by user: {}",
                eventId, search, enabled, fromDate, toDate, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        Specification<SmsTemplate> spec = buildSmsTemplateSpecification(eventId, search, enabled, fromDate, toDate);
        Page<SmsTemplate> templates = smsTemplateRepository.findAll(spec, pageable);

        log.info("Successfully fetched {} SMS templates for event ID: {} by user: {}",
                templates.getNumberOfElements(), eventId, currentUser.getUsername());

        return templates.map(SmsTemplateResponse::fromEntity);
    }

    private Specification<SmsTemplate> buildSmsTemplateSpecification(Long eventId, String search,
                                                                      Boolean enabled,
                                                                      LocalDate fromDate, LocalDate toDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("event").get("id"), eventId));

            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), pattern),
                        cb.like(root.get("smsTemplateId"), "%" + search + "%")
                ));
            }

            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDateTime"), fromDate.atStartOfDay()));
            }

            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDateTime"), toDate.atTime(23, 59, 59)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public SmsTemplateResponse getSmsTemplateById(Long eventId, Long templateId, User currentUser) {
        log.info("Fetching SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = smsTemplateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(() -> new SmsTemplateNotFoundException(
                        "SMS template not found with ID: " + templateId + " for event: " + eventId));

        log.info("Successfully fetched SMS template ID: {} by user: {}",
                smsTemplate.getId(), currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(smsTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public SmsTemplateResponse getSmsTemplateBySmsTemplateId(Long eventId, String smsTemplateId, User currentUser) {
        log.info("Fetching SMS template by DLT ID: {} for event ID: {} by user: {}",
                smsTemplateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = smsTemplateRepository.findBySmsTemplateIdAndEventId(smsTemplateId, eventId)
                .orElseThrow(() -> new SmsTemplateNotFoundException(
                        "SMS template not found with DLT ID: " + smsTemplateId + " for event: " + eventId));

        log.info("Successfully fetched SMS template by DLT ID: {} by user: {}",
                smsTemplateId, currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(smsTemplate);
    }

    @Override
    @Transactional
    public SmsTemplateResponse toggleSmsTemplateEnabled(Long eventId, Long templateId, User currentUser) {
        log.info("Toggling SMS template ID: {} enabled status for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = smsTemplateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(() -> new SmsTemplateNotFoundException(
                        "SMS template not found with ID: " + templateId + " for event: " + eventId));

        smsTemplate.setEnabled(!smsTemplate.getEnabled());
        SmsTemplate updatedTemplate = smsTemplateRepository.save(smsTemplate);

        log.info("Successfully toggled SMS template ID: {} enabled status to {} by user: {}",
                updatedTemplate.getId(), updatedTemplate.getEnabled(), currentUser.getUsername());

        return SmsTemplateResponse.fromEntity(updatedTemplate);
    }

    @Override
    @Transactional
    public void deleteSmsTemplate(Long eventId, Long templateId, User currentUser) {
        log.info("Deleting SMS template ID: {} for event ID: {} by user: {}",
                templateId, eventId, currentUser.getUsername());

        validateEventAccess(eventId, currentUser);

        SmsTemplate smsTemplate = smsTemplateRepository.findByIdAndEventId(templateId, eventId)
                .orElseThrow(() -> new SmsTemplateNotFoundException(
                        "SMS template not found with ID: " + templateId + " for event: " + eventId));

        smsTemplateRepository.delete(smsTemplate);
        log.info("Successfully deleted SMS template ID: {} by user: {}", templateId, currentUser.getUsername());
    }

    private Event validateEventAccess(Long eventId, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + eventId));

        validateUserAuthorizationForEvent(currentUser, event);

        return event;
    }

    private void validateUserAuthorizationForEvent(User currentUser, Event event) {
        UserRole role = currentUser.getRole();

        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }

        if (role == UserRole.ORGANIZER_ADMIN || role == UserRole.ORGANIZER_USER) {
            if (currentUser.getOrganization() == null) {
                throw new UnauthorizedAccessException("User does not belong to any organization");
            }

            if (!event.getOrganization().getId().equals(currentUser.getOrganization().getId())) {
                throw new UnauthorizedAccessException(
                        "User can only access SMS templates from their own organization's events");
            }
            return;
        }

        throw new UnauthorizedAccessException("User does not have permission to access SMS templates");
    }
}
