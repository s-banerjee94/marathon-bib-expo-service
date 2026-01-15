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
echo "LocalStack initialization complete!"
