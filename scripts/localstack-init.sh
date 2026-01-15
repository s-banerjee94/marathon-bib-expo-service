#!/bin/bash

echo "Initializing LocalStack resources..."

echo "Creating DynamoDB table: marathon-participants"
awslocal dynamodb create-table \
    --table-name marathon-participants \
    --attribute-definitions \
        AttributeName=eventId,AttributeType=S \
        AttributeName=bibNumber,AttributeType=S \
        AttributeName=fullName,AttributeType=S \
        AttributeName=email,AttributeType=S \
        AttributeName=phoneNumber,AttributeType=S \
        AttributeName=raceName,AttributeType=S \
        AttributeName=categoryName,AttributeType=S \
    --key-schema \
        AttributeName=eventId,KeyType=HASH \
        AttributeName=bibNumber,KeyType=RANGE \
    --local-secondary-indexes \
        "[
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
                \"IndexName\": \"LSI-RaceNameIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"raceName\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            },
            {
                \"IndexName\": \"LSI-CategoryNameIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"categoryName\", \"KeyType\": \"RANGE\"}
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
        AttributeName=itemName,AttributeType=S \
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
                \"IndexName\": \"LSI-ItemNameIndex\",
                \"KeySchema\": [
                    {\"AttributeName\": \"eventId\", \"KeyType\": \"HASH\"},
                    {\"AttributeName\": \"itemName\", \"KeyType\": \"RANGE\"}
                ],
                \"Projection\": {\"ProjectionType\": \"ALL\"}
            }
        ]" \
    --billing-mode PAY_PER_REQUEST

echo "DynamoDB table marathon-distribution-logs created successfully!"
echo "LocalStack initialization complete!"
