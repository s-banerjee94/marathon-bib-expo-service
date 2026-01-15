package com.timekeeper.bibexpo.repository.dynamodb;

import com.timekeeper.bibexpo.exception.ParticipantNotFoundException;
import com.timekeeper.bibexpo.model.dynamodb.ParticipantDDB;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ParticipantDDBRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private DynamoDbTable<ParticipantDDB> table;

    @PostConstruct
    public void init() {
        this.table = dynamoDbEnhancedClient.table(
                "marathon-participants",
                TableSchema.fromBean(ParticipantDDB.class)
        );
    }

    public ParticipantDDB findByEventAndBibOrThrow(Long eventId, String bibNumber) {
        String eventIdStr = String.valueOf(eventId);
        Key key = Key.builder()
                .partitionValue(eventIdStr)
                .sortValue(bibNumber)
                .build();

        ParticipantDDB participant = table.getItem(key);
        if (participant == null) {
            throw new ParticipantNotFoundException(eventIdStr, bibNumber);
        }

        log.debug("Found participant with bib {} for event {}", bibNumber, eventId);
        return participant;
    }

    public void save(ParticipantDDB participant) {
        table.putItem(participant);
        log.debug("Saved participant with bib {} for event {}",
                participant.getBibNumber(), participant.getEventId());
    }

    public DynamoDbTable<ParticipantDDB> getTable() {
        return table;
    }
}
