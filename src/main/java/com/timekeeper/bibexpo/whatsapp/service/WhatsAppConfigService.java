package com.timekeeper.bibexpo.whatsapp.service;

import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.SaveWhatsAppConfigRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.WhatsAppTestSendRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.response.WhatsAppConfigResponse;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;

public interface WhatsAppConfigService {

    /**
     * Current WhatsApp sender configuration of an organization. When no credentials are
     * saved the response carries {@code configured=false} and mode {@code DEFAULT}.
     * The auth token is never returned — only a masked tail.
     *
     * @param organizationId organization to inspect
     * @param currentUser    caller; ORGANIZER_ADMIN may only access their own organization
     * @return current mode, masked credentials and verification status
     */
    WhatsAppConfigResponse getConfig(Long organizationId, User currentUser);

    /**
     * Create or replace the organization's own Twilio credentials and switch it to
     * {@code CUSTOM} mode. Writes a sender-mode audit row.
     *
     * @param organizationId organization to configure
     * @param request        account SID, auth token and WhatsApp sender number
     * @param currentUser    caller; ORGANIZER_ADMIN may only access their own organization
     * @return updated configuration with masked credentials
     */
    WhatsAppConfigResponse saveConfig(Long organizationId, SaveWhatsAppConfigRequest request, User currentUser);

    /**
     * Switch the organization between its own sender and the application default.
     * Saved credentials are retained when switching to {@code DEFAULT}; switching to
     * {@code CUSTOM} requires credentials to exist. Writes a sender-mode audit row.
     *
     * @param organizationId organization to switch
     * @param mode           target sender mode
     * @param currentUser    caller; ORGANIZER_ADMIN may only access their own organization
     * @return updated configuration
     */
    WhatsAppConfigResponse updateMode(Long organizationId, WhatsAppSenderMode mode, User currentUser);

    /**
     * Send a template message to a given number using the organization's saved credentials
     * (regardless of current mode) to verify they work. Sets {@code verified} true on success
     * and false on failure.
     *
     * @param organizationId organization whose credentials are tested
     * @param request        Content SID of an approved template and the recipient number
     * @param currentUser    caller; ORGANIZER_ADMIN may only access their own organization
     * @return updated configuration with the new verification status
     */
    WhatsAppConfigResponse testSend(Long organizationId, WhatsAppTestSendRequest request, User currentUser);

    /**
     * Remove the organization's saved credentials entirely, falling back to the application
     * default sender. Writes a sender-mode audit row.
     *
     * @param organizationId organization whose configuration is removed
     * @param currentUser    caller; ORGANIZER_ADMIN may only access their own organization
     */
    void deleteConfig(Long organizationId, User currentUser);
}
