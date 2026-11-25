# AI-assisted development workflow

This record uses the development conversation, [AGENTS.md](../AGENTS.md), local Git history, and command results. The prompts quoted below are verbatim excerpts from the phase briefs. Each phase heading and description summarizes the rest of its brief. The separate ChatGPT planning conversation was unavailable for independent verification.

## Tools and configuration

- The candidate reports using ChatGPT for planning, technical discussion, preparing prompts, and reviewing reported results.
- The OpenAI Codex VS Code extension worked in the local repository. Codex inspected the source and history, implemented the phased changes, ran tests, diagnosed failures, and reported results.
- The verified local tools were Eclipse Adoptium JDK 17.0.21.1, Maven 3.9.16, Git, Windows PowerShell, Docker Engine 29.9.3, and Docker Compose 5.7.1. Outside the Maven build, JShell checked legacy arithmetic, curl exercised HTTP, Python compared JSON decimals in smoke checks, and Redocly CLI validated the static OpenAPI file.
- The session used a workspace-write sandbox and the checked-in `AGENTS.md` instructions. When the sandbox could not start a shell process, Codex requested escalation for specific commands, including repository inspection and verification with Maven or Docker. The broader approval policy settings are unverified.
- The exact model identifier, reasoning effort setting, VS Code extension version, and any private ChatGPT configuration could not be verified.

## Development phases

### 1. Requirements and existing code analysis

> Analyze the complete Java implementation before making any changes.

Codex read the original assignment, the Java module, and its test. The original suite ran **1 passing test**, whose assertion did not check the resulting balance. The analysis identified the annual rates divided by 12, the eligibility cutoffs, and the Student `6.00` half-cent case. Codex proposed a lightweight HttpServer and H2 implementation as one option. The phase left a report and made no source changes.

### 2. Characterization and boundary tests

> Establish a reliable test suite that captures the existing TimeDepositCalculator behaviour before any production code refactoring.

The candidate selected Spring Boot, Spring JDBC, PostgreSQL, Flyway, static OpenAPI, JUnit, AssertJ, Testcontainers, and JaCoCo for the later work. Codex added explicit balance assertions for eligibility, batch behavior, and rounding, and configured JaCoCo while preserving the production classes. `clean verify` passed **29 tests** with **27/27 lines and 14/14 branches** covered. Git records `ca62c2b`.

### 3. Domain refactoring

> First inspect the original calculator and identify the smallest useful refactoring.

Codex extracted `TimeDepositInterest` and its immutable rule table. The existing calculator delegates to it and keeps its public contract. An additional test checks the interest result and the unchanged deposit. `clean verify` passed **30 tests**, with **32/32 lines and 12/12 branches** covered. A separate JShell comparison checked **23,040 updated balances bit for bit** against the original. Git records `2a07ebd`.

### 4. Spring Boot foundation and PostgreSQL persistence

> Do not introduce custom REST endpoints during this phase.

Codex added the Boot setup, Flyway migration, framework-independent stored models and repository port, JDBC adapter, Compose configuration, and optional fixtures. PostgreSQL tests checked constraints, mapping, batch rollback, and row locks. `clean verify` passed **45 tests**, including **15 PostgreSQL cases**. JaCoCo reported **62/64 lines and 16/16 branches**. The packaged JAR started against an isolated Compose database and the optional script loaded six deposits and three withdrawals. Git records `1e22533`, `eecd7f5`, and `11764d5`.

### 5. Application services, REST API, and OpenAPI

> Reuse the existing implementation. Do not redesign completed components without a demonstrated correctness issue.

Codex implemented the transactional application service, two HTTP operations, and static contract. The service adds the rounded credit to the stored decimal principal. Tests exercised the HTTP contract and forced a database failure after the first write to verify whole-batch rollback. `clean verify` passed **51 tests**, with **93/95 lines and 24/24 branches** covered. Redocly passed. The local smoke check was rerun with decimal comparisons after the initial check compared formatted strings. Git records `cd6c6be` and `d3d02a0`.

### 6. Concurrent HTTP and integration verification

> Ensure the test genuinely exercises overlapping requests and transaction contention rather than merely executing two sequential calls.

Codex added a test that holds a PostgreSQL row lock, observes two POST transactions waiting, then releases the lock. The test confirmed both requests succeeded, compounded to `1210.145`, and preserved deposit metadata and the withdrawal. It passed five focused runs and the full suite. `clean verify` passed **52 tests**, with **93/95 lines and 24/24 branches** covered. Git records `e6af6b8`.

### 7. Source, architecture, and security review

> Do not assume that the implementation is correct simply because previous phases passed.

Codex reread the Java changes against the original baseline. The shipped app had listened on all interfaces and a tracked IntelliJ module still declared Java 8. The default HTTP bind address became loopback and the obsolete module was removed. The rebuilt JAR and API commands were verified separately. An independent JShell comparison matched **8,320 balance calculations bit for bit**, including extreme finite inputs. `clean verify` passed **52 tests**, with **93/95 lines and 24/24 branches** covered. Git records `95b8cb2` and `d164a6e`.

### 8. Documentation and submission preparation

> The functional implementation is complete. Do not introduce new features or redesign working components.

Codex prepared the final Java README and this workflow record, repeated the build and OpenAPI validation, and checked the public fork's metadata without publishing changes. `clean verify` passed **52 tests**, with **93/95 lines and 24/24 branches** covered. The phase stops at the documentation diff for candidate approval.

## Important decisions

- The candidate selected Spring Boot and PostgreSQL after the initial HttpServer and H2 proposal. Boot supplied the HTTP and transaction wiring. PostgreSQL supplied decimal storage and row locking, and the same engine could be tested through Testcontainers.
- Spring JDBC kept the join, locking read, and batch writes visible in SQL. This use case has two tables and explicit read and write paths.
- An immutable rule table represents the three plans because they share one formula and vary by rate and eligibility. The legacy arithmetic and rounding were preserved to honor compatibility. The application uses the rounded interest from that same calculation and adds it to the original `BigDecimal` principal.
- One transaction spans locking, calculation, and persistence. `SELECT FOR UPDATE` in ID order lets each competing request calculate on the committed balance and keeps a failing batch atomic.
- The assignment limits the API to two business operations. Static OpenAPI documents those operations for Swagger-compatible tools. PostgreSQL integration tests exercise the migration and transaction semantics used locally.
- The local API binds to loopback because the assessment endpoints are unauthenticated. The packaged-JAR check verified the listener after the configuration change.

## Human oversight

The candidate chose the implementation stack and explicitly approved advancement after Phases 1 through 7. The briefs restricted each phase and reserved commits, pushes, and submission for separate approval. Codex produced the source edits, command executions, and technical reports recorded above. The approvals establish that the candidate reviewed and approved the reported phases. The extent of any separate manual source review or independent test runs was not recorded. The listed Git commits are evidence of repository history, and the previously reported runs completed with zero failures, errors, or skips.

## Reproduce the workflow

1. Work in a personal checkout of the assignment fork. Give the assistant the original README and [AGENTS.md](../AGENTS.md). Begin with read-only requirements and baseline analysis.
2. Use the phase excerpts and summaries above as a starting point. In each prompt, specify the permitted files, compatibility constraints, required verification, and where the assistant should stop.
3. Review the reported evidence and the Git diff before approving the next phase. Commit changes in small steps after approval. Build and test with `mvn -f java/pom.xml clean verify`, then follow the [local setup and API instructions](README.md).

The workflow reproduces the sequence and verification gates. Generated text and code can vary between runs.
