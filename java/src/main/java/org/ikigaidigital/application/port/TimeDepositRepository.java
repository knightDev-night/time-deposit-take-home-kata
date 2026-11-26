package org.ikigaidigital.application.port;

import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Persistence port for the listing and balance update use cases. It exposes decimal
 * deposit snapshots and keeps database details behind the adapter. The caller owns the
 * transaction that ties the locking read to the corresponding writes.
 */
public interface TimeDepositRepository {
    /**
     * Loads the deposit listing, including deposits with an empty withdrawal history.
     *
     * @return deposits ordered by deposit ID, with each withdrawal list ordered by withdrawal ID
     */
    List<DepositWithWithdrawals> findAll();

    /**
     * Reads current deposit balances while acquiring locks for the pending update. Requires
     * an active write transaction. Locks must remain held until that transaction ends.
     *
     * @return locked deposit snapshots in ID order, possibly empty
     */
    List<StoredDeposit> findAllForBalanceUpdate();

    /**
     * Writes replacement balances in the same transaction that locked the deposits.
     * The caller controls commit and rollback and supplies only IDs from that locking read.
     *
     * @param balances replacement decimal principals keyed by deposit ID
     */
    void updateBalances(Map<Integer, BigDecimal> balances);
}
