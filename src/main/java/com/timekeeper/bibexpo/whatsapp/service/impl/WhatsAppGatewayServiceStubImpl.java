package com.timekeeper.bibexpo.whatsapp.service.impl;

import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;
import com.timekeeper.bibexpo.whatsapp.service.WhatsAppGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Profile({"default", "dev", "test", "local"})
@Slf4j
public class WhatsAppGatewayServiceStubImpl implements WhatsAppGatewayService {

    @Override
    public String sendTemplate(WhatsAppSender sender, String to, String contentSid, List<String> variables) {
        String messageSid = "SM-STUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[WHATSAPP-STUB] to={} scope={} contentSid={} variables={} -> sid={}",
                to, sender.getScope(), contentSid, variables, messageSid);
        return messageSid;
    }
}
