package com.timekeeper.bibexpo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
@Tag(name = "Dev Operations", description = "Development-only endpoints for testing and data management (only available in dev profile)")
public class DevOpsController {

    private static final String PARTICIPANTS_TABLE = "marathon-participants";
    private static final String DISTRIBUTION_LOGS_TABLE = "marathon-distribution-logs";
    private static final String IMPORT_ERRORS_TABLE = "marathon-import-errors";
    public static final String DELETED_COUNT = "deletedCount";
    public static final String SUCCESS = "success";

    private final DynamoDbClient dynamoDbClient;

    @DeleteMapping("/clear-participants")
    @Operation(
            summary = "Clear all participants data",
            description = "Deletes all records from the marathon-participants DynamoDB table. Use with caution!",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully cleared participants table"),
                    @ApiResponse(responseCode = "500", description = "Error occurred while clearing table")
            }
    )
    public ResponseEntity<Map<String, Object>> clearParticipants() {
        return clearDynamoDBTable(PARTICIPANTS_TABLE, "eventId", "bibNumber");
    }

    @DeleteMapping("/clear-distribution-logs")
    @Operation(
            summary = "Clear all distribution logs",
            description = "Deletes all records from the marathon-distribution-logs DynamoDB table. Use with caution!",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully cleared distribution logs table"),
                    @ApiResponse(responseCode = "500", description = "Error occurred while clearing table")
            }
    )
    public ResponseEntity<Map<String, Object>> clearDistributionLogs() {
        return clearDynamoDBTable(DISTRIBUTION_LOGS_TABLE, "eventId", "timestamp");
    }

    @DeleteMapping("/clear-import-errors")
    @Operation(
            summary = "Clear all import errors",
            description = "Deletes all records from the marathon-import-errors DynamoDB table. Use with caution!",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully cleared import errors table"),
                    @ApiResponse(responseCode = "500", description = "Error occurred while clearing table")
            }
    )
    public ResponseEntity<Map<String, Object>> clearImportErrors() {
        return clearDynamoDBTable(IMPORT_ERRORS_TABLE, "importId", "rowNumber");
    }

    @DeleteMapping("/clear-all-dynamodb")
    @Operation(
            summary = "Clear all DynamoDB tables",
            description = "Deletes all records from all three DynamoDB tables (participants, distribution logs, and import errors). Use with extreme caution!",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully cleared all DynamoDB tables"),
                    @ApiResponse(responseCode = "500", description = "Error occurred while clearing tables")
            }
    )
    public ResponseEntity<Map<String, Object>> clearAllDynamoDB() {
        log.warn("CLEARING ALL DATA FROM ALL DYNAMODB TABLES");

        Map<String, Object> totalResponse = new HashMap<>();
        int totalDeleted = 0;
        boolean allSuccess = true;
        Map<String, Integer> tableResults = new HashMap<>();

        try {
            ResponseEntity<Map<String, Object>> participantsResult = clearParticipants();
            if (participantsResult.getBody() != null) {
                int count = (Integer) participantsResult.getBody().getOrDefault(DELETED_COUNT, 0);
                tableResults.put(PARTICIPANTS_TABLE, count);
                totalDeleted += count;
                allSuccess &= (Boolean) participantsResult.getBody().getOrDefault(SUCCESS, false);
            }

            ResponseEntity<Map<String, Object>> distributionResult = clearDistributionLogs();
            if (distributionResult.getBody() != null) {
                int count = (Integer) distributionResult.getBody().getOrDefault(DELETED_COUNT, 0);
                tableResults.put(DISTRIBUTION_LOGS_TABLE, count);
                totalDeleted += count;
                allSuccess &= (Boolean) distributionResult.getBody().getOrDefault(SUCCESS, false);
            }

            ResponseEntity<Map<String, Object>> importResult = clearImportErrors();
            if (importResult.getBody() != null) {
                int count = (Integer) importResult.getBody().getOrDefault(DELETED_COUNT, 0);
                tableResults.put(IMPORT_ERRORS_TABLE, count);
                totalDeleted += count;
                allSuccess &= (Boolean) importResult.getBody().getOrDefault(SUCCESS, false);
            }

            totalResponse.put(SUCCESS, allSuccess);
            totalResponse.put("message", "Cleared all DynamoDB tables");
            totalResponse.put("totalDeletedCount", totalDeleted);
            totalResponse.put("tableResults", tableResults);

            return ResponseEntity.ok(totalResponse);

        } catch (Exception e) {
            log.error("Error clearing all DynamoDB tables: {}", e.getMessage(), e);

            totalResponse.put(SUCCESS, false);
            totalResponse.put("message", "Error clearing DynamoDB tables: " + e.getMessage());
            totalResponse.put("totalDeletedCount", totalDeleted);
            totalResponse.put("tableResults", tableResults);

            return ResponseEntity.internalServerError().body(totalResponse);
        }
    }

    private ResponseEntity<Map<String, Object>> clearDynamoDBTable(String tableName, String partitionKeyName, String sortKeyName) {
        log.warn("CLEARING ALL DATA FROM DYNAMODB TABLE: {}", tableName);

        int deletedCount = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;

        try {
            do {
                ScanRequest.Builder scanBuilder = ScanRequest.builder()
                        .tableName(tableName)
                        .limit(100);

                if (lastEvaluatedKey != null) {
                    scanBuilder.exclusiveStartKey(lastEvaluatedKey);
                }

                ScanResponse scanResponse = dynamoDbClient.scan(scanBuilder.build());

                for (Map<String, AttributeValue> item : scanResponse.items()) {
                    Map<String, AttributeValue> key = new HashMap<>();
                    key.put(partitionKeyName, item.get(partitionKeyName));

                    AttributeValue sortKeyValue = item.get(sortKeyName);
                    if (sortKeyValue != null) {
                        key.put(sortKeyName, sortKeyValue);
                    }

                    DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                            .tableName(tableName)
                            .key(key)
                            .build();

                    dynamoDbClient.deleteItem(deleteRequest);
                    deletedCount++;
                }

                lastEvaluatedKey = scanResponse.lastEvaluatedKey();

            } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

            log.info("Successfully deleted {} items from DynamoDB table: {}", deletedCount, tableName);

            Map<String, Object> response = new HashMap<>();
            response.put(SUCCESS, true);
            response.put("message", "All data cleared from DynamoDB table");
            response.put(DELETED_COUNT, deletedCount);
            response.put("tableName", tableName);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error clearing DynamoDB table {}: {}", tableName, e.getMessage(), e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put(SUCCESS, false);
            errorResponse.put("message", "Error clearing DynamoDB table: " + e.getMessage());
            errorResponse.put(DELETED_COUNT, deletedCount);
            errorResponse.put("tableName", tableName);

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
