package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.TimeDepositApplication;
import org.ikigaidigital.application.port.TimeDepositRepository;
import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;
import org.ikigaidigital.domain.Withdrawal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the migrated PostgreSQL schema and JDBC adapter through the repository port. */
@Testcontainers
class JdbcTimeDepositRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.12");

    private static ConfigurableApplicationContext application;
    private static JdbcTemplate jdbc;
    private static TimeDepositRepository repository;
    private static TransactionTemplate transaction;

    /** Boots a non-web context so the real Flyway migration and transactional repository proxy are exercised. */
    @BeforeAll
    static void startApplication() {
        application = new SpringApplicationBuilder(TimeDepositApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
        jdbc = application.getBean(JdbcTemplate.class);
        repository = application.getBean(TimeDepositRepository.class);
        transaction = new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
    }

    /** Releases the Spring context and its connection pool after the repository tests. */
    @AfterAll
    static void stopApplication() {
        if (application != null) {
            application.close();
        }
    }

    /** Isolates each fixture while keeping the migrated schema in place. */
    @BeforeEach
    void clearDatabase() {
        jdbc.execute("TRUNCATE TABLE withdrawals, time_deposits");
    }

    /** An empty database yields an empty collection that the application can list directly. */
    @Test
    void returnsAnEmptyListWhenNoDepositsExist() {
        assertThat(repository.findAll()).isEmpty();
    }

    /**
     * Interleaves deposit and withdrawal inserts to verify grouping by parent and ordering
     * by ID. The result must retain the deposit without withdrawals, precise decimals,
     * and the leap-day date.
     */
    @Test
    void returnsEachDepositWithItsOwnWithdrawalsInIdOrderAndPreservesDecimalsAndDates() {
        var basic = new StoredDeposit(1, "basic", 31, new BigDecimal("10.00"));
        var student = new StoredDeposit(2, "student", 365, new BigDecimal("12345678901234567890.123456789012"));
        var premium = new StoredDeposit(3, "premium", 46, new BigDecimal("100.001"));
        var studentWithdrawal = new Withdrawal(20, new BigDecimal("0.012345678901"), LocalDate.of(2024, 2, 29));
        var firstPremiumWithdrawal = new Withdrawal(10, new BigDecimal("12.50"), LocalDate.of(2026, 11, 12));
        var secondPremiumWithdrawal = new Withdrawal(30, new BigDecimal("3.101"), LocalDate.of(2026, 11, 1));
        insertDeposit(premium);
        insertDeposit(student);
        insertDeposit(basic);
        insertWithdrawal(premium.id(), secondPremiumWithdrawal);
        insertWithdrawal(student.id(), studentWithdrawal);
        insertWithdrawal(premium.id(), firstPremiumWithdrawal);

        assertThat(repository.findAll()).containsExactly(
                new DepositWithWithdrawals(basic, List.of()),
                new DepositWithWithdrawals(student, List.of(studentWithdrawal)),
                new DepositWithWithdrawals(premium, List.of(firstPremiumWithdrawal, secondPremiumWithdrawal)));
    }

    /**
     * Starts with valid rows and attempts one constraint violation per case. Exact
     * PostgreSQL SQL states distinguish duplicate IDs, required columns, and orphaned
     * withdrawals from unrelated SQL failures.
     *
     * @param sql update that violates one migrated constraint
     * @param sqlState expected PostgreSQL error code for that constraint
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            UPDATE time_deposits SET id = 1 WHERE id = 2           | 23505
            UPDATE time_deposits SET plan_type = NULL WHERE id = 1 | 23502
            UPDATE time_deposits SET days = NULL WHERE id = 1      | 23502
            UPDATE time_deposits SET balance = NULL WHERE id = 1   | 23502
            UPDATE withdrawals SET id = 10 WHERE id = 20          | 23505
            UPDATE withdrawals SET time_deposit_id = NULL         | 23502
            UPDATE withdrawals SET amount = NULL                  | 23502
            UPDATE withdrawals SET date = NULL                    | 23502
            UPDATE withdrawals SET time_deposit_id = 999          | 23503
            """)
    void migratedSchemaEnforcesRequiredDatabaseConstraints(String sql, String sqlState) {
        insertDeposit(new StoredDeposit(1, "basic", 31, new BigDecimal("10.00")));
        insertDeposit(new StoredDeposit(2, "student", 365, new BigDecimal("20.00")));
        insertWithdrawal(1, new Withdrawal(10, new BigDecimal("0.15"), LocalDate.of(2026, 11, 1)));
        insertWithdrawal(1, new Withdrawal(20, new BigDecimal("0.25"), LocalDate.of(2026, 11, 12)));

        assertThatThrownBy(() -> jdbc.execute(sql))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo(sqlState));
    }

    /**
     * Locks three deposits, updates two, and verifies committed values at their supplied
     * precision. The omitted balance and the existing withdrawal must both be retained.
     */
    @Test
    void commitsOnlyTheSuppliedBalancesInOneBatchAndPreservesTheirPrecision() {
        var basic = new StoredDeposit(1, "basic", 31, new BigDecimal("10.00"));
        var premium = new StoredDeposit(2, "premium", 46, new BigDecimal("20.00"));
        var student = new StoredDeposit(3, "student", 366, new BigDecimal("30.00"));
        var withdrawal = new Withdrawal(1, new BigDecimal("1.00"), LocalDate.of(2026, 11, 1));
        insertDeposit(student);
        insertDeposit(premium);
        insertDeposit(basic);
        insertWithdrawal(basic.id(), withdrawal);
        var basicBalance = new BigDecimal("10.015678901234");
        var premiumBalance = new BigDecimal("20.083");

        transaction.executeWithoutResult(status -> {
            assertThat(repository.findAllForBalanceUpdate()).containsExactly(basic, premium, student);
            repository.updateBalances(Map.of(basic.id(), basicBalance, premium.id(), premiumBalance));
        });

        assertThat(repository.findAll()).containsExactly(
                new DepositWithWithdrawals(new StoredDeposit(basic.id(), "basic", 31, basicBalance), List.of(withdrawal)),
                new DepositWithWithdrawals(new StoredDeposit(premium.id(), "premium", 46, premiumBalance), List.of()),
                new DepositWithWithdrawals(student, List.of()));
    }

    /**
     * Reads the new balance inside the transaction, then marks the transaction rollback-only.
     * A fresh read must find the original balance, detecting a write that committed independently.
     */
    @Test
    void rollsBackBalanceChangesWithTheCallersTransaction() {
        var deposit = new StoredDeposit(1, "basic", 31, new BigDecimal("10.00"));
        insertDeposit(deposit);
        var updatedBalance = new BigDecimal("10.01");

        transaction.executeWithoutResult(status -> {
            repository.findAllForBalanceUpdate();
            repository.updateBalances(Map.of(deposit.id(), updatedBalance));
            assertThat(repository.findAll().get(0).deposit().balance()).isEqualTo(updatedBalance);
            status.setRollbackOnly();
        });

        assertThat(repository.findAll()).containsExactly(new DepositWithWithdrawals(deposit, List.of()));
    }

    /** Both update operations reject calls without a transaction, keeping the lock and write coupled. */
    @Test
    void requiresAnExistingTransactionForBothBalanceUpdateOperations() {
        assertThatThrownBy(repository::findAllForBalanceUpdate).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> repository.updateBalances(Map.of(1, BigDecimal.ONE)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /**
     * Holds the row lock in one transaction and requires a competing transaction to
     * fail with PostgreSQL's lock timeout. After the holder commits its update, a fresh
     * locking read must see the new balance.
     */
    @Test
    void blocksACompetingLockUntilCommitThenExposesTheNewBalance() {
        var deposit = new StoredDeposit(1, "basic", 31, new BigDecimal("10.00"));
        insertDeposit(deposit);
        var updatedBalance = new BigDecimal("10.01");
        ExecutorService competitor = Executors.newSingleThreadExecutor();

        try {
            transaction.executeWithoutResult(status -> {
                repository.findAllForBalanceUpdate();
                // A separate thread opens a separate transaction while the first still holds the lock.
                Future<?> blockedRead = competitor.submit(() -> transaction.executeWithoutResult(other -> {
                    jdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    repository.findAllForBalanceUpdate();
                }));

                assertThatThrownBy(() -> blockedRead.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .rootCause()
                        .isInstanceOfSatisfying(SQLException.class,
                                cause -> assertThat(cause.getSQLState()).isEqualTo("55P03"));
                repository.updateBalances(Map.of(deposit.id(), updatedBalance));
            });

            List<StoredDeposit> latest = transaction.execute(status -> repository.findAllForBalanceUpdate());
            assertThat(latest).containsExactly(new StoredDeposit(deposit.id(), "basic", 31, updatedBalance));
        } finally {
            competitor.shutdownNow();
        }
    }

    /** Seeds a stored deposit directly so the fixture is independent of repository queries and writes. */
    private void insertDeposit(StoredDeposit deposit) {
        jdbc.update("INSERT INTO time_deposits (id, plan_type, days, balance) VALUES (?, ?, ?, ?)",
                deposit.id(), deposit.planType(), deposit.days(), deposit.balance());
    }

    /** Seeds a withdrawal under the specified parent so the read can verify the association. */
    private void insertWithdrawal(int depositId, Withdrawal withdrawal) {
        jdbc.update("INSERT INTO withdrawals (id, time_deposit_id, amount, date) VALUES (?, ?, ?, ?)",
                withdrawal.id(), depositId, withdrawal.amount(), withdrawal.date());
    }
}
