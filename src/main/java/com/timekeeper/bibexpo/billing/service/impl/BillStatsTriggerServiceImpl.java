package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.config.BillingProperties;
import com.timekeeper.bibexpo.billing.service.BillStatsTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillStatsTriggerServiceImpl implements BillStatsTriggerService {

    private final LambdaClient lambdaClient;
    private final BillingProperties billingProperties;

    @Override
    public void recomputeAsync(String reason) {
        String arn = billingProperties.getStatsLambda().getArn();
        if (arn == null || arn.isBlank()) {
            log.warn("[bill-stats] no stats Lambda ARN configured — skipping {} recompute", reason);
            return;
        }
        String payload = String.format("{\"reason\":\"%s\"}", reason);
        try {
            // InvocationType.EVENT: fire-and-forget. We do not read the result — the Lambda upserts
            // the snapshot on its own and the read endpoint picks it up on the next fetch.
            lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(arn)
                    .invocationType(InvocationType.EVENT)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build());
            log.info("[bill-stats] triggered {} recompute", reason);
        } catch (Exception e) {
            // Best-effort: the snapshot stays as-is until the next trigger or a manual refresh.
            log.error("[bill-stats] could not trigger {} recompute: {}", reason, e.getMessage());
        }
    }
}
