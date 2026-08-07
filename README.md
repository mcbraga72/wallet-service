# Wallet Service

A production-ready wallet service that manages user balances with support for deposits, withdrawals, and transfers.

This project was designed with a strong focus on **correctness, consistency, and observability**, following patterns commonly used in financial systems.

---

# Features

* Create wallet for a user
* Retrieve current balance
* Retrieve historical balance
* Deposit funds
* Withdraw funds (with validation)
* Transfer funds between wallets
* Idempotent operations (safe retries)
* Concurrency-safe operations
* Full audit trail (ledger-based)

---

# Architecture Overview

The service follows a **ledger-based design**:

* Balances are **not stored directly**
* Instead, they are derived from a list of immutable transactions

```text
+100 (deposit)
-50  (withdraw)
+20  (transfer)
--------------
= 70 (computed balance)
```

### Benefits

* Full auditability
* No risk of silent data corruption
* Easier debugging and reconciliation

---

# Data Model

## Wallets

| Column     | Description         					                             |
|------------|-------------------------------------------------------|
| id         | Internal primary key (used for joins and performance) |
| public_id  | The unique public identifier of the wallet, used for API requests. |
| user_id    | Owner of the wallet                                   |
| created_at | Creation timestamp                                    |

---

## Transactions (Ledger)

| Column       | Description                                                                |
|--------------|----------------------------------------------------------------------------|
| id           | Internal primary key                                                       |
| public_id    | The unique public identifier of the transaction, used for API requests |
| wallet_id    | Associated wallet                                                          |
| amount       | Positive (credit) or negative (debit)                                      |
| type         | DEPOSIT / WITHDRAW / TRANSFER                                              |
| status       | PROCESSING / SUCCESS / FAILED                                              |
| reference_id | Links transfer operations                                                  |
| created_at   | Timestamp                                                                  |

---

## Idempotency Keys

| Column          | Description                                                                        |
|-----------------|------------------------------------------------------------------------------------|
| id              | Internal primary key                                                                 |
| public_id    | The unique public identifier of the user |
| idempotency_key | A unique client-provided string used to identify and deduplicate retried requests. |
| response        | Stores the cached API response to be replayed if the same key is sent again.       |
| status          | PROCESSING / SUCCESS / FAILED                                                      |
| created_at      | Timestamp                                                                          |

---

# Concurrency & Consistency

Financial operations require strict correctness.

The system ensures consistency using:

### 1. Pessimistic Locking

```sql
SELECT ... FOR UPDATE
```

* Prevents concurrent modifications
* Guarantees no negative balances

---

### 2. Deadlock Prevention

Wallets are locked in a **consistent order (by ID)**:

```text
Always lock smaller ID → then larger ID
```

This eliminates circular wait conditions.

---

### 3. Transaction Management

* All operations are wrapped in `@Transactional`
* Uses default isolation level: **READ COMMITTED**

---

# Idempotency

To safely handle retries:

* A unique key is required per request (`Idempotency-Key` header)
* Stored in a dedicated table with a **unique constraint**

### Strategy

```text
Insert key → if duplicate → reject request
```

This guarantees **exactly-once execution** under concurrency.

---

# Observability

The service includes observability features for health checks, metrics, and distributed tracing.

### Health & Metrics

- `/actuator/health` — application health status
- `/actuator/metrics` — application metrics
- `/actuator/prometheus` — Prometheus-formatted metrics

### Monitoring

- **Prometheus** — collects and stores application metrics
- **Grafana** — provides dashboards for visualizing metrics

### Local Monitoring & Tracing

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Zipkin: `http://localhost:9411`

### Distributed Tracing

* Trace ID and Span ID generated per request
* Integrated with **Zipkin**

---

### Log Correlation

Logs include tracing information:

```text
INFO [traceId=abc123 spanId=xyz789] Transfer executed
```

---

# Testing Strategy

The project includes a comprehensive test suite:

### 1. Unit Tests

* Service layer
* Business rules validation
* Fast and isolated

---

### 2. Integration Tests

* Full stack (Controller → DB)
* Uses real **PostgreSQL via Testcontainers**
* Validates transactions and persistence

---

### 3. Concurrency Tests

Simulates simultaneous operations:

```text
2 concurrent withdrawals from same wallet
```

Ensures:

* No race conditions
* No negative balances

---


Testing Strategy

The project includes a comprehensive test suite:

1. Unit Tests
    - Validate business logic in isolation (WalletService)
    - Fast and deterministic

2. Integration Tests
    - Use PostgreSQL via Testcontainers
    - Validate real database behavior and transactions

3. Concurrency Tests
    - Simulate simultaneous withdrawals
    - Ensure pessimistic locking prevents race conditions

This approach guarantees correctness, consistency, and reliability for financial operations.



# Running the Project

## Prerequisites

* Java 21+
* Docker

---

## Start Dependencies

```bash
docker-compose up -d
```

---

## Services

* PostgreSQL → `localhost:5432`
* Zipkin → http://localhost:9411

---

## Run Application

```bash
./mvnw spring-boot:run
```

---

# API Endpoints

## Create Wallet

```http
POST /wallets
```

---

## Get Balance

```http
GET /wallets/{walletPublicId}/balance
```

---

## Deposit

```http
POST /wallets/{walletPublicId}/deposit
```
Header:

```text
Idempotency-Key: unique-key
```

---

## Withdraw

```http
POST /wallets/{walletPublicId}/withdraw
```
Header:

```text
Idempotency-Key: unique-key
```

---

## Transfer

```http
POST /wallets/transfer
```

Header:

```text
Idempotency-Key: unique-key
```

---

# Design Decisions

### Ledger-Based Model

Chosen for:

* Auditability
* Reliability
* Industry alignment

---

### Pessimistic Locking

Chosen to:

* Prevent race conditions
* Guarantee financial correctness

---

### READ COMMITTED Isolation

Used with explicit locking to balance:

* Performance
* Consistency

---

### Testcontainers Instead of H2

Ensures:

* Real database behavior
* Accurate transaction and locking semantics

---

# API Documentation

* API documentation is provided via Swagger UI at:

```text
http://localhost:8080/swagger-ui.html
```

---

# Trade-offs

Due to time constraints:

* No authentication layer
* No rate limiting
* No distributed cache (e.g., Redis)
* Simplified idempotency

---

# Financial Data Strategy: Why NUMERIC(19, 2)?

For this project, I chose to use the NUMERIC(19, 2) type (mapped to java.math.BigDecimal) rather than storing subunits (cents) as BIGINT.

* Precision: Unlike floating-point types, NUMERIC ensures exact decimal representation, which is non-negotiable for financial calculations.
* Auditability: Storing values as 50.00 instead of 5000 makes the database directly readable for developers and stakeholders without requiring mental conversion.
* Scale: While integer math is faster, the performance gains of BIGINT are rarely the bottleneck in standard wallet operations. NUMERIC provides a more flexible foundation for future requirements, such as interest calculations or multi-currency support where decimal places vary.
* Data Integrity: Combined with a CHECK (amount <> 0) constraint, it prevents "zero amounts" in transactions.

---

# UUIDs for Public Identifiers

* We use UUID v4 for public identifiers to avoid exposing internal database IDs.
  In high-scale systems, UUID v7 could be used to improve index locality and write performance,
  especially if UUIDs were used as primary keys.

---

# Idempotency Cleanup

* In a production environment, idempotency records should be periodically cleaned to prevent unbounded growth.
* A background job (e.g., scheduled task or cron job) would remove records older than a defined TTL (e.g., 24 hours), ensuring system performance and storage efficiency.

---

# Time Spent

Approximately **12 hours**.

---

# Future Improvements

* Add authentication/authorization
* Introduce caching layer
* Add Prometheus + Grafana for metrics
* Improve idempotency
* Add rate limiting

---

# Final Notes

This project was designed to reflect **real-world backend engineering practices**, focusing on:

* Data integrity
* Concurrency safety
* Observability
* Clean architecture

---

# Future Evolution (Production Considerations)

In a real-world scenario, this service could evolve into a distributed architecture.

Possible improvements include:

* Splitting into multiple services (e.g., Wallet, User, Transaction)
* Introducing asynchronous communication using an event streaming platform such as Kafka or RabbitMQ
* Adding an API Gateway for routing and cross-cutting concerns
* Using a distributed cache (e.g., Redis) to improve read performance
* Implementing service discovery for dynamic scaling
* Implementing distributed tracing with OpenTelemetry for better observability across services

These were intentionally omitted to keep the solution focused and avoid unnecessary