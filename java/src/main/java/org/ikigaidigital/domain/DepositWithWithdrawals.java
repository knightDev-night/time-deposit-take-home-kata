package org.ikigaidigital.domain;

import java.util.List;

public record DepositWithWithdrawals(StoredDeposit deposit, List<Withdrawal> withdrawals) {
    public DepositWithWithdrawals {
        withdrawals = List.copyOf(withdrawals);
    }
}
