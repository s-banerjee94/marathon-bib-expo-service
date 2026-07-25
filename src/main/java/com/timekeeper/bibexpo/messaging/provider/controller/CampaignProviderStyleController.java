package com.timekeeper.bibexpo.messaging.provider.controller;

import com.timekeeper.bibexpo.messaging.provider.model.dto.response.CampaignProviderStyleResponse;
import com.timekeeper.bibexpo.messaging.provider.service.CampaignProviderStyleService;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CampaignProviderStyleController implements CampaignProviderStyleControllerApi {

    private final CampaignProviderStyleService campaignProviderStyleService;

    @Override
    public ResponseEntity<CampaignProviderStyleResponse> getStyle(Long eventId, MessageChannel channel, User currentUser) {
        return ResponseEntity.ok(campaignProviderStyleService.getStyle(eventId, channel, currentUser));
    }
}
