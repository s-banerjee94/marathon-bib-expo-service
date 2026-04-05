package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.model.dynamodb.ImportErrorDDB;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ImportErrorDDBRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private DynamoDbTable<ImportErrorDDB> table;

    @PostConstruct
    public void init() {
        this.table = dynamoDbEnhancedClient.table(
                "marathon-import-errors",
                TableSchema.fromBean(ImportErrorDDB.class)
        );
    }

    public void saveAll(List<ImportErrorDDB> errors) {
        if (errors.isEmpty()) return;

        int batchSize = 25;
        for (int i = 0; i < errors.size(); i += batchSize) {
            List<ImportErrorDDB> batch = errors.subList(i, Math.min(i + batchSize, errors.size()));
            WriteBatch.Builder<ImportErrorDDB> batchBuilder = WriteBatch.builder(ImportErrorDDB.class)
                    .mappedTableResource(table);
            batch.forEach(batchBuilder::addPutItem);
            dynamoDbEnhancedClient.batchWriteItem(
                    BatchWriteItemEnhancedRequest.builder()
                            .writeBatches(batchBuilder.build())
                            .build()
            );
        }
        log.info("Saved {} import errors for importId batch", errors.size());
    }

    /**
     * Returns a single DynamoDB page of errors for the given importId.
     * Pass {@code exclusiveStartKey} from the previous page for pagination.
     */
    public Page<ImportErrorDDB> findByImportId(String importId, int limit,
                                               Map<String, AttributeValue> exclusiveStartKey) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(importId).build()
        );
        QueryEnhancedRequest.Builder reqBuilder = QueryEnhancedRequest.builder()
                .queryConditional(condition)
                .limit(limit);

        if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
            reqBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        return table.query(reqBuilder.build()).stream().findFirst().orElse(null);
    }
}
