package org.ikigaidigital.infrastructure.http;

import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record TimeDepositResponse(int id, String planType, BigDecimal balance, int days,
                           List<WithdrawalResponse> withdrawals) {
    static TimeDepositResponse from(DepositWithWithdrawals entry) {
        StoredDeposit deposit = entry.deposit();
        List<WithdrawalResponse> withdrawals = entry.withdrawals().stream()
                .map(withdrawal -> new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date()))
                .toList();
        return new TimeDepositResponse(deposit.id(), deposit.planType(), deposit.balance(), deposit.days(), withdrawals);
    }

    record WithdrawalResponse(int id, BigDecimal amount, LocalDate date) {
    }
}
