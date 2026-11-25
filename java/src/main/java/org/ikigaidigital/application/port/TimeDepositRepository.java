package org.ikigaidigital.application.port;

import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TimeDepositRepository {
    List<DepositWithWithdrawals> findAll();

    /** Locks deposits for a balance update in the caller's write transaction. */
    List<StoredDeposit> findAllForBalanceUpdate();

    /** Persists balances by deposit ID in the same transaction that locked those deposits. */
    void updateBalances(Map<Integer, BigDecimal> balances);
}
