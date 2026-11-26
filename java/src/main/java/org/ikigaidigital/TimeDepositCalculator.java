package org.ikigaidigital;

import java.util.List;

/**
 * Preserves the legacy in-memory API by applying shared monthly interest to the caller's
 * mutable {@link TimeDeposit} objects. Persistence is handled by the application service.
 */
public class TimeDepositCalculator {
    /**
     * Credits each deposit with one month's rounded interest based on its current balance.
     * Mutates the original objects in iteration order. The list structure, IDs, plan keys,
     * and day counts are preserved. Another call uses the balances from the previous call.
     * If an entry fails, updates already made to earlier entries remain visible.
     *
     * @param deposits deposits to update, which may be supplied in an unmodifiable list
     * @throws NullPointerException if the list, an entry, or its balance is null, or a deposit
     *         older than 30 days has a null plan key
     * @throws NumberFormatException if an eligible deposit produces non-finite interest
     */
    public void updateBalance(List<TimeDeposit> deposits) {
        for (TimeDeposit deposit : deposits) {
            double interest = TimeDepositInterest.calculateMonthlyInterest(deposit).doubleValue();
            deposit.setBalance(deposit.getBalance() + interest);
        }
    }
}
