package com.timekeeper.bibexpo.whatsapp.service;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.CreateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.UpdateWhatsAppTemplateRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.response.WhatsAppTemplateResponse;

import java.util.List;

public interface WhatsAppTemplateService {

    /**
     * Register an approved Twilio Content Template for an event. The Content SID must be
     * unique within the event, the event has a maximum of 20 templates, and every
     * {@code #{placeholder}} in the body variables must be a known template field.
     *
     * @param eventId     event the template belongs to
     * @param request     name, Content SID, ordered body variables, note and sender scope
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the created template
     */
    WhatsAppTemplateResponse createTemplate(Long eventId, CreateWhatsAppTemplateRequest request, User currentUser);

    /**
     * Partially update a template; absent fields are left unchanged.
     *
     * @param eventId     event the template belongs to
     * @param templateId  template to update
     * @param request     fields to change
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the updated template
     */
    WhatsAppTemplateResponse updateTemplate(Long eventId, Long templateId, UpdateWhatsAppTemplateRequest request, User currentUser);

    /**
     * List an event's templates, optionally filtered by a name/Content-SID search term.
     *
     * @param eventId     event whose templates are listed
     * @param search      optional case-insensitive search over name and Content SID
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return matching templates
     */
    List<WhatsAppTemplateResponse> getTemplatesByEvent(Long eventId, String search, User currentUser);

    /**
     * Fetch a single template by ID within an event.
     *
     * @param eventId     event the template belongs to
     * @param templateId  template to fetch
     * @param currentUser caller; organizer roles may only access their organization's events
     * @return the template
     */
    WhatsAppTemplateResponse getTemplateById(Long eventId, Long templateId, User currentUser);

    /**
     * Delete a template.
     *
     * @param eventId     event the template belongs to
     * @param templateId  template to delete
     * @param currentUser caller; organizer roles may only access their organization's events
     */
    void deleteTemplate(Long eventId, Long templateId, User currentUser);
}
