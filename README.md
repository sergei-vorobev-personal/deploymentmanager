# Deployment Manager App

REST API application to manage AWS Lambdas using LocalStack in Docker:

- Upload ZIP files to a specified S3 bucket
- Create, update, or delete Lambda functions
- Check Lambda status
- Re-deploy updated files if already deployed
- Reverse proxy for Lambda invocation

## Sequence Diagrams
### Deployment Request (Create/Update)
```mermaid
sequenceDiagram
  participant Client
  participant API
  participant DB
  participant Kafka
  participant AwsSDK as AWS SDK
%% 1) Create or update application
  Client ->> API: POST /applications?name,key,bucket
  API ->> DB: create new or get existing record
  API ->> Kafka: send create/update message
  API ->> Client: 202 CREATE_REQUESTED/UPDATE_REQUESTED
  Kafka -->> AwsSDK: create/update Lambda
  AwsSDK -->> DB: update status to CREATING/UPDATING
```

### Lambda Deployment Polling
```mermaid
sequenceDiagram
  participant PollingService
  participant DB
  participant AwsSDK as AWS SDK
  
  PollingService ->> DB: get pending deployments
  PollingService ->> AwsSDK: get function configuration
  PollingService ->> DB: update application state
  PollingService ->> PollingService: reschedule if needed
```

### Proxy Lambda call
```mermaid
sequenceDiagram
  participant Client
  participant API
  participant DB
  participant AWSLambda

  Client ->> API: /proxy/{appName}
  API ->> DB: get function URL if ready
  API ->> AWSLambda: invoke Lambda via URL
  AWSLambda ->> API: Lambda response
  API ->> Client: response
```

### Get App Status
```mermaid
sequenceDiagram
  participant Client
  participant API
  participant DB
  Client ->> API: GET /applications/{name}/status
  API ->> DB: fetch status
  DB ->> API: return status
  API ->> Client: return status
```

### Delete App
```mermaid
sequenceDiagram
  participant Client
  participant API
  participant DB
  participant Kafka
  participant AwsSDK as AWS SDK
  Client ->> API: DELETE /applications/{name}
  API ->> Kafka: send delete message
  API ->> Client: 202 DELETE_REQUESTED
  Kafka -->> AwsSDK: delete Lambda
  AwsSDK -->> DB: update status
```

## Application Deployment State Diagram
```mermaid
stateDiagram-v2
  [*] --> CREATE_REQUESTED: create request
  [*] --> UPDATE_REQUESTED: update request
  CREATE_REQUESTED --> CREATING: lambda created, pending
  CREATING --> ACTIVE: lambda is active
  CREATE_REQUESTED --> CREATE_FAILED: failure
  CREATE_FAILED --> CREATE_REQUESTED: retry
  ACTIVE --> UPDATE_REQUESTED: update request
  UPDATE_REQUESTED --> UPDATING: lambda updated, pending
  UPDATING --> ACTIVE: lambda is active
  UPDATE_REQUESTED --> UPDATE_FAILED: failure
  UPDATE_FAILED --> UPDATE_REQUESTED: retry
  ACTIVE --> DELETE_REQUESTED: delete request
  DELETE_REQUESTED --> DELETED: success
  DELETE_REQUESTED --> DELETE_FAILED: failure
  DELETE_FAILED --> DELETE_REQUESTED: retry
```

## Requirements

- Java 17
- Docker (required for local infrastructure)

## Tech Stack

- Kotlin
- Spring Boot
- PostgreSQL
- Kafka
- Flyway
- LocalStack (Lambda, S3)

## Run with Local Profile

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Infrastructure will be created automatically. Port ``8080``.

## Local Infrastructure

The local environment uses Docker containers for:

- PostgreSQL
- Kafka
- LocalStack
- Flyway (DB migrations)

Automatically created:

- S3 Bucket ``deployments``
- PostgreSQL DB ``dm``
- topic ``dm-deployment-topic-local``.

### LocalStack Init Scripts

LocalStack init scripts are located in:

```
init-functions
```

### Database Migrations

Flyway migrations are located in:

```
src/main/resources/db/migration
```

## Metrics

Prometheus endpoint ``localhost:8080/actuator/prometheus``

- Kafka metrics start with ``kafka_consumer_``

- AWS metrics start with ``aws_sdk_lambda_`` and ``aws_sdk_s3_``:
  - ``aws_sdk_lambda_ApiCallDuration_seconds_max``
  - ``aws_sdk_s3_ApiCallDuration_seconds_max``
  - ...

- HTTP key metrics:
  - ``http_server_requests_seconds_max``
  - ``http_server_requests_seconds_count``
  - ``http_server_requests_seconds_sum``
  - ...

- Application key metrics:
  - ``application_ready_time_seconds``
  - ``jvm_memory_used_bytes``
  - ``system_cpu_usage``
  - ``jvm_gc_pause_seconds_max``
  - ``jvm_gc_pause_seconds_count``
  - ``jvm_gc_pause_seconds_sum``
  - ``tomcat_sessions_active_current_sessions``
  - ...

- DB related metrics:
  - ``spring_data_repository_invocations_seconds_max``
  - ``spring_data_repository_invocations_seconds_count``
  - ``spring_data_repository_invocations_seconds_sum``
  - ``hikaricp_connections_active``
  - ``hikaricp_connections_creation_seconds_max``
  - ...
