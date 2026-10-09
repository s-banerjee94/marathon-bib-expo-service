package com.timekeeper.bibexpo.billing.service.impl;

import com.timekeeper.bibexpo.billing.config.BillingProperties;
import com.timekeeper.bibexpo.billing.service.BillingScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.ActionAfterCompletion;
import software.amazon.awssdk.services.scheduler.model.ConflictException;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindow;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.scheduler.model.RetryPolicy;
import software.amazon.awssdk.services.scheduler.model.Target;
import software.amazon.awssdk.services.scheduler.model.UpdateScheduleRequest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Arms/cancels the deferred auto-bill via a one-time EventBridge Scheduler schedule
 * named {@code bill-{eventId}}, whose target is the billing Lambda. The schedule only
 * fires the Lambda after the configured delay; all billing logic stays in the Lambda.
 *
 * <p>Failures are swallowed (logged, not rethrown): this runs after the status change
 * has already committed, so a scheduler hiccup must never surface as a failed status
 * change. When {@code billing.scheduling.enabled} is false (local dev, where LocalStack
 * stores but does not fire schedules) the calls are no-ops beyond logging.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillingScheduleServiceImpl implements BillingScheduleService {

    /** EventBridge {@code at()} expects a local date-time with no offset/zone suffix. */
    private static final DateTimeFormatter AT_EXPRESSION =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final SchedulerClient schedulerClient;
    private final BillingProperties billingProperties;

    @Override
    public void schedule(Long eventId) {
        if (!billingProperties.getScheduling().isEnabled()) {
            log.atDebug().addKeyValue("event", "billing.autobill.skipped").addKeyValue("eventId", eventId)
                    .log("Billing schedules are disabled; no auto-bill armed");
            return;
        }

        String name = scheduleName(eventId);
        String fireAt = AT_EXPRESSION.format(
                Instant.now().plus(billingProperties.getScheduler().getDelayHours(), ChronoUnit.HOURS));
        Target target = Target.builder()
                .arn(billingProperties.getLambda().getArn())
                .roleArn(billingProperties.getScheduler().getRoleArn())
                .input(autoBillPayload(eventId, name))
                // Single-shot: the auto-bill fires once and must not retry — a failed auto-draft
                // is recovered by a manual generate. EventBridge then auto-deletes the schedule
                // (ActionAfterCompletion=DELETE), so no self-cleanup is needed anywhere.
                .retryPolicy(RetryPolicy.builder().maximumRetryAttempts(0).build())
                .build();
        FlexibleTimeWindow window = FlexibleTimeWindow.builder()
                .mode(FlexibleTimeWindowMode.OFF)
                .build();

        try {
            try {
                schedulerClient.createSchedule(CreateScheduleRequest.builder()
                        .name(name)
                        .scheduleExpression("at(" + fireAt + ")")
                        .scheduleExpressionTimezone("UTC")
                        .flexibleTimeWindow(window)
                        .actionAfterCompletion(ActionAfterCompletion.DELETE)
                        .target(target)
                        .build());
            } catch (ConflictException existing) {
                // Re-completion before the previous timer fired: re-arm the existing schedule.
                schedulerClient.updateSchedule(UpdateScheduleRequest.builder()
                        .name(name)
                        .scheduleExpression("at(" + fireAt + ")")
                        .scheduleExpressionTimezone("UTC")
                        .flexibleTimeWindow(window)
                        .actionAfterCompletion(ActionAfterCompletion.DELETE)
                        .target(target)
                        .build());
            }
            log.atInfo().addKeyValue("event", "billing.autobill.armed").addKeyValue("eventId", eventId)
                    .addKeyValue("fireAt", fireAt).log("Auto-bill armed");
        } catch (SdkException e) {
            log.atError().addKeyValue("event", "billing.autobill.arm_failed").addKeyValue("eventId", eventId)
                    .setCause(e).log("Auto-bill could not be armed");
        }
    }

    @Override
    public void cancel(Long eventId) {
        if (!billingProperties.getScheduling().isEnabled()) {
            log.atDebug().addKeyValue("event", "billing.autobill.skipped").addKeyValue("eventId", eventId)
                    .log("Billing schedules are disabled; no auto-bill to cancel");
            return;
        }

        try {
            schedulerClient.deleteSchedule(DeleteScheduleRequest.builder()
                    .name(scheduleName(eventId))
                    .build());
            log.atInfo().addKeyValue("event", "billing.autobill.cancelled").addKeyValue("eventId", eventId)
                    .log("Pending auto-bill cancelled");
        } catch (ResourceNotFoundException none) {
            // No pending timer — nothing to cancel.
        } catch (SdkException e) {
            log.atError().addKeyValue("event", "billing.autobill.cancel_failed").addKeyValue("eventId", eventId)
                    .setCause(e).log("Pending auto-bill could not be cancelled");
        }
    }

    private String scheduleName(Long eventId) {
        return "bill-" + eventId;
    }

    private String autoBillPayload(Long eventId, String scheduleName) {
        return String.format("{\"eventId\":\"%d\",\"reason\":\"AUTO\",\"scheduleName\":\"%s\"}",
                eventId, scheduleName);
    }
}
