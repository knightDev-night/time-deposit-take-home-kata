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

@Service
public class TimeDepositService {
    private final TimeDepositRepository repository;

    public TimeDepositService(TimeDepositRepository repository) {
        this.repository = repository;
    }

    public List<DepositWithWithdrawals> findAll() {
        return repository.findAll();
    }

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

    private static BigDecimal calculateInterest(StoredDeposit deposit) {
        // Convert only the input required by the legacy calculation. The stored principal stays decimal.
        double legacyBalance = deposit.balance().doubleValue();
        if (!Double.isFinite(legacyBalance)) {
            throw new ArithmeticException("Balance is outside the legacy interest range for deposit " + deposit.id());
        }
        TimeDeposit legacyDeposit = new TimeDeposit(deposit.id(), deposit.planType(), legacyBalance, deposit.days());
        return TimeDepositInterest.calculateMonthlyInterest(legacyDeposit);
    }
}
