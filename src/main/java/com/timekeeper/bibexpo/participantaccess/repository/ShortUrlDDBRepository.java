package com.timekeeper.bibexpo.participantaccess.repository;

import com.timekeeper.bibexpo.exception.ShortUrlNotFoundException;
import com.timekeeper.bibexpo.participantaccess.model.dynamodb.ShortUrlDDB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ShortUrlDDBRepository {

    private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
    private volatile DynamoDbTable<ShortUrlDDB> table;

    private DynamoDbTable<ShortUrlDDB> getTable() {
        if (table == null) {
            synchronized (this) {
                if (table == null) {
                    table = dynamoDbEnhancedClient.table("marathon-short-urls", TableSchema.fromBean(ShortUrlDDB.class));
                }
            }
        }
        return table;
    }

    public boolean saveIfAbsent(ShortUrlDDB shortUrl) {
        try {
            getTable().putItem(PutItemEnhancedRequest.builder(ShortUrlDDB.class)
                    .item(shortUrl)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_not_exists(shortCode)")
                            .build())
                    .build());
            log.debug("Saved short URL {} for event {} bib {}", shortUrl.getShortCode(),
                    shortUrl.getEventId(), shortUrl.getBibNumber());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    public void save(ShortUrlDDB shortUrl) {
        getTable().putItem(shortUrl);
    }

    public ShortUrlDDB findByCode(String shortCode) {
        return getTable().getItem(Key.builder().partitionValue(shortCode).build());
    }

    public ShortUrlDDB findByCodeOrThrow(String shortCode) {
        ShortUrlDDB item = findByCode(shortCode);
        if (item == null) {
            throw new ShortUrlNotFoundException();
        }
        return item;
    }
}
