package com.timekeeper.bibexpo.billing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the external billing pipeline. The Spring app never computes a bill or
 * renders a PDF — that lives entirely in the Python billing Lambda. These properties
 * only describe how to (a) arm the deferred EventBridge schedule that fires the Lambda
 * after an event turns terminal and (b) invoke that same Lambda directly for an
 * on-demand bill. Pricing, tax and issuer details belong to the Lambda's own
 * environment, not here.
 */
@Configuration
@ConfigurationProperties(prefix = "billing")
@Data
public class BillingProperties {

    private final Scheduling scheduling = new Scheduling();
    private final Scheduler scheduler = new Scheduler();
    private final Lambda lambda = new Lambda();

    @Data
    public static class Scheduling {
        /**
         * Master switch for the deferred auto-bill timer. Off locally (LocalStack stores
         * schedules but never fires them); on in real AWS. When off, status changes are
         * still observed and logged but no EventBridge schedule is created.
         */
        private boolean enabled = false;
    }

    @Data
    public static class Scheduler {
        /** LocalStack endpoint override; empty/blank targets real AWS EventBridge Scheduler. */
        private String endpoint;

        /** ARN of the IAM role EventBridge Scheduler assumes to invoke the billing Lambda. */
        private String roleArn;

        /** How long after an event turns terminal the auto-bill fires. */
        private long delayHours = 5;
    }

    @Data
    public static class Lambda {
        /** ARN (or name) of the billing Lambda — the EventBridge target and the manual-invoke target. */
        private String arn;

        /** LocalStack endpoint override; empty/blank targets real AWS Lambda. */
        private String endpoint;
    }
}
