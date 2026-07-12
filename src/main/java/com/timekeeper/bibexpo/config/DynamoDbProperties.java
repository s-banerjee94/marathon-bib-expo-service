package com.timekeeper.bibexpo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * DynamoDB endpoint plus the name of every table the service uses. Each name has a
 * sensible default and can be overridden individually; {@code tablePrefix} namespaces
 * all of them at once (e.g. "staging-") for per-environment isolation. The resolver
 * methods return the prefixed name and are what repositories should call.
 */
@Configuration
@ConfigurationProperties(prefix = "aws.dynamodb")
@Data
public class DynamoDbProperties {

    /** Override endpoint for LocalStack; empty/blank targets real AWS DynamoDB. */
    private String endpoint;

    /** Prepended to every table name; empty by default. */
    private String tablePrefix = "";

    private String participants = "marathon-participants";
    private String notifications = "marathon-notifications";
    private String importErrors = "marathon-import-errors";
    private String eventStats = "marathon-event-stats";
    private String distributionLogs = "marathon-distribution-logs";
    private String auditLog = "marathon-audit-log";
    private String shortUrls = "marathon-short-urls";

    public String participantsTable()     { return tablePrefix + participants; }
    public String notificationsTable()    { return tablePrefix + notifications; }
    public String importErrorsTable()     { return tablePrefix + importErrors; }
    public String eventStatsTable()       { return tablePrefix + eventStats; }
    public String distributionLogsTable() { return tablePrefix + distributionLogs; }
    public String auditLogTable()         { return tablePrefix + auditLog; }
    public String shortUrlsTable()        { return tablePrefix + shortUrls; }
}
