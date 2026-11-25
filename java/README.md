# Java time deposit application

The Java module uses Java 17, Maven, Spring Boot, Spring JDBC, Flyway, and PostgreSQL. Run the commands below from the repository root. Docker must be running for the PostgreSQL integration tests.

## Start locally

```sh
docker compose -f java/compose.yaml up -d --wait
mvn -f java/pom.xml clean verify
java -jar java/target/time-deposit-kata-1.0-SNAPSHOT.jar
```

The application starts on port `8080` by default and runs Flyway migrations at startup. The Compose database binds to `127.0.0.1:5432` and persists its data in a named volume. Set `TIME_DEPOSIT_DB_PORT` before starting Compose and the application to use a different host port. The checked-in database name and user `time_deposit` and password `time_deposit_dev` are for local development. For another environment, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

To stop the local database and keep its data, run `docker compose -f java/compose.yaml stop`.

## Load optional sample data

Run this once against the local development database after the application has applied the Flyway migration. The sample inserts six deposits across all three plans and three withdrawals. It uses fixed IDs and runs in one transaction.

```sh
docker compose -f java/compose.yaml exec -T postgres psql -U time_deposit -d time_deposit -v ON_ERROR_STOP=1 < java/db/sample-data.sql
```

In PowerShell, use this command.

```powershell
Get-Content -Raw -LiteralPath java/db/sample-data.sql |
    docker compose -f java/compose.yaml exec -T postgres psql -U time_deposit -d time_deposit -v ON_ERROR_STOP=1
```

## Persistence design

The migration maps `timeDeposits`, `planType`, and `timeDepositId` to PostgreSQL's unquoted snake_case identifiers `time_deposits`, `plan_type`, and `time_deposit_id`. Both monetary columns use `NUMERIC` with no declared precision or scale. The assignment leaves monetary limits unspecified, so the database preserves fractional digits within PostgreSQL's numeric limits. Java uses `BigDecimal` for money and `LocalDate` for withdrawal dates.

The JDBC adapter reads deposits and withdrawals in an ordered `LEFT JOIN`, including deposits with empty withdrawal lists. The withdrawal foreign-key index supports lookup by deposit. The adapter writes balances by deposit ID with a parameterized batch. The stored balance is the principal and withdrawals are read as separate records.

The future bulk-update caller must use one write transaction at PostgreSQL's default `READ COMMITTED` isolation. It should lock deposits in ID order through `findAllForBalanceUpdate`, calculate interest through the domain logic, then call `updateBalances` in that same transaction. Concurrent updates following this protocol wait for the locks and read the committed balances before calculating. An error in the transaction rolls the batch back. The lock applies to rows visible to the locking query, so an insert after the query starts may first be included on the next run.

## Run tests and view coverage

`mvn -f java/pom.xml clean verify` runs the existing domain tests and the repository integration tests through Surefire. Testcontainers starts PostgreSQL `17.12`, the same image as Compose, and Spring Boot applies the production migrations to that test database. The integration tests require Docker. The JaCoCo report is generated at `java/target/site/jacoco/index.html`.

## AI-assisted development

The Phase 4 prompt asked for a “Spring Boot Foundation, PostgreSQL Database Schema and Persistence Adapter” while preserving the Java domain and deferring the application use cases and HTTP endpoints. Codex used that prompt, the root assignment README, and `AGENTS.md` to implement and verify the Java changes. The build configuration here and the Maven, Docker Compose, and Testcontainers commands above reproduce the verification. The schema and locking choices are recorded in the persistence section.
