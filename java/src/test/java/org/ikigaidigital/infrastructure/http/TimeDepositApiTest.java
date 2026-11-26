package org.ikigaidigital.infrastructure.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ikigaidigital.TimeDepositApplication;
import org.ikigaidigital.application.TimeDepositService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the HTTP contract and application transactions against a real PostgreSQL database. */
@Testcontainers
class TimeDepositApiTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.12");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static ServletWebServerApplicationContext application;
    private static URI baseUrl;
    private static JdbcTemplate jdbc;
    private static ObjectReader json;

    /** Starts the real server on an ephemeral loopback port and configures exact decimal JSON assertions. */
    @BeforeAll
    static void startApplication() {
        application = (ServletWebServerApplicationContext) new SpringApplicationBuilder(TimeDepositApplication.class)
                .run("--server.port=0", "--server.address=127.0.0.1",
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
        baseUrl = URI.create("http://127.0.0.1:" + application.getWebServer().getPort());
        jdbc = application.getBean(JdbcTemplate.class);
        json = application.getBean(ObjectMapper.class).reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /** Closes the server and connection pool when the suite finishes. */
    @AfterAll
    static void stopApplication() {
        if (application != null) {
            application.close();
        }
    }

    /** Clears both tables so each case starts with its own committed fixture. */
    @BeforeEach
    void clearDatabase() {
        jdbc.execute("TRUNCATE TABLE withdrawals, time_deposits");
    }

    /** GET returns an empty array and POST succeeds with no content when there are no deposits. */
    @Test
    void listsAndUpdatesAnEmptyDatabase() throws IOException, InterruptedException {
        JsonNode emptyArray = json.readTree("[]");
        assertThat(getDeposits()).isEqualTo(emptyArray);
        applyInterest();
        assertThat(getDeposits()).isEqualTo(emptyArray);
    }

    /**
     * Compares the full JSON response with the contract, including ID order, nested
     * associations, an empty withdrawal list, and ISO dates. High-precision amounts
     * expose accidental conversions to floating point during mapping or serialization.
     */
    @Test
    void returnsTheFullDepositAndWithdrawalContractWithExactDecimalsAndIsoDates()
            throws IOException, InterruptedException {
        insertDeposit(3, "premium", 46, "100.001");
        insertDeposit(2, "student", 365, "12345678901234567890.123456789012");
        insertDeposit(1, "basic", 31, "10.00");
        insertWithdrawal(30, 3, "3.101", "2026-11-01");
        insertWithdrawal(20, 2, "0.012345678901234567891", "2024-02-29");
        insertWithdrawal(10, 3, "12.50", "2026-11-12");

        assertThat(getDeposits()).isEqualTo(json.readTree("""
                [
                  {"id":1,"planType":"basic","balance":10.00,"days":31,"withdrawals":[]},
                  {"id":2,"planType":"student","balance":12345678901234567890.123456789012,"days":365,
                   "withdrawals":[{"id":20,"amount":0.012345678901234567891,"date":"2024-02-29"}]},
                  {"id":3,"planType":"premium","balance":100.001,"days":46,
                   "withdrawals":[{"id":10,"amount":12.50,"date":"2026-11-12"},
                                  {"id":30,"amount":3.101,"date":"2026-11-01"}]}
                ]
                """));
    }

    /**
     * Seeds each plan on both sides of an eligibility cutoff. Checks that only the
     * eligible balances change, including the Student half-cent case, and verifies
     * exact principal digits in PostgreSQL and the full follow-up GET response.
     */
    @Test
    void updatesOnlyEligibleBalancesAndReturnsThePersistedValuesOnGet() throws IOException, InterruptedException {
        insertDeposit(1, "basic", 30, "1200.125");
        insertDeposit(2, "basic", 31, "2400.123456789012");
        insertDeposit(3, "student", 365, "6.00");
        insertDeposit(4, "student", 366, "1800.125");
        insertDeposit(5, "premium", 45, "600.00");
        insertDeposit(6, "premium", 46, "4800.000000000123");
        insertWithdrawal(1, 2, "200.00", "2026-11-12");
        insertWithdrawal(2, 6, "250.125", "2026-11-13");
        JsonNode expected = getDeposits();

        applyInterest();

        assertThat(storedBalances()).containsExactly(new BigDecimal("1200.125"), new BigDecimal("2402.123456789012"),
                new BigDecimal("6.01"), new BigDecimal("1800.125"), new BigDecimal("600.00"),
                new BigDecimal("4820.000000000123"));
        ((ObjectNode) expected.get(1)).put("balance", new BigDecimal("2402.123456789012"));
        ((ObjectNode) expected.get(2)).put("balance", new BigDecimal("6.01"));
        ((ObjectNode) expected.get(5)).put("balance", new BigDecimal("4820.000000000123"));
        assertThat(getDeposits()).isEqualTo(expected);
    }

    /** Two successful POSTs persist 4,820.00 then 4,840.08, confirming that repeated calls compound. */
    @Test
    void eachSuccessfulPostAppliesInterestToTheLatestBalance() throws IOException, InterruptedException {
        insertDeposit(1, "premium", 46, "4800.00");

        applyInterest();
        assertThat(storedBalances()).containsExactly(new BigDecimal("4820.00"));

        applyInterest();
        assertThat(storedBalances()).containsExactly(new BigDecimal("4840.08"));
    }

    /**
     * Uses an independent PostgreSQL transaction to hold a row lock until both POSTs
     * are observed waiting. Both must return 204, and the final response must show
     * compounded interest with the same metadata and withdrawal. This catches a lost
     * credit when concurrent requests calculate from the same starting principal.
     */
    @Test
    void overlappingPostsEachApplyInterestToTheLatestCommittedBalance() throws Exception {
        int depositId = 1;
        insertDeposit(depositId, "premium", 46, "1200.125");
        insertWithdrawal(10, depositId, "25.125", "2026-11-12");
        var dataSource = application.getBean(DataSource.class);
        var requests = new ArrayList<CompletableFuture<HttpResponse<String>>>(2);

        try (var gate = dataSource.getConnection()) {
            gate.setAutoCommit(false);
            try {
                try (var lock = gate.prepareStatement("SELECT id FROM time_deposits WHERE id = ? FOR UPDATE")) {
                    lock.setInt(1, depositId);
                    lock.setQueryTimeout(5);
                    try (var row = lock.executeQuery()) {
                        assertThat(row.next()).isTrue();
                    }
                }

                requests.add(HTTP.sendAsync(interestRequest(), HttpResponse.BodyHandlers.ofString()));
                requests.add(HTTP.sendAsync(interestRequest(), HttpResponse.BodyHandlers.ofString()));
                awaitBothInterestQueriesWaitingOnLocks(dataSource);
            } finally {
                // Release the row lock even if observing the two waiting requests fails.
                gate.rollback();
            }

            for (var request : requests) {
                assertNoContent(request.get(10, TimeUnit.SECONDS));
            }
        } finally {
            // Cancel outstanding requests if a response assertion or wait failed.
            requests.forEach(request -> request.cancel(true));
        }

        // 1200.125 earns 5.00 at 5% / 12. The new principal of 1205.125 earns 5.02.
        assertThat(storedBalances()).containsExactly(new BigDecimal("1210.145"));
        assertThat(getDeposits()).isEqualTo(json.readTree("""
                [{"id":1,"planType":"premium","balance":1210.145,"days":46,
                  "withdrawals":[{"id":10,"amount":25.125,"date":"2026-11-12"}]}]
                """));
    }

    /**
     * Rejects the second update only after a trigger observes the first new balance.
     * Both rows must retain their original values after the service fails, proving
     * rollback of a write already executed in the batch.
     */
    @Test
    void rollsBackAnEarlierBalanceWriteWhenTheNextWriteFails() {
        insertDeposit(1, "basic", 31, "1200.00");
        insertDeposit(2, "premium", 46, "4800.00");
        jdbc.execute("""
                CREATE FUNCTION reject_second_interest_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF (SELECT balance FROM time_deposits WHERE id = 1) = 1201.00 THEN
                        RAISE EXCEPTION 'Rejected after the earlier balance was written' USING ERRCODE = '23514';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_second_interest_update BEFORE UPDATE OF balance ON time_deposits
                FOR EACH ROW WHEN (OLD.id = 2) EXECUTE FUNCTION reject_second_interest_update()
                """);

        try {
            assertThatThrownBy(application.getBean(TimeDepositService.class)::applyMonthlyInterest)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("Rejected after the earlier balance was written");
            assertThat(storedBalances()).containsExactly(new BigDecimal("1200.00"), new BigDecimal("4800.00"));
        } finally {
            // Remove temporary schema objects so subsequent cases can update balances normally.
            jdbc.execute("DROP TRIGGER reject_second_interest_update ON time_deposits");
            jdbc.execute("DROP FUNCTION reject_second_interest_update()");
        }
    }

    /**
     * Stores an amount PostgreSQL can represent that converts to an infinite double.
     * The service must reject it and leave both balances unchanged, including the
     * earlier eligible deposit whose credit can be calculated.
     */
    @Test
    void rejectsABalanceOutsideTheLegacyDoubleRangeWithoutPersistingOtherCredits() {
        insertDeposit(1, "basic", 31, "1200.00");
        insertDeposit(2, "student", 365, "1e309");
        List<BigDecimal> before = storedBalances();

        assertThatThrownBy(application.getBean(TimeDepositService.class)::applyMonthlyInterest)
                .isInstanceOf(ArithmeticException.class)
                .hasMessageContaining("outside the legacy interest range for deposit 2");
        assertThat(storedBalances()).isEqualTo(before);
    }

    /**
     * Checks the HTTP status and media type on each GET before comparing its JSON content.
     *
     * @return response body parsed with decimal precision
     * @throws IOException if the request or JSON read fails
     * @throws InterruptedException if the request is interrupted
     */
    private JsonNode getDeposits() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(baseUrl.resolve("/time-deposits"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("application/json"));
        return json.readTree(response.body());
    }

    /**
     * Polls PostgreSQL from a separate connection for both active locking queries.
     * Observing actual lock waits establishes overlap before the test releases its row lock.
     *
     * @param dataSource source of observer connections to the test database
     * @throws InterruptedException if waiting for the observation is interrupted
     * @throws ExecutionException if the polling query fails
     * @throws AssertionError if both waiters are never observed within the deadline
     */
    private static void awaitBothInterestQueriesWaitingOnLocks(DataSource dataSource)
            throws InterruptedException, ExecutionException {
        var observer = new JdbcTemplate(dataSource);
        observer.setQueryTimeout(3);
        var bothWaiting = new CompletableFuture<Void>();
        var poller = Executors.newSingleThreadScheduledExecutor();
        try {
            poller.scheduleWithFixedDelay(() -> {
                try {
                    Integer blockedQueries = observer.queryForObject("""
                            SELECT count(*) FROM pg_stat_activity
                            WHERE datname = current_database()
                              AND pid <> pg_backend_pid()
                              AND state = 'active'
                              AND wait_event_type = 'Lock'
                              AND query LIKE '%FROM time_deposits%'
                              AND query LIKE '%FOR UPDATE%'
                            """, Integer.class);
                    if (Integer.valueOf(2).equals(blockedQueries)) {
                        bothWaiting.complete(null);
                    }
                } catch (RuntimeException failure) {
                    bothWaiting.completeExceptionally(failure);
                }
            }, 0, 25, TimeUnit.MILLISECONDS);
            bothWaiting.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new AssertionError("Expected both POST transactions to wait on PostgreSQL locks", timeout);
        } finally {
            poller.shutdownNow();
        }
    }

    /** {@return the bodyless POST used for both sequential and overlapping requests} */
    private static HttpRequest interestRequest() {
        return HttpRequest.newBuilder(baseUrl.resolve("/time-deposits/interest"))
                .timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.noBody()).build();
    }

    /** Sends a synchronous POST and verifies the success response contract. */
    private void applyInterest() throws IOException, InterruptedException {
        assertNoContent(HTTP.send(interestRequest(), HttpResponse.BodyHandlers.ofString()));
    }

    /** Checks that a successful POST returns both the 204 status and an empty body. */
    private static void assertNoContent(HttpResponse<String> response) {
        assertThat(response.statusCode()).as("POST status with body %s", response.body()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
    }

    /** Seeds a deposit directly and parses the principal from decimal text to preserve fixture precision. */
    private void insertDeposit(int id, String planType, int days, String balance) {
        jdbc.update("INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (?, ?, ?, ?)",
                id, planType, days, new BigDecimal(balance));
    }

    /** Seeds a withdrawal for a specific deposit with a decimal amount and a calendar date. */
    private void insertWithdrawal(int id, int depositId, String amount, String date) {
        jdbc.update("INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES (?, ?, ?, ?)",
                id, depositId, new BigDecimal(amount), LocalDate.parse(date));
    }

    /** {@return persisted balances in deposit ID order for checks independent of the HTTP mapping} */
    private List<BigDecimal> storedBalances() {
        return jdbc.queryForList("SELECT balance FROM time_deposits ORDER BY id", BigDecimal.class);
    }
}
