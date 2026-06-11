package com.timekeeper.bibexpo.whatsapp.service;

import com.timekeeper.bibexpo.whatsapp.exception.WhatsAppSendException;
import com.timekeeper.bibexpo.whatsapp.model.WhatsAppSender;

import java.util.List;

public interface WhatsAppGatewayService {

    /**
     * Send a single WhatsApp template message through Twilio.
     *
     * <p>The same call serves both the application-default account and an organization's own
     * account; the credentials and sender number come from the supplied {@link WhatsAppSender},
     * which is the only thing that differs between the two. A document send is the same call with
     * a media content template whose URL variable carries a presigned link.
     *
     * @param sender     resolved sender (account SID, auth token, from-number, scope)
     * @param to         recipient phone number, country-coded; normalised to {@code whatsapp:+E164}
     * @param contentSid Twilio Content SID ({@code HX…}) of the approved template
     * @param variables  ordered template variables, mapped positionally to {@code ContentVariables} "1"…"n"
     * @return the Twilio Message SID of the accepted message — the key for future delivery-status tracking
     * @throws WhatsAppSendException if the gateway call fails or Twilio returns a non-2xx response
     */
    String sendTemplate(WhatsAppSender sender, String to, String contentSid, List<String> variables);
}
