package com.timekeeper.bibexpo.service.impl;

import com.timekeeper.bibexpo.service.SmsGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"default", "dev", "test", "local"})
@Slf4j
public class SmsGatewayServiceStubImpl implements SmsGatewayService {

    @Override
    public void send(String phoneNumber, String message, String dltTemplateId) {
        log.info("[SMS-STUB] to={} dltTemplateId={} message=\"{}\"", phoneNumber, dltTemplateId, message);
    }
}
