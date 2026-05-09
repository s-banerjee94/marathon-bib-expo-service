package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.exception.EventNotFoundException;
import com.timekeeper.bibexpo.exception.InvalidSmsTemplateException;
import com.timekeeper.bibexpo.exception.SmsTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.model.entity.Event;
import com.timekeeper.bibexpo.model.entity.Organization;
import com.timekeeper.bibexpo.model.entity.SmsTemplate;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.EventRepository;
import com.timekeeper.bibexpo.repository.SmsTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsTemplateServiceImplTest {

    @Mock
    private SmsTemplateRepository smsTemplateRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private SmsTemplateServiceImpl smsTemplateService;

    private Organization organization;
    private Event event;
    private User adminUser;
    private User organizerUser;
    private User otherOrgUser;

    @BeforeEach
    void setUp() {
        organization = Organization.builder().id(1L).organizerName("Test Org").build();

        Organization otherOrg = Organization.builder().id(2L).organizerName("Other Org").build();

        event = Event.builder().id(10L).eventName("Mumbai Marathon").organization(organization).build();

        adminUser = User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();

        organizerUser = User.builder().id(2L).username("organizer").role(UserRole.ORGANIZER_ADMIN)
                .organization(organization).build();

        otherOrgUser = User.builder().id(3L).username("other").role(UserRole.ORGANIZER_ADMIN)
                .organization(otherOrg).build();
    }

    private SmsTemplate buildSavedTemplate(Long id, String name, String template) {
        return SmsTemplate.builder()
                .id(id).name(name).smsTemplateId("12345678901234567890")
                .template(template).enabled(true).event(event).build();
    }

    @Test
    @DisplayName("Admin user creates SMS template successfully")
    void createSmsTemplate_adminUser_createsSuccessfully() {
        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Bib Ready").smsTemplateId("12345678901234567890")
                .template("Hi #{fullName}, your bib #{bibNumber} is ready.")
                .build();

        SmsTemplate saved = buildSavedTemplate(100L, "bib ready", "Hi #{fullName}, your bib #{bibNumber} is ready.");

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId("12345678901234567890", 10L)).thenReturn(false);
        when(smsTemplateRepository.save(any())).thenReturn(saved);

        SmsTemplateResponse response = smsTemplateService.createSmsTemplate(10L, request, adminUser);

        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    @Test
    @DisplayName("Organizer user creates SMS template for their own organization's event")
    void createSmsTemplate_organizerUser_ownOrg_createsSuccessfully() {
        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Bib Ready").smsTemplateId("12345678901234567890")
                .template("Hi #{fullName}, your bib #{bibNumber} is ready.")
                .build();

        SmsTemplate saved = buildSavedTemplate(101L, "bib ready", "Hi #{fullName}, your bib #{bibNumber} is ready.");

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId("12345678901234567890", 10L)).thenReturn(false);
        when(smsTemplateRepository.save(any())).thenReturn(saved);

        SmsTemplateResponse response = smsTemplateService.createSmsTemplate(10L, request, organizerUser);

        assertNotNull(response);
        assertEquals(101L, response.getId());
    }

    @Test
    @DisplayName("Template name is trimmed and lowercased before saving")
    void createSmsTemplate_nameTrimmedAndLowercased() {
        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("  Bib Ready  ").smsTemplateId("12345678901234567890")
                .template("Hi #{fullName}.")
                .build();

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);
        when(smsTemplateRepository.save(any())).thenReturn(buildSavedTemplate(103L, "bib ready", "Hi #{fullName}."));

        smsTemplateService.createSmsTemplate(10L, request, adminUser);

        ArgumentCaptor<SmsTemplate> captor = ArgumentCaptor.forClass(SmsTemplate.class);
        verify(smsTemplateRepository).save(captor.capture());
        assertEquals("bib ready", captor.getValue().getName());
    }

    @Test
    @DisplayName("Throws EventNotFoundException when event does not exist")
    void createSmsTemplate_eventNotFound_throwsEventNotFoundException() {
        when(eventRepository.findById(99L)).thenReturn(Optional.empty());

        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Test").smsTemplateId("12345678901234567890").template("Hi #{fullName}.").build();

        assertThrows(EventNotFoundException.class,
                () -> smsTemplateService.createSmsTemplate(99L, request, adminUser));

        verify(smsTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws SmsTemplateAlreadyExistsException when DLT ID is already used for the event")
    void createSmsTemplate_duplicateSmsTemplateId_throwsAlreadyExistsException() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId("12345678901234567890", 10L)).thenReturn(true);

        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Test").smsTemplateId("12345678901234567890").template("Hi #{fullName}.").build();

        assertThrows(SmsTemplateAlreadyExistsException.class,
                () -> smsTemplateService.createSmsTemplate(10L, request, adminUser));

        verify(smsTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws InvalidSmsTemplateException when template contains unknown placeholder")
    void createSmsTemplate_invalidPlaceholder_throwsInvalidSmsTemplateException() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);

        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Test").smsTemplateId("12345678901234567890")
                .template("Hi #{unknownField}, your bib is ready.")
                .build();

        assertThrows(InvalidSmsTemplateException.class,
                () -> smsTemplateService.createSmsTemplate(10L, request, adminUser));

        verify(smsTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Throws UnauthorizedAccessException when organizer accesses another organization's event")
    void createSmsTemplate_organizerUser_differentOrg_throwsUnauthorizedException() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));

        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Test").smsTemplateId("12345678901234567890").template("Hi #{fullName}.").build();

        assertThrows(UnauthorizedAccessException.class,
                () -> smsTemplateService.createSmsTemplate(10L, request, otherOrgUser));

        verify(smsTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Template is enabled by default on creation")
    void createSmsTemplate_enabledDefaultsToTrue() {
        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Test").smsTemplateId("12345678901234567890").template("Hi #{fullName}.").build();

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);
        when(smsTemplateRepository.save(any())).thenReturn(buildSavedTemplate(104L, "test", "Hi #{fullName}."));

        smsTemplateService.createSmsTemplate(10L, request, adminUser);

        ArgumentCaptor<SmsTemplate> captor = ArgumentCaptor.forClass(SmsTemplate.class);
        verify(smsTemplateRepository).save(captor.capture());
        assertTrue(captor.getValue().getEnabled());
    }

    @Test
    @DisplayName("Template with no placeholders passes validation and saves successfully")
    void createSmsTemplate_noPlaceholders_passesValidation() {
        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Static").smsTemplateId("12345678901234567890")
                .template("Your bib is ready for collection.")
                .build();

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);
        when(smsTemplateRepository.save(any())).thenReturn(buildSavedTemplate(105L, "static", "Your bib is ready for collection."));

        SmsTemplateResponse response = smsTemplateService.createSmsTemplate(10L, request, adminUser);

        assertNotNull(response);
        verify(smsTemplateRepository).save(any());
    }

    @Test
    @DisplayName("Throws InvalidSmsTemplateException when template contains empty braces #{}")
    void createSmsTemplate_emptyBraces_throwsInvalidSmsTemplateException() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);

        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Literal").smsTemplateId("12345678901234567890")
                .template("Hello #{}, collect your bib.")
                .build();

        assertThrows(InvalidSmsTemplateException.class,
                () -> smsTemplateService.createSmsTemplate(10L, request, adminUser));

        verify(smsTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("Multiple valid placeholders all pass validation and template saves successfully")
    void createSmsTemplate_multiplePlaceholders_allValid_createsSuccessfully() {
        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Multi").smsTemplateId("12345678901234567890")
                .template("Hi #{fullName}, your bib #{bibNumber} is ready at #{raceName}.")
                .build();

        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);
        when(smsTemplateRepository.save(any())).thenReturn(
                buildSavedTemplate(107L, "multi", "Hi #{fullName}, your bib #{bibNumber} is ready at #{raceName}."));

        SmsTemplateResponse response = smsTemplateService.createSmsTemplate(10L, request, adminUser);

        assertNotNull(response);
        verify(smsTemplateRepository).save(any());
    }

    @Test
    @DisplayName("Throws InvalidSmsTemplateException listing only the invalid placeholder among multiple")
    void createSmsTemplate_multiplePlaceholders_oneInvalid_throwsWithInvalidName() {
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(smsTemplateRepository.existsBySmsTemplateIdAndEventId(any(), eq(10L))).thenReturn(false);

        CreateSmsTemplateRequest request = CreateSmsTemplateRequest.builder()
                .name("Mixed").smsTemplateId("12345678901234567890")
                .template("Hi #{fullName}, your bib #{bibNumber} and #{chipTime} are ready.")
                .build();

        InvalidSmsTemplateException ex = assertThrows(InvalidSmsTemplateException.class,
                () -> smsTemplateService.createSmsTemplate(10L, request, adminUser));

        assertTrue(ex.getMessage().contains("chipTime"));
        verify(smsTemplateRepository, never()).save(any());
    }
}
