package com.timekeeper.bibexpo.service;

import com.timekeeper.bibexpo.exception.SmsTemplateAlreadyExistsException;
import com.timekeeper.bibexpo.exception.SmsTemplateNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.dto.request.CreateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.request.UpdateSmsTemplateRequest;
import com.timekeeper.bibexpo.model.dto.response.SmsTemplateResponse;
import com.timekeeper.bibexpo.model.entity.User;
import java.util.List;


public interface SmsTemplateService {

    /**
     * Create a new SMS template for an event
     * Authorization:
     * - ROOT and ADMIN can create templates for any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can create templates for their organization's events
     * @param eventId The event ID
     * @param request The SMS template creation request
     * @param currentUser The authenticated user
     * @return The created SMS template response
     * @throws SmsTemplateAlreadyExistsException if smsTemplateId already exists for the event
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    SmsTemplateResponse createSmsTemplate(Long eventId, CreateSmsTemplateRequest request, User currentUser);

    /**
     * Update an existing SMS template
     * Authorization:
     * - ROOT and ADMIN can update any template
     * - ORGANIZER_ADMIN and ORGANIZER_USER can update templates in their organization's events
     * @param eventId The event ID
     * @param templateId The SMS template ID
     * @param request The SMS template update request
     * @param currentUser The authenticated user
     * @return The updated SMS template response
     * @throws SmsTemplateNotFoundException if the template does not exist
     * @throws SmsTemplateAlreadyExistsException if smsTemplateId already exists for the event (for update)
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    SmsTemplateResponse updateSmsTemplate(Long eventId, Long templateId, UpdateSmsTemplateRequest request, User currentUser);

    /**
     * Get all SMS templates for an event (paginated)
     * Authorization:
     * - ROOT and ADMIN can view templates for any event
     * - ORGANIZER_ADMIN and ORGANIZER_USER can view templates for their organization's events
     * @param eventId The event ID
     * @param search Partial match on name or smsTemplateId
     * @param currentUser The authenticated user
     * @return List of SMS template responses
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    List<SmsTemplateResponse> getSmsTemplatesByEvent(Long eventId, String search, User currentUser);

    /**
     * Get an SMS template by ID
     * Authorization:
     * - ROOT and ADMIN can view any template
     * - ORGANIZER_ADMIN and ORGANIZER_USER can view templates in their organization's events
     * @param eventId The event ID
     * @param templateId The SMS template ID
     * @param currentUser The authenticated user
     * @return The SMS template response
     * @throws SmsTemplateNotFoundException if the template does not exist
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    SmsTemplateResponse getSmsTemplateById(Long eventId, Long templateId, User currentUser);

    /**
     * Get an SMS template by DLT Template ID
     * Authorization:
     * - ROOT and ADMIN can view any template
     * - ORGANIZER_ADMIN and ORGANIZER_USER can view templates in their organization's events
     * @param eventId The event ID
     * @param smsTemplateId The DLT Template ID
     * @param currentUser The authenticated user
     * @return The SMS template response
     * @throws SmsTemplateNotFoundException if the template does not exist
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    SmsTemplateResponse getSmsTemplateBySmsTemplateId(Long eventId, String smsTemplateId, User currentUser);

    /**
     * Delete an SMS template
     * Authorization:
     * - ROOT and ADMIN can delete any template
     * - ORGANIZER_ADMIN and ORGANIZER_USER can delete templates in their organization's events
     * @param eventId The event ID
     * @param templateId The SMS template ID
     * @param currentUser The authenticated user
     * @throws SmsTemplateNotFoundException if the template does not exist
     * @throws UnauthorizedAccessException if the user is not authorized
     */
    void deleteSmsTemplate(Long eventId, Long templateId, User currentUser);
}
