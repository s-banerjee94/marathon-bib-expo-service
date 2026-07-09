#!/bin/bash

echo "Initializing LocalStack resources..."

echo "Creating DynamoDB table: marathon-participants"
awslocal dynamodb create-table \
    --table-name marathon-participants \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
        AttributeName=bibNumber,AttributeType=S \
        AttributeName=chipNumber,AttributeType=S \
        AttributeName=fullName,AttributeType=S \
        AttributeName=email,AttributeType=S \
        AttributeName=phoneNumber,AttributeType=S \
        AttributeName=raceCategoryKey,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH \
        AttributeName=bibNumber,KeyType=RANGE \
    --local-secondary-indexes \
        "[
            {
                \"IndexName\": \"LSI-ChipNumberIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"chipNumber\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-FullNameIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"fullName\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-EmailIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"email\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-PhoneNumberIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"phoneNumber\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-RaceCategoryIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"raceCategoryKey\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

echo "DynamoDB table marathon-participants created successfully!"

echo "Creating DynamoDB table: marathon-import-errors"
awslocal dynamodb create-table \
    --table-name marathon-import-errors \
    --attribute-definitions \
        AttributeName=importId,AttributeType=S \
        AttributeName=rowNumber,AttributeType=N \
    --key-schema \
        AttributeName=importId,KeyType=HASH \
        AttributeName=rowNumber,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

echo "Enabling TTL on marathon-import-errors table (expirationTime attribute)"
awslocal dynamodb update-time-to-live \
    --table-name marathon-import-errors \
    --time-to-live-specification "Enabled=true, AttributeName=expirationTime"

echo "DynamoDB table marathon-import-errors created successfully with TTL enabled!"

echo "Creating DynamoDB table: marathon-distribution-logs"
awslocal dynamodb create-table \
    --table-name marathon-distribution-logs \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
        AttributeName=timestamp,AttributeType=S \
        AttributeName=bibNumber,AttributeType=S \
        AttributeName=action,AttributeType=S \
        AttributeName=performedBy,AttributeType=S \
        AttributeName=collectorName,AttributeType=S \
        AttributeName=collectorPhone,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH \
        AttributeName=timestamp,KeyType=RANGE \
    --local-secondary-indexes \
        "[
            {
                \"IndexName\": \"LSI-BibNumberIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"bibNumber\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-ActionIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"action\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-PerformedByIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"performedBy\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-CollectorNameIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"collectorName\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-CollectorPhoneIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"collectorPhone\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

echo "DynamoDB table marathon-distribution-logs created successfully!"

echo "Creating DynamoDB table: marathon-event-stats"
awslocal dynamodb create-table \
    --table-name marathon-event-stats \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
        AttributeName=statKey,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH \
        AttributeName=statKey,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

echo "DynamoDB table marathon-event-stats created successfully!"

echo "Creating DynamoDB table: marathon-short-urls"
awslocal dynamodb create-table \
    --table-name marathon-short-urls \
    --attribute-definitions \
        AttributeName=shortCode,AttributeType=S \
    --key-schema \
        AttributeName=shortCode,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST

echo "Enabling TTL on marathon-short-urls table (expirationTime attribute)"
awslocal dynamodb update-time-to-live \
    --table-name marathon-short-urls \
    --time-to-live-specification "Enabled=true, AttributeName=expirationTime"

echo "DynamoDB table marathon-short-urls created successfully with TTL enabled!"

echo "Creating DynamoDB table: marathon-audit-log"
awslocal dynamodb create-table \
    --table-name marathon-audit-log \
    --attribute-definitions \
        AttributeName=organizationId,AttributeType=N \
        AttributeName=eventKey,AttributeType=S \
        AttributeName=actionKey,AttributeType=S \
        AttributeName=entityTypeKey,AttributeType=S \
        AttributeName=actorKey,AttributeType=S \
    --key-schema \
        AttributeName=organizationId,KeyType=HASH \
        AttributeName=eventKey,KeyType=RANGE \
    --local-secondary-indexes \
        "[
            {
                \"IndexName\": \"LSI-ActionTimeIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"organizationId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"actionKey\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-EntityTypeTimeIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"organizationId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"entityTypeKey\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-ActorTimeIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"organizationId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"actorKey\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

echo "Enabling TTL on marathon-audit-log table (expirationTime attribute — 15 day retention)"
awslocal dynamodb update-time-to-live \
    --table-name marathon-audit-log \
    --time-to-live-specification "Enabled=true, AttributeName=expirationTime"

echo "DynamoDB table marathon-audit-log created successfully with TTL enabled!"

echo "Creating DynamoDB table: marathon-notifications"
awslocal dynamodb create-table \
    --table-name marathon-notifications \
    --attribute-definitions \
        AttributeName=userId,AttributeType=N \
        AttributeName=notificationKey,AttributeType=S \
        AttributeName=unreadKey,AttributeType=S \
    --key-schema \
        AttributeName=userId,KeyType=HASH \
        AttributeName=notificationKey,KeyType=RANGE \
    --local-secondary-indexes \
        "[
            {
                \"IndexName\": \"LSI-UnreadIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"userId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"unreadKey\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

echo "Enabling TTL on marathon-notifications table (expirationTime attribute — 30 day retention)"
awslocal dynamodb update-time-to-live \
    --table-name marathon-notifications \
    --time-to-live-specification "Enabled=true, AttributeName=expirationTime"

echo "DynamoDB table marathon-notifications created successfully with TTL enabled!"

# LangGraph (Python agent) checkpoint store — schema required by langgraph-checkpoint-aws's
# DynamoDBSaver: partition key PK, sort key SK, TTL attribute "ttl".
echo "Creating DynamoDB table: marathon-ai-agent-checkpoints"
awslocal dynamodb create-table \
    --table-name marathon-ai-agent-checkpoints \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

echo "Enabling TTL on marathon-ai-agent-checkpoints table (ttl attribute — 30 day retention)"
awslocal dynamodb update-time-to-live \
    --table-name marathon-ai-agent-checkpoints \
    --time-to-live-specification "Enabled=true, AttributeName=ttl"

echo "DynamoDB table marathon-ai-agent-checkpoints created successfully with TTL enabled!"

# Per-user daily AI token usage. Spring pre-checks the day's bucket; the Python agent atomically
# ADDs the tokens of each model call. PK=USER#<id>, SK=DAY#<yyyy-MM-dd>; ttl auto-expires old days.
echo "Creating DynamoDB table: marathon-ai-usage"
awslocal dynamodb create-table \
    --table-name marathon-ai-usage \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

echo "Enabling TTL on marathon-ai-usage table (ttl attribute — daily buckets auto-expire)"
awslocal dynamodb update-time-to-live \
    --table-name marathon-ai-usage \
    --time-to-live-specification "Enabled=true, AttributeName=ttl"

echo "DynamoDB table marathon-ai-usage created successfully with TTL enabled!"

# Per-user AI assistant preferences (e.g. the MCP tool enable/disable toggle). Agent-owned and read
# by the Python service; consumer is Python, so it lives beside the other AI tables. PK=USER#<id>,
# SK=PREFS#<name> (one item per preference group). Deliberately NO TTL — unlike the usage/checkpoint
# tables, a saved setting must persist indefinitely.
echo "Creating DynamoDB table: marathon-ai-agent-prefs"
awslocal dynamodb create-table \
    --table-name marathon-ai-agent-prefs \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST

echo "DynamoDB table marathon-ai-agent-prefs created successfully (no TTL — preferences persist)!"

# Bucket name matches the application.yaml default (aws.s3.bucket / AWS_S3_BUCKET).
echo "Creating S3 bucket: marathon-bib-expo-media"
awslocal s3api create-bucket --bucket marathon-bib-expo-media

echo "Applying CORS to marathon-bib-expo-media (browser PUT/GET via presigned URLs)"
awslocal s3api put-bucket-cors --bucket marathon-bib-expo-media --cors-configuration '{
    "CORSRules": [
        {
            "AllowedHeaders": ["*"],
            "AllowedMethods": ["GET", "PUT", "POST", "HEAD"],
            "AllowedOrigins": ["*"],
            "ExposeHeaders": ["ETag"],
            "MaxAgeSeconds": 3600
        }
    ]
}'

echo "S3 bucket marathon-bib-expo-media created successfully with CORS enabled!"
echo "LocalStack initialization complete!"
