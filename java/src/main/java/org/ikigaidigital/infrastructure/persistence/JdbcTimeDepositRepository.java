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

@Repository
public class JdbcTimeDepositRepository implements TimeDepositRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcTimeDepositRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DepositWithWithdrawals> findAll() {
        return jdbc.query("""
                SELECT d.id, d.plan_type, d.days, d.balance, w.id AS withdrawal_id, w.amount, w.date
                FROM time_deposits d
                LEFT JOIN withdrawals w ON w.time_deposit_id = d.id
                ORDER BY d.id, w.id
                """, JdbcTimeDepositRepository::mapDepositsWithWithdrawals);
    }

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

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void updateBalances(Map<Integer, BigDecimal> balances) {
        SqlParameterSource[] batch = balances.entrySet().stream()
                .map(entry -> new MapSqlParameterSource("id", entry.getKey()).addValue("balance", entry.getValue()))
                .toArray(SqlParameterSource[]::new);

        jdbc.batchUpdate("UPDATE time_deposits SET balance = :balance WHERE id = :id", batch);
    }

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

    private static StoredDeposit mapDeposit(ResultSet row) throws SQLException {
        return new StoredDeposit(row.getInt("id"), row.getString("plan_type"), row.getInt("days"),
                row.getBigDecimal("balance"));
    }
}
