package org.ikigaidigital.infrastructure.persistence;

import org.ikigaidigital.application.port.TimeDepositRepository;
import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;
import org.ikigaidigital.domain.Withdrawal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL adapter for the time deposit persistence port. Spring JDBC maps SQL rows to
 * domain records and executes the batch, keeping query shape and locking explicit.
 * Balance updates join the application service's transaction.
 */
@Repository
public class JdbcTimeDepositRepository implements TimeDepositRepository {
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Creates the adapter with the application's Spring JDBC template.
     *
     * @param jdbc template used to query and batch write through the configured data source
     */
    public JdbcTimeDepositRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@inheritDoc}
     * A single left join includes deposits without withdrawals, and ordering in SQL
     * lets the mapper retain both deposit and withdrawal order.
     *
     * @throws org.springframework.dao.DataAccessException if the listing query fails
     */
    @Override
    public List<DepositWithWithdrawals> findAll() {
        return jdbc.query("""
                SELECT d.id, d.plan_type, d.days, d.balance, w.id AS withdrawal_id, w.amount, w.date
                FROM time_deposits d
                LEFT JOIN withdrawals w ON w.time_deposit_id = d.id
                ORDER BY d.id, w.id
                """, JdbcTimeDepositRepository::mapDepositsWithWithdrawals);
    }

    /**
     * {@inheritDoc}
     * PostgreSQL's {@code FOR UPDATE} locks rows in ID order. Holding those locks through
     * the write prevents concurrent interest requests from overwriting a credit calculated
     * against the same principal.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException if no transaction is active
     * @throws org.springframework.dao.DataAccessException if the locking read fails
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StoredDeposit> findAllForBalanceUpdate() {
        return jdbc.query("""
                SELECT id, plan_type, days, balance
                FROM time_deposits
                ORDER BY id
                FOR UPDATE
                """, (row, rowNumber) -> mapDeposit(row));
    }

    /**
     * {@inheritDoc}
     * Binds IDs and balances as named parameters and sends the updates as one JDBC batch.
     * The surrounding transaction makes the writes atomic, including when a later update fails.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException if no transaction is active
     * @throws org.springframework.dao.DataAccessException if the batch write fails
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateBalances(Map<Integer, BigDecimal> balances) {
        SqlParameterSource[] batch = balances.entrySet().stream()
                .map(entry -> new MapSqlParameterSource("id", entry.getKey()).addValue("balance", entry.getValue()))
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate("UPDATE time_deposits SET balance = :balance WHERE id = :id", batch);
    }

    /**
     * Groups repeated deposit columns into one entry per deposit. A null withdrawal ID
     * represents the empty side of the left join. The insertion-ordered map and withdrawal
     * lists retain the ordering established by the query.
     *
     * @param rows ordered join result positioned before the first row
     * @return grouped deposits in encounter order
     * @throws SQLException if advancing the cursor or reading a column fails
     */
    private static List<DepositWithWithdrawals> mapDepositsWithWithdrawals(ResultSet rows) throws SQLException {
        Map<StoredDeposit, List<Withdrawal>> deposits = new LinkedHashMap<>();
        while (rows.next()) {
            List<Withdrawal> withdrawals = deposits.computeIfAbsent(mapDeposit(rows), deposit -> new ArrayList<>());
            Integer withdrawalId = rows.getObject("withdrawal_id", Integer.class);
            if (withdrawalId != null) {
                withdrawals.add(new Withdrawal(withdrawalId, rows.getBigDecimal("amount"),
                        rows.getObject("date", LocalDate.class)));
            }
        }
        return deposits.entrySet().stream()
                .map(entry -> new DepositWithWithdrawals(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Maps the deposit columns common to both queries and reads the principal directly as decimal.
     *
     * @param row result set positioned on a deposit row
     * @return stored deposit snapshot
     * @throws SQLException if reading a deposit column fails
     */
    private static StoredDeposit mapDeposit(ResultSet row) throws SQLException {
        return new StoredDeposit(row.getInt("id"), row.getString("plan_type"), row.getInt("days"),
                row.getBigDecimal("balance"));
    }
}
