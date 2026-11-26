package org.ikigaidigital.application;

import org.ikigaidigital.TimeDeposit;
import org.ikigaidigital.TimeDepositInterest;
import org.ikigaidigital.application.port.TimeDepositRepository;
import org.ikigaidigital.domain.DepositWithWithdrawals;
import org.ikigaidigital.domain.StoredDeposit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coordinates the listing and monthly interest use cases. The repository port supplies
 * stored deposits and persists changes, while the shared domain calculation supplies the
 * rounded credit used by both this service and the legacy calculator.
 */
@Service
public class TimeDepositService {
    private final TimeDepositRepository repository;

    /**
     * Creates the service with the persistence port for both use cases.
     *
     * @param repository source of deposits and writer of updated balances
     */
    public TimeDepositService(TimeDepositRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves the deposit listing with its withdrawal history.
     *
     * @return deposits and their withdrawals in ID order, or an empty list
     */
    public List<DepositWithWithdrawals> findAll() {
        return repository.findAll();
    }

    /**
     * Applies one month's interest to the locked deposits using their stored day counts and
     * current balances. Adds each rounded credit to the original {@link BigDecimal} principal,
     * preserving its fractional digits. Writes only balances with nonzero credits.
     * Withdrawal history is informational for this use case.
     *
     * <p>For an HTTP call, Spring opens a read-committed transaction spanning the locking
     * read, calculation, and batch write. Competing calls wait for those locks and then
     * calculate from the latest committed balances, so every successful call applies
     * another month's interest. A runtime failure rolls back the batch.
     *
     * @throws ArithmeticException if a stored balance converts to a non-finite double
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyMonthlyInterest() {
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        for (StoredDeposit deposit : repository.findAllForBalanceUpdate()) {
            BigDecimal interest = calculateInterest(deposit);
            if (interest.signum() != 0) {
                balances.put(deposit.id(), deposit.balance().add(interest));
            }
        }
        if (!balances.isEmpty()) {
            repository.updateBalances(balances);
        }
    }

    /**
     * Adapts a stored deposit to the legacy calculation. Converting the principal to a
     * double can lose precision or underflow to zero, so the converted value is used only
     * to calculate the credit and the original principal stays decimal.
     *
     * @param deposit stored principal, plan key, and day count
     * @return monthly interest rounded according to the legacy rules
     * @throws ArithmeticException if the conversion produces a non-finite double
     */
    private static BigDecimal calculateInterest(StoredDeposit deposit) {
        double legacyBalance = deposit.balance().doubleValue();
        if (!Double.isFinite(legacyBalance)) {
            throw new ArithmeticException("Balance is outside the legacy interest range for deposit " + deposit.id());
        }
        TimeDeposit legacyDeposit = new TimeDeposit(deposit.id(), deposit.planType(), legacyBalance, deposit.days());
        return TimeDepositInterest.calculateMonthlyInterest(legacyDeposit);
    }
}
