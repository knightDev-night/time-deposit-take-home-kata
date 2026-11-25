package org.ikigaidigital;

import java.util.List;

public class TimeDepositCalculator {
    public void updateBalance(List<TimeDeposit> deposits) {
        for (TimeDeposit deposit : deposits) {
            double interest = TimeDepositInterest.calculateMonthlyInterest(deposit).doubleValue();
            deposit.setBalance(deposit.getBalance() + interest);
        }
    }
}
