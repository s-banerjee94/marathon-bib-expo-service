package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.model.dynamodb.DistributionLogDDB;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
@RequiredArgsConstructor
@Slf4j
public class DistributionLogDDBRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private DynamoDbTable<DistributionLogDDB> table;

    @PostConstruct
    public void init() {
        this.table = dynamoDbEnhancedClient.table(
                "marathon-distribution-logs",
                TableSchema.fromBean(DistributionLogDDB.class)
        );
    }

    public void save(DistributionLogDDB logEntry) {
        table.putItem(logEntry);
        log.debug("Saved distribution log for event {} bib {} action {}",
                logEntry.getEventId(), logEntry.getBibNumber(), logEntry.getAction());
    }

    public DynamoDbTable<DistributionLogDDB> getTable() {
        return table;
    }
}
