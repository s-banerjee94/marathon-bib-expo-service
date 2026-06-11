package com.timekeeper.bibexpo.whatsapp.service.impl;

import com.timekeeper.bibexpo.exception.OrganizationNotFoundException;
import com.timekeeper.bibexpo.exception.UnauthorizedAccessException;
import com.timekeeper.bibexpo.model.entity.User;
import com.timekeeper.bibexpo.model.entity.UserRole;
import com.timekeeper.bibexpo.repository.OrganizationRepository;
import com.timekeeper.bibexpo.whatsapp.exception.InvalidWhatsAppConfigException;
import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppConfigNotFoundException;
import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.SaveWhatsAppConfigRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.request.WhatsAppTestSendRequest;
import com.timekeeper.bibexpo.whatsapp.model.dto.response.WhatsAppConfigResponse;
import com.timekeeper.bibexpo.whatsapp.model.entity.OrganizationWhatsAppConfig;
import com.timekeeper.bibexpo.whatsapp.model.entity.WhatsAppSenderModeChange;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderMode;
import com.timekeeper.bibexpo.whatsapp.model.enums.WhatsAppSenderScope;
import com.timekeeper.bibexpo.whatsapp.repository.OrganizationWhatsAppConfigRepository;
import com.timekeeper.bibexpo.whatsapp.repository.WhatsAppSenderModeChangeRepository;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppConfigService;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppConfigServiceImpl implements WhatsAppConfigService {

    private final OrganizationWhatsAppConfigRepository configRepository;
    private final WhatsAppSenderModeChangeRepository modeChangeRepository;
    private final OrganizationRepository organizationRepository;
    private final WhatsAppGatewayService gatewayService;

    @Override
    @Transactional(readOnly = true)
    public WhatsAppConfigResponse getConfig(Long organizationId, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        return configRepository.findByOrganizationId(organizationId)
                .map(this::toResponse)
                .orElseGet(this::defaultResponse);
    }

    @Override
    @Transactional
    public WhatsAppConfigResponse saveConfig(Long organizationId, SaveWhatsAppConfigRequest request, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        OrganizationWhatsAppConfig config = configRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> OrganizationWhatsAppConfig.builder().organizationId(organizationId).build());

        WhatsAppSenderMode fromMode = effectiveMode(config);
        config.setAccountSid(request.getAccountSid().trim());
        config.setAuthToken(request.getAuthToken().trim());
        config.setFromNumber(request.getFromNumber().trim());
        config.setSenderMode(WhatsAppSenderMode.CUSTOM);
        config.setVerified(false);
        OrganizationWhatsAppConfig saved = configRepository.save(config);

        recordModeChange(organizationId, fromMode, WhatsAppSenderMode.CUSTOM);
        log.info("WhatsApp credentials saved for organization ID: {}", organizationId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public WhatsAppConfigResponse updateMode(Long organizationId, WhatsAppSenderMode mode, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        OrganizationWhatsAppConfig config = configRepository.findByOrganizationId(organizationId)
                .orElseThrow(WhatsAppConfigNotFoundException::new);

        WhatsAppSenderMode fromMode = config.getSenderMode();
        if (fromMode == mode) {
            return toResponse(config);
        }

        config.setSenderMode(mode);
        OrganizationWhatsAppConfig saved = configRepository.save(config);

        recordModeChange(organizationId, fromMode, mode);
        log.info("WhatsApp sender mode switched to {} for organization ID: {}", mode, organizationId);
        return toResponse(saved);
    }

    // Intentionally non-transactional: the verified-status write below must commit in the
    // repository's own transaction so a failed test is persisted even though we then throw.
    @Override
    public WhatsAppConfigResponse testSend(Long organizationId, WhatsAppTestSendRequest request, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        OrganizationWhatsAppConfig config = configRepository.findByOrganizationId(organizationId)
                .orElseThrow(WhatsAppConfigNotFoundException::new);

        WhatsAppSender sender = WhatsAppSender.builder()
                .accountSid(config.getAccountSid())
                .authToken(config.getAuthToken())
                .fromNumber(config.getFromNumber())
                .scope(WhatsAppSenderScope.ORGANIZATION)
                .build();

        boolean verified;
        try {
            gatewayService.sendTemplate(sender, request.getToNumber(), request.getContentSid(), List.of());
            verified = true;
        } catch (WhatsAppSendException e) {
            verified = false;
        }

        config.setVerified(verified);
        OrganizationWhatsAppConfig saved = configRepository.save(config);

        if (!verified) {
            log.warn("WhatsApp test send failed for organization ID: {} — verification cleared", organizationId);
            throw new InvalidWhatsAppConfigException(
                    "Your WhatsApp credentials could not be verified, please check them and try again.");
        }

        log.info("WhatsApp test send succeeded for organization ID: {}", organizationId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteConfig(Long organizationId, User currentUser) {
        authorizeOrgAccess(currentUser, organizationId);
        verifyOrganizationExists(organizationId);

        OrganizationWhatsAppConfig config = configRepository.findByOrganizationId(organizationId)
                .orElseThrow(WhatsAppConfigNotFoundException::new);

        WhatsAppSenderMode fromMode = config.getSenderMode();
        configRepository.delete(config);

        recordModeChange(organizationId, fromMode, WhatsAppSenderMode.DEFAULT);
        log.info("WhatsApp credentials removed for organization ID: {}", organizationId);
    }

    private void authorizeOrgAccess(User currentUser, Long organizationId) {
        UserRole role = currentUser.getRole();
        if (role == UserRole.ROOT || role == UserRole.ADMIN) {
            return;
        }
        if (currentUser.getOrganization() == null
                || !currentUser.getOrganization().getId().equals(organizationId)) {
            throw new UnauthorizedAccessException("You do not have access to this organization's WhatsApp settings.");
        }
    }

    // Existence check only — the slice stores plain IDs, never the Organization entity
    private void verifyOrganizationExists(Long organizationId) {
        organizationRepository.findById(organizationId)
                .filter(org -> !Boolean.TRUE.equals(org.getDeleted()))
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private void recordModeChange(Long organizationId, WhatsAppSenderMode fromMode, WhatsAppSenderMode toMode) {
        modeChangeRepository.save(WhatsAppSenderModeChange.builder()
                .organizationId(organizationId)
                .fromMode(fromMode)
                .toMode(toMode)
                .build());
    }

    private WhatsAppSenderMode effectiveMode(OrganizationWhatsAppConfig config) {
        return config.getId() == null ? WhatsAppSenderMode.DEFAULT : config.getSenderMode();
    }

    private WhatsAppConfigResponse toResponse(OrganizationWhatsAppConfig config) {
        return WhatsAppConfigResponse.builder()
                .configured(true)
                .mode(config.getSenderMode())
                .accountSid(config.getAccountSid())
                .authTokenMasked(maskToken(config.getAuthToken()))
                .fromNumber(config.getFromNumber())
                .verified(config.isVerified())
                .build();
    }

    private WhatsAppConfigResponse defaultResponse() {
        return WhatsAppConfigResponse.builder()
                .configured(false)
                .mode(WhatsAppSenderMode.DEFAULT)
                .build();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 4) {
            return "••••";
        }
        return "••••" + token.substring(token.length() - 4);
    }
}
