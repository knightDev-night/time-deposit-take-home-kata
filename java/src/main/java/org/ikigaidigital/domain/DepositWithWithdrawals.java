package org.ikigaidigital.domain;

import java.util.List;

/**
 * Immutable read model pairing a stored deposit with its withdrawal history.
 * The repository assembles it, and the HTTP adapter maps it to the listing response.
 *
 * @param deposit deposit snapshot
 * @param withdrawals withdrawals in the supplied order, possibly empty
 */
public record DepositWithWithdrawals(StoredDeposit deposit, List<Withdrawal> withdrawals) {
    /**
     * Copies the withdrawal list so later changes to the supplied list cannot alter the snapshot.
     *
     * @param deposit deposit snapshot
     * @param withdrawals list to copy in iteration order
     * @throws NullPointerException if the list or any of its elements is null
     */
    public DepositWithWithdrawals {
        withdrawals = List.copyOf(withdrawals);
    }
}
