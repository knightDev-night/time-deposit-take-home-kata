package org.ikigaidigital.infrastructure.http;

import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HTTP response record for one deposit in the listing. It defines the JSON shape separately
 * from the persisted model and legacy calculator object. The mapper supplies an unmodifiable
 * withdrawal list and passes through decimal amounts.
 *
 * @param id deposit identifier
 * @param planType stored plan key
 * @param balance current decimal principal including any applied interest
 * @param days stored elapsed day count
 * @param withdrawals nested withdrawal responses, possibly empty
 */
record TimeDepositResponse(int id, String planType, BigDecimal balance, int days,
                           List<WithdrawalResponse> withdrawals) {
    /**
     * Projects one repository read model into the HTTP contract and preserves withdrawal order.
     *
     * @param entry stored deposit and its associated withdrawals
     * @return response ready for JSON serialization
     */
    static TimeDepositResponse from(DepositWithWithdrawals entry) {
        StoredDeposit deposit = entry.deposit();
        List<WithdrawalResponse> withdrawals = entry.withdrawals().stream()
                .map(withdrawal -> new WithdrawalResponse(withdrawal.id(), withdrawal.amount(), withdrawal.date()))
                .toList();
        return new TimeDepositResponse(deposit.id(), deposit.planType(), deposit.balance(), deposit.days(), withdrawals);
    }

    /**
     * Immutable nested response for a withdrawal whose deposit is identified by the containing response.
     *
     * @param id withdrawal identifier
     * @param amount stored decimal amount
     * @param date calendar date serialized in ISO format without time or time zone
     */
    record WithdrawalResponse(int id, BigDecimal amount, LocalDate date) {
    }
}
