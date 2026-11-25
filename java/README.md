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

## Call the API

Open [openapi.yaml](openapi.yaml) in Swagger Editor to see the two operations or import it into an OpenAPI client. Use the local server `http://localhost:8080`. From a terminal, send the requests described by the contract with `curl`. In Windows PowerShell, use `curl.exe`.

```sh
curl --include http://localhost:8080/time-deposits
curl --include --request POST http://localhost:8080/time-deposits/interest
curl --include http://localhost:8080/time-deposits
```

The GET returns `200` with a JSON array. Each withdrawal contains `id`, `amount`, and `date`. Its parent ID is the enclosing deposit's `id`. Empty deposits and withdrawals are represented by empty arrays. The POST accepts an empty request and returns `204` with an empty response. Each successful POST applies another month's interest to the current balances. Repeating the request can therefore change balances again.

## Persistence design

The migration maps `timeDeposits`, `planType`, and `timeDepositId` to PostgreSQL's unquoted snake_case identifiers `time_deposits`, `plan_type`, and `time_deposit_id`. Both monetary columns use `NUMERIC` with no declared precision or scale. The assignment leaves monetary limits unspecified, so the database preserves fractional digits within PostgreSQL's numeric limits. Java uses `BigDecimal` for money and `LocalDate` for withdrawal dates.

The JDBC adapter reads deposits and withdrawals in an ordered `LEFT JOIN`, including deposits with empty withdrawal lists. The withdrawal foreign-key index supports lookup by deposit. The adapter writes balances by deposit ID with a parameterized batch. The stored balance is the principal and withdrawals are read as separate records.

The application service holds one `READ COMMITTED` write transaction while it locks deposits in ID order through `findAllForBalanceUpdate`, calculates interest, and calls `updateBalances`. Concurrent updates wait for the locks before reading balances and calculating interest. A failure rolls back the batch. The lock applies to rows visible to the locking query, so an insert after the query starts may first be included on the next run.

The application converts the principal to `double` for the existing `TimeDepositInterest` function and adds its rounded decimal result to the original `BigDecimal`. This preserves the stored fractional principal and the legacy binary rounding, including the `0.01` Student credit on `6.00`. Finite inputs have the precision limits of `double` for the interest calculation. A principal whose conversion is non-finite fails the batch before any writes. Values small enough to convert to zero earn no cent-sized credit and retain their stored precision.

## Run tests and view coverage

`mvn -f java/pom.xml clean verify` runs the domain, repository, and HTTP/application tests through Surefire. Testcontainers starts PostgreSQL `17.12`, the same image as Compose, and Spring Boot applies the production migrations to each test database. The integration tests require Docker. The JaCoCo report is generated at `java/target/site/jacoco/index.html`.

## AI-assisted development

The Phase 4 prompt asked for a “Spring Boot Foundation, PostgreSQL Database Schema and Persistence Adapter” while preserving the Java domain and deferring the application use cases and HTTP endpoints. Codex used that prompt, the root assignment README, and `AGENTS.md` to implement and verify the Java changes. The build configuration here and the Maven, Docker Compose, and Testcontainers commands above reproduce the verification. The schema and locking choices are recorded in the persistence section.

The Phase 5 prompt asked for “Application Use Cases, Transactional Interest Updates, REST API and OpenAPI Contract” while preserving legacy rounding and adding exactly two business operations. Codex used the prompt and repository instructions to implement the application service, HTTP adapter, and static contract, then exercised the API against PostgreSQL. The commands above reproduce those checks.
